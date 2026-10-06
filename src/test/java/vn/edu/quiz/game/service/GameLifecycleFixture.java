package vn.edu.quiz.game.service;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.quiz.auth.security.AuthPrincipal;
import vn.edu.quiz.game.dto.request.StartGameRequest;
import vn.edu.quiz.game.dto.response.*;
import vn.edu.quiz.game.enums.*;
import vn.edu.quiz.game.repository.PlayerSessionRepository;
import vn.edu.quiz.game.runtime.PhaseWindow;
import vn.edu.quiz.quiz.entity.*;
import vn.edu.quiz.quiz.enums.*;
import vn.edu.quiz.quiz.repository.*;
import vn.edu.quiz.quiz.dto.request.*;
import vn.edu.quiz.quiz.service.QuizService;
import vn.edu.quiz.room.dto.request.*;
import vn.edu.quiz.room.dto.response.RoomResponse;
import vn.edu.quiz.room.enums.Participation;
import vn.edu.quiz.room.service.*;
import vn.edu.quiz.realtime.session.*;
import vn.edu.quiz.realtime.timer.*;
import vn.edu.quiz.user.entity.UserAccount;
import vn.edu.quiz.user.repository.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Shared real MySQL/network fixture; no gameplay implementation or inherited test cases. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000"
}) @ActiveProfiles("mysql") @Import(GameLifecycleFixture.Config.class)
abstract class GameLifecycleFixture {
    static class Clock implements ServerClock {
        final long epoch=System.currentTimeMillis(); final AtomicLong mono=new AtomicLong();
        public Sample sample() { long t=mono.get(); return new Sample(t,epoch+t); }
    }
    static class Scheduler implements TimerScheduler {
        record Task(long due,long delay,Runnable action,AtomicBoolean cancelled) {}
        final Clock clock; final List<Task> tasks=new CopyOnWriteArrayList<>();
        Scheduler(Clock clock) { this.clock=clock; }
        public Ticket schedule(Runnable callback,long delay) {
            var task=new Task(clock.mono.get()+delay,delay,callback,new AtomicBoolean()); tasks.add(task); return () -> task.cancelled().set(true);
        }
        void advance(long target) {
            assertThat(target).isGreaterThanOrEqualTo(clock.mono.get()); clock.mono.set(target);
            tasks.stream().filter(t -> t.due()<=target && t.cancelled().compareAndSet(false,true)).forEach(t -> t.action().run());
        }
        void retry(long delay) { waitUntil(() -> tasks.stream().anyMatch(t -> !t.cancelled().get() && t.delay()==delay)); advance(clock.mono.get()+delay); }
        public void close() { tasks.forEach(t -> t.cancelled().set(true)); }
    }
    static class Observer {
        @Autowired JdbcTemplate jdbc;
        final List<GameLifecycleEvent> seen=new CopyOnWriteArrayList<>();
        final List<Boolean> committed=new CopyOnWriteArrayList<>();
        final AtomicReference<String> failPublication=new AtomicReference<>();
        @EventListener public void event(GameLifecycleEvent event) {
            var s=event.snapshot();
            if(!event.type().equals("GAME_UNAVAILABLE")) {
                long revision=jdbc.queryForObject("select revision from game_session where id=?",Long.class,s.gameSessionId());
                int count=jdbc.queryForObject("select count(*) from game_member where game_session_id=?",Integer.class,s.gameSessionId());
                committed.add(revision==s.revision() && count==s.members().size());
                if(event.type().equals("QUESTION_RESULT")) committed.add(jdbc.queryForObject(
                    "select count(*) from answer a join game_question q on q.id=a.game_question_id where q.game_session_id=? and q.order_index=? and a.scored_at_ms is not null",Integer.class,s.gameSessionId(),s.questionIndex())==s.results().size());
            }
            seen.add(event);
            if(failPublication.compareAndSet(event.type(),null)) throw new IllegalStateException("Injected delivery failure after commit");
        }
        GameSnapshot next(String type,long id,int index) {
            waitUntil(() -> seen.stream().anyMatch(e -> e.type().equals(type) && e.snapshot().gameSessionId()==id && (index<0 || e.snapshot().questionIndex()==index)));
            return seen.stream().filter(e -> e.type().equals(type) && e.snapshot().gameSessionId()==id && (index<0 || e.snapshot().questionIndex()==index)).findFirst().orElseThrow().snapshot();
        }
    }
    @TestConfiguration static class Config {
        @Bean @Primary Clock testClock() { return new Clock(); }
        @Bean @Primary Scheduler testScheduler(Clock clock) { return new Scheduler(clock); }
        @Bean Observer observer() { return new Observer(); }
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired QuizRepository quizzes;
    @Autowired QuestionRepository questions;
    @Autowired QuizService quizService;
    @Autowired RoomOperations roomOps;
    @Autowired RoomService rooms;
    @Autowired GameOperations operations;
    @MockitoSpyBean GameRuntime runtime;
    @Autowired Clock clock;
    @Autowired Scheduler scheduler;
    @Autowired Observer observer;
    @MockitoSpyBean GameTransactions transactions;
    @MockitoSpyBean PlayerSessionRepository players;
    final List<Long> userIds=new ArrayList<>(),quizIds=new ArrayList<>(),roomIds=new ArrayList<>(),gameIds=new ArrayList<>(),installed=new ArrayList<>();
    final List<Client> clients=new ArrayList<>();
    static final String PASSWORD="Task8-test-42", HASH=new BCryptPasswordEncoder().encode(PASSWORD);
    record Account(long id,String name,Authentication auth) {}
    Account account() {
        var u=new UserAccount(); String name="life_"+UUID.randomUUID().toString().replace("-","").substring(0,15);
        u.setUsername(name); u.setDisplayName(name); u.setPasswordHash(HASH); u.setCreatedAtMs(System.currentTimeMillis()); users.saveAndFlush(u); userIds.add(u.getId());
        var principal=new AuthPrincipal(u); return new Account(u.getId(),name,UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
    }
    long quiz(Account author,int count) {
        var q=new Quiz(); q.setOwnerUserId(author.id()); q.setTitle("Original snapshot quiz"); q.setVisibility(Visibility.PUBLIC); q.setCreatedAtMs(System.currentTimeMillis()); q=quizzes.saveAndFlush(q); quizIds.add(q.getId());
        for(int i=1;i<=count;i++) { var item=new Question(); item.setQuizId(q.getId()); item.setOrderIndex(i); item.setContent("Original question "+i); item.setOptionA("A"); item.setOptionB("B"); item.setOptionC("C"); item.setOptionD("D"); item.setCorrectOption(Option.D); item.setCreatedAtMs(System.currentTimeMillis()); questions.save(item); }
        questions.flush(); return q.getId();
    }
    RoomResponse room(Account host,long quiz,List<Account> roster) {
        var r=roomOps.create(host.auth(),new CreateRoomRequest(UUID.randomUUID().toString(),new RoomConfigRequest(quiz,"Lifecycle",10,1000L,Participation.SPECTATOR))).snapshot(); roomIds.add(r.id());
        long revision=r.revision(); long id=r.id();
        roomOps.execute(host.auth(),id,UUID.randomUUID().toString(),"OPEN_ROOM",Map.of("revision",revision),null,() -> {},() -> rooms.open(host.id(),id,revision),(s,replay) -> {});
        for(var p:roster) roomOps.execute(p.auth(),id,UUID.randomUUID().toString(),"JOIN_ROOM",Map.of("roomCode",r.roomCode(),"participation","PLAYER"),r.roomCode(),() -> {},() -> rooms.join(p.id(),id,r.roomCode(),Participation.PLAYER),(s,replay) -> {});
        return rooms.get(host.id(),id);
    }
    record Fixture(Account host,List<Account> roster,long quiz,RoomResponse room) {}
    Fixture fixture() { var host=account(); var roster=List.of(account(),account(),account()); long q=quiz(host,10); return new Fixture(host,roster,q,room(host,q,roster)); }
    StartGameRequest request(RoomResponse room) { return new StartGameRequest(UUID.randomUUID().toString(),room.revision(),10); }
    long start(Fixture f) { var result=operations.start(f.host().auth(),f.room().id(),request(f.room()),() -> {}); long id=result.gameSessionId(); gameIds.add(id); installed.add(id); observer.next("DECISION_STARTED",id,1); return id; }
    GameSnapshot open(long id,int index) {
        var decision=observer.next("DECISION_STARTED",id,index); scheduler.advance(decision.deadlineEpochMs()-clock.epoch); return observer.next("QUESTION_START",id,index);
    }
    void expire(GameSnapshot open) { scheduler.advance(open.deadlineEpochMs()-clock.epoch); }
    static void waitUntil(BooleanSupplier condition) {
        long bound=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean()) { if(System.nanoTime()>=bound) throw new AssertionError("Controlled state did not arrive"); Thread.yield(); }
    }
    @BeforeEach void before() { observer.seen.clear(); observer.committed.clear(); observer.failPublication.set(null); assertThat(jdbc.queryForObject("select database()",String.class)).isEqualTo("quizz_task2_test"); }
    @AfterEach void cleanup() {
        reset(players,transactions,runtime);
        for(long id:installed) { try { runtime.interrupt(id).get(10,TimeUnit.SECONDS); } catch(Exception ignored) {} }
        for(long id:gameIds) { transactions.interrupt(id,Math.max(System.currentTimeMillis(),clock.sample().epochMs())); jdbc.update("delete from answer where game_session_id=?",id); jdbc.update("delete from game_question where game_session_id=?",id); jdbc.update("delete from player_session where game_session_id=?",id); jdbc.update("delete from game_member where game_session_id=?",id); jdbc.update("delete from game_session where id=?",id); }
        for(long id:roomIds) { jdbc.update("delete from room_member where room_id=?",id); jdbc.update("delete from room where id=?",id); }
        for(long id:quizIds) { jdbc.update("delete from question where quiz_id=?",id); jdbc.update("delete from quiz where id=?",id); }
        for(long id:userIds) jdbc.update("delete from app_user where id=?",id);
        clients.forEach(Client::close);
        assertThat(observer.committed).doesNotContain(false);
    }

    class Client implements AutoCloseable {
        final Account account; final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build(); String csrf;
        Client(Account account) throws Exception { this.account=account; clients.add(this); token(); assertThat(call("POST","/api/auth/login",Map.of("username",account.name(),"password",PASSWORD)).statusCode()).isEqualTo(200); token(); }
        void token() throws Exception { csrf=json.readTree(call("GET","/api/auth/csrf",null).body()).get("token").asText(); }
        HttpResponse<String> call(String method,String path,Object body) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
            if(csrf!=null) builder.header("X-CSRF-TOKEN",csrf); if(body!=null) builder.header("Content-Type","application/json");
            return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        public void close() { http.close(); }
    }
}
