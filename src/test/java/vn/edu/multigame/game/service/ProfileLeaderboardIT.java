package vn.edu.multigame.game.service;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import vn.edu.multigame.achievements.model.*;
import vn.edu.multigame.achievements.repository.AwardRepository;
import vn.edu.multigame.achievements.service.*;
import vn.edu.multigame.admin.dto.SanctionRequest;
import vn.edu.multigame.admin.service.AdminSanctionTransactions;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.auth.service.AccountAccessPolicy;
import vn.edu.multigame.game.dto.TypedAnswer;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.runtime.PhaseWindow;
import vn.edu.multigame.questionbank.dto.request.*;
import vn.edu.multigame.questionbank.enums.Visibility;
import vn.edu.multigame.realtime.session.RoomBoundary;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.enums.Participation;
import vn.edu.multigame.user.enums.UserRole;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** T12 real committed Game/award facts, REST guards and snapshot consistency. */
class ProfileLeaderboardIT extends GameLifecycleFixture {
    @Autowired vn.edu.multigame.achievements.service.ProfileReadService profiles;
    @Autowired vn.edu.multigame.leaderboard.service.LeaderboardReadService leaderboard;
    @Autowired AwardTransactions awardWrites;
    @Autowired vn.edu.multigame.admin.service.AdminKickTransactions kicks;
    @Autowired PlatformTransactionManager manager;
    @Autowired RoomBoundary boundary;
    record Match(Fixture fixture,long game,int count) {}
    Match match(int count){
        var host=account();var roster=List.of(account(),account(),account());
        long quiz=quizService.create(host.auth(),new CreateQuestionBankRequest("Award fixture",Visibility.PRIVATE,Collections.nCopies(count,new QuestionRequest("Question",null,null,null,List.of("yes"))),GameMode.RIDDLE)).id();quizIds.add(quiz);
        var config=new RoomConfigRequest(null,"Award room",8,null,Participation.SPECTATOR,List.of(new RoomStageRequest(GameMode.RIDDLE,quiz,count,1000L)));
        var room=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),config)).snapshot();roomIds.add(room.id());long revision=room.revision();
        roomOps.execute(host.auth(),room.id(),UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",revision),null,()->{},()->rooms.open(host.id(),room.id(),revision),(a,b)->{});
        for(var p:roster)roomOps.execute(p.auth(),room.id(),UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",room.roomCode(),"participation","PLAYER"),room.roomCode(),()->{},()->rooms.join(p.id(),room.id(),room.roomCode(),Participation.PLAYER),(a,b)->{});
        var ready=rooms.get(host.id(),room.id());long id=boundary.read(RoomBoundary.Scope.room(room.id()),()->transactions.startMultimodeSnapshot(host.id(),room.id(),ready.revision(),System.currentTimeMillis())).gameSessionId();gameIds.add(id);
        return new Match(new Fixture(host,roster,quiz,ready),id,count);
    }
    void question(Match m,int index,Set<Long> correct){
        long at=jdbc.queryForObject("select started_at_ms from game_session where id=?",Long.class,m.game())+index*2000L;
        if(index==1)transactions.open(m.game(),index,Phase.INTRO,d->PhaseWindow.open(index,Phase.INTRO,index*3L,0,at,d));
        transactions.open(m.game(),index,Phase.QUESTION_OPEN,d->PhaseWindow.open(index,Phase.QUESTION_OPEN,index*3L+1,0,at,d));
        var allowed=jdbc.queryForList("select p.user_id from game_question_participation qp join player_session p on p.id=qp.player_session_id join game_question q on q.id=qp.game_question_id where qp.game_session_id=? and q.order_index=?",Long.class,m.game(),index);
        for(var p:m.fixture().roster())if(allowed.contains(p.id()))transactions.acceptTyped(m.game(),p.id(),index,null,new TypedAnswer(correct.contains(p.id())?"yes":"wrong",null),at+100,100);
        transactions.transition(m.game(),index,Phase.QUESTION_OPEN,Phase.QUESTION_CLOSED);transactions.transition(m.game(),index,Phase.QUESTION_CLOSED,Phase.SCORING);transactions.score(m.game(),index,at+1000);
    }
    Match completed(){var m=match(10);for(int i=1;i<=10;i++)question(m,i,new HashSet<>(m.fixture().roster().stream().map(Account::id).toList()));return m;}

    void finalizeMatch(Match m){awardReconciler.dispatch(m.game());}
    long a(Match m){return m.fixture().roster().getFirst().id();}
    @Test void zeroDenominatorsAndExplicitRestAllowlists()throws Exception{
        var u=account();var c=new Client(u);
        var data=json.readTree(c.call("GET","/api/profiles/me",null).body());
        assertThat(data.get("stats").get("officialGames").asLong()).isZero();assertThat(data.get("stats").get("winRate").isNull()).isTrue();
        assertThat(data.get("stats").get("accuracy").isNull()).isTrue();assertThat(data.get("badges").isEmpty()).isTrue();
        var response=c.call("GET","/api/profiles/"+u.id(),null);assertThat(response.statusCode()).isEqualTo(200);
        var publicData=json.readTree(response.body());assertThat(publicData.fieldNames()).toIterable().containsExactly("userId","displayName","stats");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        for(String path:List.of("/api/profiles/me/awards?page=-1","/api/profiles/me/awards?size=51","/api/leaderboard?page=1000001","/api/leaderboard?size=0","/api/leaderboard?page=oops"))assertThat(c.call("GET",path,null).statusCode()).as(path).isEqualTo(400);
        assertThat(c.call("POST","/api/leaderboard",Map.of("cups",100)).statusCode()).isEqualTo(403);
        assertThat(c.call("GET","/api/profiles/999999999999",null).statusCode()).isEqualTo(404);
        assertThat(c.call("GET","/api/profiles/"+u.id()+"/awards",null).statusCode()).isEqualTo(403);
        c.call("POST","/api/auth/logout",null);
        for(String path:List.of("/api/profiles/me","/api/profiles/"+u.id(),"/api/leaderboard"))assertThat(c.call("GET",path,null).statusCode()).isEqualTo(401);
    }
    @Test void pendingRollbackAndReplayNeverExposePartialOrDuplicateStats(){
        var m=completed();long u=a(m);assertThat(profiles.own(u).stats().officialGames()).isZero();
        var tx=new TransactionTemplate(manager);tx.executeWithoutResult(s->{awardWrites.finalizeGame(m.game());assertThat(profiles.own(u).stats().cups()).isEqualTo(1);s.setRollbackOnly();});
        assertThat(profiles.own(u).stats().cups()).isZero();assertThat(profiles.ownAwards(u,0,20).items()).isEmpty();
        finalizeMatch(m);finalizeMatch(m);
        var stats=profiles.own(u).stats();assertThat(stats.officialGames()).isEqualTo(1);assertThat(stats.officialWins()).isEqualTo(1);assertThat(stats.cups()).isEqualTo(1);
        assertThat(stats.correct()).isEqualTo(10);assertThat(stats.participated()).isEqualTo(10);assertThat(stats.winRate()).isEqualTo(1);
        assertThat(profiles.own(u).badges()).hasSize(3);
    }
    @Test void stableTiePaginationAndOwnRankBeyondPage(){
        var m=completed();finalizeMatch(m);var ids=m.fixture().roster().stream().map(Account::id).toList();
        var entries=ids.stream().map(id->leaderboard.read(id,0,1).own()).toList();
        assertThat(entries).allSatisfy(e->assertThat(e.rank()).isEqualTo(entries.getFirst().rank()));
        var seen=new ArrayList<Long>();var initial=leaderboard.read(a(m),0,1);
        for(int p=0;p<initial.totalElements();p++){var page=leaderboard.read(a(m),p,1);seen.addAll(page.items().stream().map(e->e.userId()).toList());}
        assertThat(seen).doesNotHaveDuplicates();assertThat(seen).containsSubsequence(ids.toArray(Long[]::new));
        assertThat(leaderboard.read(a(m),1000000,1).own().rank()).isEqualTo(entries.getFirst().rank());
        assertThat(leaderboard.read(a(m),1000000,1).items()).isEmpty();
    }
    @Test void cupsThenWinRateThenAggregateAccuracyOrderWithoutIdBreakingTies(){
        var m=completed();finalizeMatch(m);
        var room=rooms.get(m.fixture().host().id(),m.fixture().room().id());
        long next=boundary.read(RoomBoundary.Scope.room(room.id()),()->transactions.startMultimodeSnapshot(m.fixture().host().id(),room.id(),room.revision(),System.currentTimeMillis())).gameSessionId();gameIds.add(next);
        var second=new Match(m.fixture(),next,10);var ids=m.fixture().roster().stream().map(Account::id).toList();
        for(int i=1;i<=10;i++){var correct=new HashSet<Long>();correct.add(ids.get(0));if(i<=2)correct.add(ids.get(1));if(i<=5)correct.add(ids.get(2));question(second,i,correct);}finalizeMatch(second);
        var other=completed();finalizeMatch(other);
        var highest=leaderboard.read(ids.get(0),0,1).own();var middle=leaderboard.read(a(other),0,1).own();
        var accurate=leaderboard.read(ids.get(2),0,1).own();var lower=leaderboard.read(ids.get(1),0,1).own();
        assertThat(highest.stats().cups()).isEqualTo(2);assertThat(middle.stats().cups()).isEqualTo(1);
        assertThat(highest.rank()).isLessThan(middle.rank());assertThat(middle.rank()).isLessThan(accurate.rank());assertThat(accurate.rank()).isLessThan(lower.rank());
        assertThat(lower.stats().correct()).isEqualTo(12);assertThat(lower.stats().participated()).isEqualTo(20);assertThat(lower.stats().accuracy()).isEqualTo(.6);
    }
    @Test void unqualifiedAndV1GamesAreNotOfficialStats()throws Exception{
        var shortGame=match(1);question(shortGame,1,Set.of(a(shortGame)));finalizeMatch(shortGame);
        assertThat(profiles.own(a(shortGame)).stats().officialGames()).isZero();
        var interrupted=match(10);transactions.interrupt(interrupted.game(),System.currentTimeMillis());finalizeMatch(interrupted);
        assertThat(profiles.own(a(interrupted)).stats().officialGames()).isZero();
        var legacy=fixture();long id=start(legacy);runtime.interrupt(id).get(5,TimeUnit.SECONDS);
        assertThat(profiles.own(legacy.roster().getFirst().id()).stats().officialGames()).isZero();
    }
    @Test void publicVisibilityUsesCurrentBanAndDeleteWithoutChangingFrozenFacts(){
        var m=completed();finalizeMatch(m);long u=a(m);var before=profiles.own(u);
        jdbc.update("update app_user set ban_kind='INDEFINITE' where id=?",u);
        assertThatThrownBy(()->profiles.publicProfile(u)).isInstanceOf(GameFailure.class).hasMessage("PROFILE_NOT_FOUND");assertThat(leaderboard.read(u,0,50).own()).isNull();
        assertThat(profiles.own(u)).isEqualTo(before); // Service alone is internal; REST account guard enforces ban.
        jdbc.update("update app_user set ban_kind='TEMP',banned_until_ms=? where id=?",System.currentTimeMillis()-1,u);
        assertThat(profiles.publicProfile(u).stats()).isEqualTo(before.stats());assertThat(leaderboard.read(u,0,50).own()).isNotNull();
        jdbc.update("update app_user set ban_kind='NONE',banned_until_ms=null,deleted_at_ms=? where id=?",System.currentTimeMillis(),u);
        assertThatThrownBy(()->profiles.publicProfile(u)).isInstanceOf(GameFailure.class);assertThat(leaderboard.read(u,0,50).own()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from achievement_award where game_session_id=? and user_id=?",Long.class,m.game(),u)).isEqualTo(3);
    }
    @Test void committedSnapshotSurvivesConcurrentAwardCommit()throws Exception{
        var m=completed();long u=a(m);var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(s->{assertThat(profiles.own(u).stats().cups()).isZero();
            try(var pool=Executors.newSingleThreadExecutor()){pool.submit(()->finalizeMatch(m)).get(8,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}
            assertThat(profiles.own(u).stats().cups()).isZero();assertThat(profiles.ownAwards(u,0,20).totalElements()).isZero();assertThat(leaderboard.read(u,0,20).own()).isNull();
        });
        assertThat(profiles.own(u).stats().cups()).isEqualTo(1);assertThat(profiles.ownAwards(u,0,20).totalElements()).isEqualTo(3);assertThat(leaderboard.read(u,0,20).own()).isNotNull();
    }
    @Test void roomCloseLogoutAndNewGameDoNotEraseProfile()throws Exception{
        var m=completed();finalizeMatch(m);long u=a(m);var before=profiles.own(u);var c=new Client(m.fixture().roster().getFirst());
        var waiting=rooms.get(m.fixture().host().id(),m.fixture().room().id());rooms.close(m.fixture().host().id(),waiting.id(),waiting.revision());
        c.call("POST","/api/auth/logout",null);var again=new Client(m.fixture().roster().getFirst());assertThat(again.call("GET","/api/profiles/me",null).statusCode()).isEqualTo(200);
        var other=match(1);transactions.cancel(other.game(),other.fixture().host().id(),System.currentTimeMillis());finalizeMatch(other);
        assertThat(profiles.own(u)).isEqualTo(before);
        assertThat(profiles.ownAwards(u,0,1).totalElements()).isEqualTo(3);assertThat(profiles.ownAwards(u,0,1).items()).hasSize(1);
        assertThat(profiles.ownAwards(u,1,1).items().getFirst().awardId()).isNotEqualTo(profiles.ownAwards(u,0,1).items().getFirst().awardId());
    }
    @Test void restrictedTopPlayerNeverContributesStatsAndPublicDtoHasNoSecrets()throws Exception{
        var m=match(10);long u=a(m);var admin=account();jdbc.update("update app_user set role='ADMIN' where id=?",admin.id());
        var principal=new AuthPrincipal(users.findById(admin.id()).orElseThrow());
        var auth=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        var request=new vn.edu.multigame.admin.dto.KickRequest(UUID.randomUUID().toString(),u,"private moderation");
        boundary.read(RoomBoundary.Scope.room(m.fixture().room().id()),()->kicks.apply(auth,m.game(),request,request.requestId()));
        for(int i=1;i<=10;i++)question(m,i,new HashSet<>(m.fixture().roster().stream().map(Account::id).toList()));
        finalizeMatch(m);assertThat(profiles.own(u).stats().officialGames()).isZero();assertThat(profiles.own(u).badges()).isEmpty();
        var c=new Client(m.fixture().roster().get(1));String body=c.call("GET","/api/leaderboard",null).body();
        assertThat(body).doesNotContain("password","username","csrf","session","private moderation","acceptedAnswers","mode_metrics","ban_version");
    }
}
