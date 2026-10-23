package vn.edu.multigame.room.service;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.user.enums.UserRole;
import vn.edu.multigame.room.dto.request.ClassMembershipRequests.*;
import vn.edu.multigame.room.dto.response.*;
import vn.edu.multigame.room.repository.ClassRoomStore;
import vn.edu.multigame.realtime.idempotency.CommandFingerprint;

@Service @Profile("mysql") @Transactional(readOnly=true)
public class ClassMembershipService {
    private final org.springframework.context.ApplicationEventPublisher events;
    private final ClassRoomStore rooms;
    private final AuthService auth;
    private final AccountAccessPolicy accounts;
    private final CommandFingerprint fingerprints;
    public ClassMembershipService(ClassRoomStore rooms,AuthService auth,AccountAccessPolicy accounts,CommandFingerprint fingerprints,org.springframework.context.ApplicationEventPublisher events) {
        this.events=events;this.rooms=rooms;this.auth=auth;this.accounts=accounts;this.fingerprints=fingerprints;
    }
    private static <T> T found(Optional<T> value) {return value.orElseThrow(RoomFailure::missing);}
    private long student(Authentication caller) {
        var user=auth.requireActiveUser(caller);auth.requireCompleteProfile(user);
        if(user.getRole()!=UserRole.SINH_VIEN) throw RoomFailure.forbidden();return user.getId();
    }
    private ClassRoomView teacherRoom(Authentication caller,long classId) {
        var user=auth.requireActiveUser(caller);
        if(user.getRole()!=UserRole.GIANG_VIEN || !rooms.assigned(classId,user.getId())) throw RoomFailure.forbidden();
        return found(rooms.forClass(classId));
    }
    private void allowed(long student,ClassRoomView room) {
        if(rooms.excluded(student,room)) throw new AuthFailure(HttpStatus.FORBIDDEN,"SUBJECT_YEAR_REMOVED","Bạn đã bị loại khỏi môn trong năm học này.");
    }
    /** Shared account fence serializes joins/reviews/removal across every class for this student. */
    private void lock(Authentication caller,long target) {
        long actor=((AuthPrincipal)caller.getPrincipal()).userId();
        accounts.lock(Math.min(actor,target));
        if(actor!=target) accounts.lock(Math.max(actor,target));
        auth.requireActiveUser(caller); // Recheck the epoch after waiting for either account lock.
        accounts.requireAvailable(target);
    }
    public List<ClassMembershipView> mine(Authentication caller) {return rooms.mine(student(caller));}
    public List<ClassMembershipView> roster(Authentication caller,long classId) {return rooms.roster(teacherRoom(caller,classId).id());}
    public ClassRoomView room(Authentication caller,long classId) {
        var user=auth.requireActiveUser(caller);
        if(user.getRole()==UserRole.ADMIN) return found(rooms.forClass(classId));
        if(user.getRole()==UserRole.GIANG_VIEN) return teacherRoom(caller,classId);
        return requireApproved(caller,classId);
    }
    /** Reusable admission guard for lesson/game snapshots and commands in subsequent tasks. */
    public ClassRoomView requireApproved(Authentication caller,long classId) {
        long student=student(caller);var room=found(rooms.forClass(classId));allowed(student,room);
        if(!found(rooms.member(room.id(),student)).status().equals("APPROVED")) throw RoomFailure.forbidden();
        return room;
    }
    private boolean replay(long actor,String request,String hash) {
        var prior=rooms.receipt(actor,request);
        if(prior.isPresent() && !prior.get().equals(hash)) throw RoomFailure.conflict("REQUEST_ID_REUSED");
        return prior.isPresent();
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public ClassMembershipView join(Authentication caller,Join input) {
        long student=student(caller);lock(caller,student);
        var room=found(rooms.byCode(input.code().toUpperCase(Locale.ROOT)));allowed(student,room);
        String hash=fingerprints.of("REQUEST_JOIN","CLASS",room.courseClassId(),null,input);
        if(replay(student,input.requestId(),hash)) return found(rooms.member(room.id(),student));
        long now=System.currentTimeMillis();
        // Rejected and approved requests are final; retry never resets them to PENDING.
        if(rooms.member(room.id(),student).isEmpty()) rooms.request(room.id(),student,now);
        var result=found(rooms.member(room.id(),student));rooms.record(student,input.requestId(),hash,"REQUEST_JOIN",result,now);
        events.publishEvent(new ClassMembershipChanged(student));return result;
    }
    @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
    public ClassMembershipView decide(Authentication caller,long classId,long student,String command,Decision input) {
        var room=teacherRoom(caller,classId);lock(caller,student);
        long teacher=((AuthPrincipal)caller.getPrincipal()).userId();
        var member=found(rooms.member(room.id(),student));
        String hash=fingerprints.of(command,"CLASS",classId,student,input);
        // A replay returns current authority, so an earlier APPROVED receipt cannot resurrect removal.
        if(replay(teacher,input.requestId(),hash)) return member;
        allowed(student,room);
        if(member.revision()!=input.revision()) throw RoomFailure.conflict("STALE_REVISION");
        long now=Math.max(System.currentTimeMillis(),member.requestedAtMs());
        switch(command) {
            case "APPROVE", "REJECT" -> {
                if(!member.status().equals("PENDING")) throw RoomFailure.conflict("MEMBERSHIP_ALREADY_REVIEWED");
                rooms.review(member.id(),command.equals("APPROVE")?"APPROVED":"REJECTED",teacher,now);
            }
            case "REMOVE" -> {
                if(!member.status().equals("APPROVED")) throw RoomFailure.conflict("MEMBERSHIP_NOT_APPROVED");
                rooms.exclude(student,room,teacher,now);
            }
            default -> throw RoomFailure.invalid();
        }
        var result=found(rooms.member(room.id(),student));rooms.record(teacher,input.requestId(),hash,command,result,now);events.publishEvent(new ClassMembershipChanged(student));
        return result;
    }
}
