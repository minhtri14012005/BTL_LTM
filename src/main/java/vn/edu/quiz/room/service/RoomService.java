package vn.edu.quiz.room.service;

import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.quiz.room.dto.request.RoomConfigRequest;
import vn.edu.quiz.room.dto.response.*;
import vn.edu.quiz.room.entity.*;
import vn.edu.quiz.room.enums.*;
import vn.edu.quiz.room.repository.*;
import vn.edu.quiz.quiz.entity.Quiz;
import vn.edu.quiz.quiz.repository.QuizRepository;
import vn.edu.quiz.quiz.service.*;
import vn.edu.quiz.user.repository.UserRepository;
import vn.edu.quiz.game.repository.GameSessionRepository;
import vn.edu.quiz.game.enums.GameStatus;

/** Persistence use-cases; callers serialize each Room through the shared realtime boundary. */
@Service @Profile("mysql") @Transactional(readOnly = true)
public class RoomService {
    private final RoomRepository rooms;
    private final RoomMemberRepository members;
    private final QuizRepository quizzes;
    private final QuizAccessPolicy quizPolicy;
    private final UserRepository users;
    private final GameSessionRepository games;
    private final EntityManager entities;
    private final SecureRandom random = new SecureRandom();
    public RoomService(RoomRepository rooms, RoomMemberRepository members, QuizRepository quizzes,
            QuizAccessPolicy quizPolicy, UserRepository users, GameSessionRepository games, EntityManager entities) {
        this.rooms=rooms; this.members=members; this.quizzes=quizzes; this.quizPolicy=quizPolicy;
        this.users=users; this.games=games; this.entities=entities;
    }
    public RoomResponse get(long userId, long id) {
        Room room=room(id, false); joined(userId, room); return snapshot(room);
    }
    public RoomListResponse list(long userId, int page, int size) {
        if(page<0 || size<1 || size>100) throw RoomFailure.invalid();
        var found=rooms.findJoined(userId, PageRequest.of(page,size));
        return new RoomListResponse(found.getContent().stream().map(this::snapshot).toList(),page,size,found.getTotalElements());
    }
    public RoomPreviewResponse preview(String code) {
        if(code==null || !code.matches("[A-Z2-9]{12}")) throw RoomFailure.missing();
        Room room=rooms.findByRoomCode(code).filter(r->r.getStatus()==RoomStatus.WAITING).orElseThrow(RoomFailure::missing);
        return new RoomPreviewResponse(room.getId(),code,room.getName(),room.getStatus(),room.getMaxPlayers());
    }
    /** Replay authorization uses retained LEFT membership for Leave only, never grants snapshot access. */
    public void authorizeReceipt(long userId, long id, String type, String code) {
        Room room=room(id,false);
        switch(type) {
            case "EDIT_ROOM", "OPEN_ROOM", "CLOSE_ROOM", "REMOVE_MEMBER", "START_GAME" -> host(userId,room);
            case "JOIN_ROOM" -> { if(!room.getRoomCode().equals(code)) throw RoomFailure.missing(); }
            case "LEAVE_ROOM" -> { if(members.findByRoomIdAndUserId(id,userId).isEmpty()) throw RoomFailure.forbidden(); }
            case "SUBSCRIBE_ROOM" -> joined(userId,room);
            default -> throw RoomFailure.invalid();
        }
    }
    @Transactional(timeout=5)
    public RoomMutation create(long userId, RoomConfigRequest config) {
        Quiz quiz=usableQuiz(userId,config.quizId());
        quizPolicy.requireParticipation(userId,quiz.getOwnerUserId(),config.hostParticipation());
        Room room=new Room(); room.setHostUserId(userId); room.setRoomCode(code()); room.setStatus(RoomStatus.DRAFT);
        apply(room,config); room.setCreatedAtMs(System.currentTimeMillis()); room=rooms.saveAndFlush(room);
        RoomMember host=new RoomMember(); host.setRoomId(room.getId()); host.setUserId(userId);
        host.setParticipation(config.hostParticipation()); host.setStatus(MembershipStatus.JOINED); host.setJoinedAtMs(room.getCreatedAtMs());
        members.saveAndFlush(host); return new RoomMutation(snapshot(room),false);
    }
    @Transactional(timeout=5)
    public RoomMutation edit(long userId, long id, long revision, RoomConfigRequest config) {
        Room room=room(id,true); host(userId,room); mutable(room); revision(room,revision);
        Quiz quiz=usableQuiz(userId,config.quizId());
        var roster=members.findByRoomIdAndStatus(id,MembershipStatus.JOINED);
        long players=0;
        for(RoomMember member:roster) {
            Participation participation=member.getUserId()==userId ? config.hostParticipation():member.getParticipation();
            quizPolicy.requireParticipation(member.getUserId(),quiz.getOwnerUserId(),participation);
            if(participation==Participation.PLAYER) players++;
        }
        if(players>config.maxPlayers()) throw RoomFailure.conflict("ROOM_FULL");
        joined(userId,room).setParticipation(config.hostParticipation()); apply(room,config);
        return changed(room,revision);
    }
    @Transactional(timeout=5)
    public RoomMutation open(long userId, long id, long revision) {
        Room room=room(id,true); host(userId,room); mutable(room); revision(room,revision);
        if(room.getStatus()!=RoomStatus.DRAFT) throw RoomFailure.conflict("INVALID_STATE");
        usableQuiz(userId,room.getQuizId()); room.setStatus(RoomStatus.WAITING); return changed(room,revision);
    }
    @Transactional(timeout=5)
    public RoomMutation close(long userId, long id, long revision) {
        Room room=room(id,true); host(userId,room); mutable(room); revision(room,revision);
        // Retain the final membership roster and Host authority; CLOSED is terminal.
        room.setStatus(RoomStatus.CLOSED); return changed(room,revision);
    }
    @Transactional(timeout=5)
    public RoomMutation join(long userId, long id, String code, Participation participation) {
        Room room=room(id,true);
        if(!room.getRoomCode().equals(code)) throw RoomFailure.missing();
        waiting(room); Quiz quiz=usableQuiz(room.getHostUserId(),room.getQuizId());
        quizPolicy.requireParticipation(userId,quiz.getOwnerUserId(),participation);
        RoomMember member=members.findByRoomIdAndUserId(id,userId).orElse(null);
        var roster=members.findByRoomIdAndStatus(id,MembershipStatus.JOINED);
        boolean current=member!=null && member.getStatus()==MembershipStatus.JOINED;
        if(!current && roster.size()>=100) throw RoomFailure.conflict("MEMBER_LIMIT_REACHED");
        long players=roster.stream().filter(m->m.getUserId()!=userId && m.getParticipation()==Participation.PLAYER).count();
        if(participation==Participation.PLAYER && players>=room.getMaxPlayers()) throw RoomFailure.conflict("ROOM_FULL");
        if(current && member.getParticipation()==participation) return new RoomMutation(snapshot(room),false);
        long now=System.currentTimeMillis();
        if(member==null) {
            member=new RoomMember(); member.setRoomId(id); member.setUserId(userId); member.setStatus(MembershipStatus.JOINED); member.setJoinedAtMs(now);
        } else if(!current) member.rejoin(Math.max(now,member.getLeftAtMs()));
        member.setParticipation(participation); members.saveAndFlush(member); return changed(room,room.getRevision());
    }
    @Transactional(timeout=5)
    public RoomMutation leave(long userId, long id) {
        Room room=room(id,true); waiting(room);
        if(room.getHostUserId()==userId) throw RoomFailure.conflict("HOST_CANNOT_LEAVE");
        RoomMember member=joined(userId,room); member.leave(Math.max(System.currentTimeMillis(),member.getJoinedAtMs()));
        return changed(room,room.getRevision());
    }
    @Transactional(timeout=5)
    public RoomMutation remove(long userId, long id, long targetUserId) {
        Room room=room(id,true); host(userId,room); waiting(room);
        if(room.getHostUserId()==targetUserId) throw RoomFailure.conflict("HOST_CANNOT_LEAVE");
        RoomMember member=joined(targetUserId,room); member.leave(Math.max(System.currentTimeMillis(),member.getJoinedAtMs()));
        return changed(room,room.getRevision());
    }
    /** For adapter subscription under the same boundary, without leaking a snapshot to LEFT members. */
    public boolean isJoined(long userId, long id) {
        return members.findByRoomIdAndUserId(id,userId).filter(m->m.getStatus()==MembershipStatus.JOINED).isPresent();
    }
    private Room room(long id, boolean lock) {
        return (lock?rooms.findLockedById(id):rooms.findById(id)).orElseThrow(RoomFailure::missing);
    }
    private RoomMember joined(long userId, Room room) {
        return members.findByRoomIdAndUserId(room.getId(),userId).filter(m->m.getStatus()==MembershipStatus.JOINED)
                .filter(m->room.getStatus()!=RoomStatus.DRAFT || room.getHostUserId()==userId).orElseThrow(RoomFailure::forbidden);
    }
    private void host(long userId, Room room) { if(room.getHostUserId()!=userId) throw RoomFailure.forbidden(); }
    private void mutable(Room room) {
        if((room.getStatus()!=RoomStatus.DRAFT && room.getStatus()!=RoomStatus.WAITING)
                || games.findByRoomIdAndStatus(room.getId(),GameStatus.ACTIVE).isPresent()) throw RoomFailure.conflict("INVALID_STATE");
    }
    private void waiting(Room room) { mutable(room); if(room.getStatus()!=RoomStatus.WAITING) throw RoomFailure.conflict("INVALID_STATE"); }
    private void revision(Room room, long revision) { if(room.getRevision()!=revision) throw RoomFailure.conflict("REVISION_CONFLICT"); }
    private Quiz usableQuiz(long organizerId, long id) {
        Quiz quiz=quizzes.findLockedById(id).filter(q->q.getDeletedAtMs()==null).orElseThrow(QuizFailure::notFound);
        quizPolicy.requireUse(organizerId,quiz.getOwnerUserId(),quiz.getVisibility()); return quiz;
    }
    private void apply(Room room, RoomConfigRequest config) {
        room.setQuizId(config.quizId()); room.setName(config.name().strip()); room.setMaxPlayers(config.maxPlayers());
        room.setQuestionDurationMs(config.questionDurationMs()); room.setDecisionDurationMs(7000L);
    }
    private RoomMutation changed(Room room, long previousRevision) {
        members.flush(); rooms.flush();
        if(room.getRevision()==previousRevision) {
            if(rooms.touchRevision(room.getId(),previousRevision)!=1) throw RoomFailure.conflict("REVISION_CONFLICT");
            entities.refresh(room);
        }
        return new RoomMutation(snapshot(room),true);
    }
    private RoomResponse snapshot(Room room) {
        Quiz quiz=quizzes.findById(room.getQuizId()).orElseThrow(QuizFailure::notFound);
        var roster=members.findByRoomIdAndStatus(room.getId(),MembershipStatus.JOINED).stream().sorted(Comparator.comparing(RoomMember::getUserId))
                .map(m->new RoomResponse.Member(m.getId(),m.getUserId(),users.findById(m.getUserId()).orElseThrow(RoomFailure::missing).getDisplayName(),
                        m.getUserId().equals(room.getHostUserId()),m.getParticipation(),m.getJoinedAtMs())).toList();
        return new RoomResponse(room.getId(),room.getHostUserId(),room.getQuizId(),quiz.getTitle(),quiz.getDeletedAtMs()!=null,
                room.getRoomCode(),room.getName(),room.getStatus(),room.getMaxPlayers(),room.getQuestionDurationMs(),
                room.getDecisionDurationMs(),room.getCreatedAtMs(),room.getRevision(),roster);
    }
    private String code() {
        String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; StringBuilder code=new StringBuilder();
        for(int i=0;i<12;i++) code.append(alphabet.charAt(random.nextInt(alphabet.length()))); return code.toString();
    }
}
