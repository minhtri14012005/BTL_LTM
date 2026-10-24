package vn.edu.multigame.leaderboard.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import vn.edu.multigame.auth.service.*;
import vn.edu.multigame.user.enums.UserRole;
import vn.edu.multigame.room.service.ClassMembershipService;
import vn.edu.multigame.room.repository.ClassRoomStore;
import vn.edu.multigame.lesson.repository.LessonStore;
import vn.edu.multigame.lesson.service.LessonFailure;
import vn.edu.multigame.leaderboard.repository.LearningBoardStore;
import vn.edu.multigame.leaderboard.dto.LearningBoard.*;
@Service @Profile("mysql") @Transactional(readOnly=true)
public class LearningBoardService {
 private final LearningBoardStore store;private final AuthService auth;private final AccountAccessPolicy accounts;private final ClassMembershipService memberships;private final ClassRoomStore rooms;private final LessonStore lessons;private final ApplicationEventPublisher events;
 public LearningBoardService(LearningBoardStore store,AuthService auth,AccountAccessPolicy accounts,ClassMembershipService memberships,ClassRoomStore rooms,LessonStore lessons,ApplicationEventPublisher events){this.store=store;this.auth=auth;this.accounts=accounts;this.memberships=memberships;this.rooms=rooms;this.lessons=lessons;this.events=events;}
 @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
 public ClassView classroom(Authentication caller,long course){memberships.room(caller,course);return new ClassView(course,LearningRanks.classroom(store.classroom(course),store.config(course).ranges()));}
 @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
 public GlobalView global(Authentication caller,long subject,long year){auth.requireActiveUser(caller);if(!store.scope(subject,year))throw new LessonFailure(org.springframework.http.HttpStatus.NOT_FOUND,"SUBJECT_YEAR_NOT_FOUND");return new GlobalView(subject,year,LearningRanks.global(store.global(subject,year)));}
 private void teacher(Authentication caller,long course){var user=auth.requireActiveUser(caller);if(user.getRole()!=UserRole.GIANG_VIEN || !rooms.assigned(course,user.getId()))throw new LessonFailure(org.springframework.http.HttpStatus.FORBIDDEN,"CLASS_NOT_ASSIGNED");}
 public Config config(Authentication caller,long course){teacher(caller,course);return store.config(course);}
 private void lock(Authentication caller,long course,long revision){long id=auth.requireActiveUser(caller).getId();accounts.lock(id);teacher(caller,course);lessons.lockClass(course);if(store.config(course).revision()!=revision)throw LessonFailure.conflict("STALE_REVISION");}
 @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
 public Config save(Authentication caller,long course,Config input){lock(caller,course,input.revision());if(!LearningRanks.valid(input.ranges()))throw LessonFailure.conflict("INVALID_RANK_RANGES");store.save(course,input.ranges());events.publishEvent(new LearningScoreChanged(course));return store.config(course);}
 @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
 public String share(Authentication caller,long course,long revision){lock(caller,course,revision);String code=UUID.randomUUID().toString().replace("-","");store.share(code,store.config(course).ranges());return code;}
 @Transactional(timeout=5,isolation=Isolation.READ_COMMITTED)
 public Config copy(Authentication caller,long course,Import input){lock(caller,course,input.revision());if(input.code()==null || !input.code().matches("[0-9a-f]{32}"))throw LessonFailure.conflict("INVALID_SHARE_CODE");var ranges=store.shared(input.code()).orElseThrow(()->new LessonFailure(org.springframework.http.HttpStatus.NOT_FOUND,"RANK_SHARE_NOT_FOUND"));if(!LearningRanks.valid(ranges))throw LessonFailure.conflict("INVALID_RANK_RANGES");store.save(course,ranges);events.publishEvent(new LearningScoreChanged(course));return store.config(course);}
}
