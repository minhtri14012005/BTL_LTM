package vn.edu.multigame.game.service;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import vn.edu.multigame.MultigameApplication;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.realtime.session.*;
import vn.edu.multigame.room.service.*;
import vn.edu.multigame.room.dto.request.*;
import vn.edu.multigame.room.dto.response.RoomResponse;
import vn.edu.multigame.room.enums.Participation;
import vn.edu.multigame.game.repository.*;
import vn.edu.multigame.game.enums.GameMode;
import vn.edu.multigame.user.repository.UserRepository;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL/HTTP/raw WS, retained private schema. v2 Start is internal and never installs a runtime.
 * Existing lifecycle suites verify v1; this suite verifies v2 configuration/atomic snapshots only. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultimodeRoomStartIT {
    ConfigurableApplicationContext ctx;
    JdbcTemplate jdbc;
    ObjectMapper json=new ObjectMapper();
    int port;String schema;
    RoomOperations operations;RoomService rooms;GameTransactions transactions;
    final List<RoomChanged> events=new CopyOnWriteArrayList<>();
    final List<Boolean> afterCommit=new CopyOnWriteArrayList<>();
    final List<Client> clients=new ArrayList<>();
    @BeforeAll void startServerOnPrivateSchema() throws Exception {
        var local=new Properties();try(var r=Files.newBufferedReader(Path.of("config/application-local.properties"))){local.load(r);}
        String host=setting(local,"DB_HOST","127.0.0.1"),dbPort=setting(local,"DB_PORT","3306"),
            user=setting(local,"DB_USER","quiz_app"),password=setting(local,"DB_PASSWORD","");
        schema="quizz_task17_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String url="jdbc:mysql://"+host+":"+dbPort+"/?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        new JdbcTemplate(new DriverManagerDataSource(url,user,password)).execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        url=url.replace("/?","/"+schema+"?");
        jdbc=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        ctx=new SpringApplicationBuilder(MultigameApplication.class).run("--spring.profiles.active=mysql","--spring.datasource.url="+url,
            "--spring.datasource.username="+user,"--spring.datasource.password="+password,"--server.address=127.0.0.1","--server.port=0","--debug=false");
        port=((ServletWebServerApplicationContext)ctx).getWebServer().getPort();
        operations=ctx.getBean(RoomOperations.class);rooms=ctx.getBean(RoomService.class);transactions=ctx.getBean(GameTransactions.class);
        ctx.addApplicationListener(event -> {
            if(event instanceof PayloadApplicationEvent<?> payload && payload.getPayload() instanceof RoomChanged change) {
                events.add(change);
                afterCommit.add(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                    && jdbc.queryForObject("select revision from room where id=?",Long.class,change.snapshot().id())==change.snapshot().revision());
            }
        });
        assertThat(jdbc.queryForObject("select max(cast(version as unsigned)) from flyway_schema_history where success=1",Integer.class)).isEqualTo(7);
    }
    String setting(Properties p,String key,String fallback){return Optional.ofNullable(System.getenv(key)).orElse(p.getProperty(key,fallback));}
    @AfterAll void closeServer() {
        clients.forEach(Client::close);if(ctx!=null)ctx.close();
        assertThat(afterCommit).doesNotContain(false);
        System.out.println("TASK17_MYSQL: configuration/snapshot/races/gate; retained schema="+schema);
    }
    String id(){return UUID.randomUUID().toString();}
    long insert(String sql,Object...args){
        var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},keys);return keys.getKey().longValue();
    }
    class Client implements AutoCloseable {
        long uid;Authentication auth;String csrf;final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
        Client() throws Exception {
            clients.add(this);String name="stage_"+id().replace("-","").substring(0,16);String password="Task17-test-42";
            token();ok(call("POST","/api/auth/register",Map.of("username",name,"password",password,"displayName",name)),201);
            uid=ok(call("POST","/api/auth/login",Map.of("username",name,"password",password)),200).path("id").asLong();token();
            var principal=new AuthPrincipal(ctx.getBean(UserRepository.class).findById(uid).orElseThrow());
            auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        }
        void token() throws Exception{csrf=ok(call("GET","/api/auth/csrf",null),200).path("token").asText();}
        HttpResponse<String> call(String method,String route,Object body) throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+route)).timeout(Duration.ofSeconds(15));
            if(csrf!=null)b.header("X-CSRF-TOKEN",csrf);if(body!=null)b.header("Content-Type","application/json");
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        public void close(){http.close();}
    }
    JsonNode ok(HttpResponse<String> r,int code)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(code);return r.body().isEmpty()?json.nullNode():json.readTree(r.body());}
    void error(HttpResponse<String> r,int status,String code)throws Exception{assertThat(ok(r,status).path("code").asText()).isEqualTo(code);}
    Map<String,Object> stage(GameMode mode,long set,int count,long duration){return Map.of("mode",mode,"quizId",set,"questionCount",count,"questionDurationMs",duration);}
    Map<String,Object> config(List<?> stages){return Map.of("name","Many stages","maxPlayers",10,"hostParticipation","SPECTATOR","stages",stages);}
    long set(Client author,GameMode mode,int count,String visibility,String content)throws Exception{
        Object question=mode==GameMode.QUIZ?Map.of("content",content,"options",Map.of("A","A","B","B","C","C","D","D"),"correctAnswer","D"):
            Map.of("content",content,"acceptedAnswers",List.of(" CÁI   BÀN ","bàn"));
        return ok(author.call("POST","/api/quizzes",Map.of("title","Original "+mode,"mode",mode,"visibility",visibility,"questions",Collections.nCopies(count,question))),201).path("id").asLong();
    }
    JsonNode create(Client host,List<?> stages)throws Exception{return ok(host.call("POST","/api/rooms",Map.of("requestId",id(),"config",config(stages))),201);}
    JsonNode open(Client host,JsonNode room)throws Exception{return ok(host.call("POST","/api/rooms/"+room.path("id").asLong()+"/open",Map.of("requestId",id(),"revision",room.path("revision").asLong())),200);}
    RoomResponse waiting(Client host,List<Map<String,Object>> plan,List<Client> roster)throws Exception{
        var r=open(host,create(host,plan));long room=r.path("id").asLong();String code=r.path("roomCode").asText();
        for(var p:roster) operations.execute(p.auth,room,id(),"JOIN_ROOM",Map.of("roomCode",code,"participation","PLAYER"),code,()->{},
            ()->rooms.join(p.uid,room,code,Participation.PLAYER),(receipt,replay)->{});
        return rooms.get(host.uid,room);
    }
    RoomBoundary.Receipt start(Client host,RoomResponse room,String request){
        return operations.execute(host.auth,room.id(),request,"START_GAME",Map.of("revision",room.revision(),"questionCount",room.stages().stream().mapToInt(RoomResponse.Stage::questionCount).sum()),null,()->{},
            ()->transactions.startMultimodeSnapshot(host.uid,room.id(),room.revision(),System.currentTimeMillis()),(receipt,replay)->{});
    }
    class Socket implements WebSocket.Listener,AutoCloseable {
        final BlockingQueue<JsonNode> received=new LinkedBlockingQueue<>();final StringBuilder text=new StringBuilder();final WebSocket socket;
        Socket(Client user)throws Exception{socket=user.http.newWebSocketBuilder().header("Origin","http://localhost:8080")
            .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws"),this).get(5,TimeUnit.SECONDS);next(n->n.path("type").asText().equals("AUTH_READY"));}
        public void onOpen(WebSocket socket){socket.request(1);}
        public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){
            text.append(data);if(last){try{received.add(json.readTree(text.toString()));}catch(Exception e){throw new AssertionError(e);}text.setLength(0);}socket.request(1);return null;
        }
        JsonNode next(java.util.function.Predicate<JsonNode> predicate)throws Exception{
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<end){var n=received.poll(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);if(n!=null && predicate.test(n))return n;}
            throw new AssertionError("WS frame did not arrive");
        }
        JsonNode send(String type,long room,Object payload)throws Exception{
            String request=id();var frame=new LinkedHashMap<String,Object>();frame.put("v",1);frame.put("kind","COMMAND");frame.put("requestId",request);frame.put("type",type);
            frame.put("target",Map.of("kind","ROOM","id",room));frame.put("questionIndex",null);frame.put("payload",payload);
            socket.sendText(json.writeValueAsString(frame),true).get(5,TimeUnit.SECONDS);return next(n->n.path("requestId").asText().equals(request));
        }
        public void close(){socket.abort();}
    }
    @Test void restWsPlanPrivacyRevisionAndStartRosterGuard()throws Exception{
        var host=new Client();var author=new Client();var player=new Client();var outsider=new Client();
        long riddle=set(host,GameMode.RIDDLE,3,"PRIVATE","Private riddle");long quiz=set(author,GameMode.QUIZ,10,"PUBLIC","Quiz");
        var plan=List.of(stage(GameMode.RIDDLE,riddle,2,20000),stage(GameMode.QUIZ,quiz,10,30000));
        var room=open(host,create(host,plan));long rid=room.path("id").asLong();
        assertThat(room.path("configVersion").asInt()).isEqualTo(2);assertThat(room.path("quizId").isNull()).isTrue();assertThat(room.path("stages").size()).isEqualTo(2);
        assertThat(host.call("GET","/api/quizzes?scope=MINE&mode=RIDDLE",null).body()).contains("\"id\":"+riddle);
        assertThat(host.call("GET","/api/quizzes?scope=SHARED&mode=RIDDLE",null).body()).doesNotContain("\"id\":"+riddle);
        error(outsider.call("POST","/api/rooms",Map.of("requestId",id(),"config",config(plan))),404,"QUIZ_NOT_FOUND");
        error(outsider.call("GET","/api/rooms/"+rid,null),403,"FORBIDDEN");
        try(var h=new Socket(host);var p=new Socket(player);var a=new Socket(author)){
            assertThat(h.send("SUBSCRIBE_ROOM",rid,Map.of()).path("kind").asText()).isEqualTo("ACK");
            assertThat(p.send("JOIN_ROOM",rid,Map.of("roomCode",room.path("roomCode").asText(),"participation","PLAYER")).path("kind").asText()).isEqualTo("ACK");
            var update=h.next(n->n.path("type").asText().equals("ROOM_UPDATED"));
            assertThat(update.path("payload").path("stages").size()).isEqualTo(2);
            assertThat(update.toString()).doesNotContain("acceptedAnswers","correctAnswer","options","payload\":{\"accepted");
            assertThat(a.send("JOIN_ROOM",rid,Map.of("roomCode",room.path("roomCode").asText(),"participation","PLAYER")).path("code").asText()).isEqualTo("QUIZ_AUTHOR_CANNOT_PLAY");
            assertThat(a.send("JOIN_ROOM",rid,Map.of("roomCode",room.path("roomCode").asText(),"participation","SPECTATOR")).path("kind").asText()).isEqualTo("ACK");
            error(player.call("GET","/api/quizzes/"+riddle,null),404,"QUIZ_NOT_FOUND");
            long revision=rooms.get(host.uid,rid).revision();
            var edit=Map.of("requestId",id(),"revision",revision,"config",config(List.of(plan.get(1),plan.get(0))));
            var edited=ok(host.call("PUT","/api/rooms/"+rid,edit),200);
            assertThat(edited.path("revision").asLong()).isEqualTo(revision+1);
            assertThat(edited.path("stages").get(0).path("mode").asText()).isEqualTo("QUIZ");
            assertThat(ok(host.call("PUT","/api/rooms/"+rid,edit),200)).isEqualTo(edited);
            assertThat(jdbc.queryForObject("select count(*) from room_stage where room_id=?",Integer.class,rid)).isEqualTo(4);
            assertThat(h.next(n->n.path("type").asText().equals("ROOM_UPDATED") && n.path("revision").asLong()==revision+1).path("payload").path("stages")).isEqualTo(edited.path("stages"));
            error(host.call("POST","/api/rooms/"+rid+"/start",Map.of("requestId",id(),"revision",revision+1,"questionCount",12)),409,"NOT_ENOUGH_PLAYERS");
            assertThat(h.send("START_GAME",rid,Map.of("revision",revision+1,"questionCount",12)).path("code").asText()).isEqualTo("NOT_ENOUGH_PLAYERS");
            assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,rid)).isZero();
        }
    }
    @Test void invalidPlansAndAuthorsAreRejectedWithoutPartialConfiguration()throws Exception{
        var host=new Client();var author=new Client();long q=set(author,GameMode.QUIZ,10,"PUBLIC","Quiz");long r=set(host,GameMode.RIDDLE,1,"PUBLIC","Riddle");
        for(var bad:List.of(List.of(),List.of(stage(GameMode.QUIZ,q,1,1000),stage(GameMode.QUIZ,q,1,1000)),
            List.of(stage(GameMode.QUIZ,q,50,1000),stage(GameMode.RIDDLE,r,1,1000)),List.of(stage(GameMode.QUIZ,q,0,1000)),List.of(stage(GameMode.QUIZ,q,1,0)))) {
            error(host.call("POST","/api/rooms",Map.of("requestId",id(),"config",config(bad))),400,"INVALID_REQUEST");
        }
        error(host.call("POST","/api/rooms",Map.of("requestId",id(),"config",config(List.of(stage(GameMode.SONG,r,1,1000))))),409,"MODE_MISMATCH");
        var mixed=new LinkedHashMap<>(config(List.of(stage(GameMode.RIDDLE,r,1,1000))));mixed.put("quizId",q);mixed.put("questionDurationMs",1000);
        error(host.call("POST","/api/rooms",Map.of("requestId",id(),"config",mixed)),400,"INVALID_REQUEST");
        var authorPlayer=new LinkedHashMap<>(config(List.of(stage(GameMode.RIDDLE,r,1,1000))));authorPlayer.put("hostParticipation","PLAYER");
        error(host.call("POST","/api/rooms",Map.of("requestId",id(),"config",authorPlayer)),403,"QUIZ_AUTHOR_CANNOT_PLAY");
        assertThat(jdbc.queryForObject("select count(*) from room where host_user_id=?",Integer.class,host.uid)).isZero();
    }
    @Test void snapshotsPersistAllStagesAndSurviveSetEditDelete()throws Exception{
        var host=new Client();var author=new Client();var roster=List.of(new Client(),new Client(),new Client());
        long quiz=set(author,GameMode.QUIZ,10,"PUBLIC","Frozen quiz");long riddle=set(host,GameMode.RIDDLE,2,"PRIVATE","Frozen riddle");
        var room=waiting(host,List.of(stage(GameMode.RIDDLE,riddle,2,23000),stage(GameMode.QUIZ,quiz,10,17000)),roster);
        String request=id();long game=start(host,room,request).gameSessionId();assertThat(start(host,room,request).gameSessionId()).isEqualTo(game);
        error(host.call("POST","/api/rooms/"+room.id()+"/start",Map.of("requestId",request,"revision",room.revision(),"questionCount",12)),409,"RULES_NOT_IMPLEMENTED");
        assertThatThrownBy(()->operations.execute(host.auth,room.id(),request,"START_GAME",Map.of("revision",room.revision(),"questionCount",13),null,()->{},()->{throw new AssertionError("Replay mismatch must reject before business");},(r,replay)->{})).hasMessage("INVALID_REQUEST_ID");
        var games=ctx.getBean(GameSessionRepository.class);var stages=ctx.getBean(GameStageRepository.class);var questions=ctx.getBean(GameQuestionRepository.class);
        assertThat(games.findById(game).orElseThrow().getV2ConfigSnapshot().quizQuestionCount()).isEqualTo(10);
        var ss=stages.findByGameSessionIdOrderByOrderIndex(game);assertThat(ss).hasSize(2);
        assertThat(ss.get(0).getFirstQuestionIndex()).isEqualTo(1);assertThat(ss.get(1).getFirstQuestionIndex()).isEqualTo(3);
        assertThat(ss.get(0).getConfigSnapshot()).containsEntry("lastQuestionCorrectPoints",20);
        assertThat(((Map<?,?>)ss.get(1).getConfigSnapshot().get("normal")).get("wrong")).isEqualTo(-4);
        var qq=questions.findByGameSessionIdOrderByOrderIndex(game);assertThat(qq).hasSize(12);
        assertThat(qq.get(0).getPayload().get("acceptedAnswers").toString()).contains("cái bàn","bàn");
        assertThat(qq.get(0).getQuestionDurationMs()).isEqualTo(23000L);assertThat(qq.get(2).getQuestionDurationMs()).isEqualTo(17000L);
        assertThat(qq.get(0).getOptionA()).isNull();assertThat(qq.get(2).getCorrectOption().name()).isEqualTo("D");
        assertThat(ctx.getBean(PlayerSessionRepository.class).findByGameSessionId(game)).hasSize(3).allSatisfy(p->{
            assertThat(p.getScore()).isZero();assertThat(p.getRemainingSpins()).isEqualTo(1);assertThat(p.getStarAvailable()).isTrue();assertThat(p.getTotalCorrectAnswerTimeMs()).isZero();
        });
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where game_session_id=?",Integer.class,game)).isEqualTo(4);
        String frozen=jdbc.queryForObject("select cast(payload as char) from game_question where id=?",String.class,qq.get(0).getId());
        ok(host.call("PUT","/api/quizzes/"+riddle,Map.of("revision",0,"mode","RIDDLE","title","Changed","visibility","PRIVATE","questions",List.of(Map.of("content","Changed","acceptedAnswers",List.of("ghế"))))),200);
        ok(host.call("DELETE","/api/quizzes/"+riddle+"?revision=1",null),204);
        ok(author.call("DELETE","/api/quizzes/"+quiz+"?revision=0",null),204);
        assertThat(jdbc.queryForObject("select cast(payload as char) from game_question where id=?",String.class,qq.get(0).getId())).isEqualTo(frozen);
        assertThat(questions.findById(qq.get(0).getId()).orElseThrow().getContent()).isEqualTo("Frozen riddle");
        assertThat(stages.findByGameSessionIdOrderByOrderIndex(game).getFirst().getTitleSnapshot()).isEqualTo("Original RIDDLE");
        // Fixture terminal data, not a claimed v2 lifecycle: retained snapshots are what future History reads.
        jdbc.update("delete from user_active_game where game_session_id=?",game); // Release only this test's occupancy, not history.
        jdbc.update("update game_session set status='FINISHED',phase='FINISHED',end_reason='CANCELLED',finished_at_ms=started_at_ms+1 where id=?",game);
        assertThat(stages.findByGameSessionIdOrderByOrderIndex(game)).hasSize(2);
        assertThatThrownBy(()->jdbc.update("update game_question set content='mutated' where id=?",qq.get(0).getId())).hasRootCauseInstanceOf(java.sql.SQLException.class);
    }
    @Test void simultaneousSameRoomStartCommitsOneAndSameRequestReplays()throws Exception{
        var host=new Client();long r=set(host,GameMode.RIDDLE,2,"PRIVATE","Riddle");var room=waiting(host,List.of(stage(GameMode.RIDDLE,r,2,1000)),List.of(new Client(),new Client(),new Client()));
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);String request=id();
        try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{ready.countDown();go.await();return start(host,room,request);});
            var two=pool.submit(()->{ready.countDown();go.await();return start(host,room,request);});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            var a=one.get(10,TimeUnit.SECONDS);var b=two.get(10,TimeUnit.SECONDS);assertThat(a).isEqualTo(b);
            assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,room.id())).isEqualTo(1);
            assertThat(ctx.getBean(PlayerSessionRepository.class).findByGameSessionId(a.gameSessionId())).allSatisfy(p->{assertThat(p.getRemainingSpins()).isZero();assertThat(p.getStarAvailable()).isFalse();});
        }
        assertThatThrownBy(()->start(host,room,id())).hasMessage("INVALID_STATE");
    }
    @Test void concurrentDifferentRequestIdsCannotCreateTwoGamesInOneRoom()throws Exception {
        var host=new Client();long r=set(host,GameMode.RIDDLE,1,"PRIVATE","Riddle");
        var room=waiting(host,List.of(stage(GameMode.RIDDLE,r,1,1000)),List.of(new Client(),new Client(),new Client()));
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        java.util.concurrent.Callable<Object> attempt=()->{
            ready.countDown();go.await();try{return start(host,room,id());}catch(GameFailure failure){return failure;}
        };
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(attempt);var b=pool.submit(attempt);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            var results=List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertThat(results.stream().filter(RoomBoundary.Receipt.class::isInstance).count()).isEqualTo(1);
            assertThat((Throwable)results.stream().filter(GameFailure.class::isInstance).findFirst().orElseThrow()).hasMessage("INVALID_STATE");
            assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,room.id())).isEqualTo(1);
        }
    }

    @Test void simultaneousCrossRoomStartClaimsSharedSpectatorOnlyOnce()throws Exception{
        var host=new Client();long r=set(host,GameMode.RIDDLE,1,"PRIVATE","Riddle");
        var first=waiting(host,List.of(stage(GameMode.RIDDLE,r,1,1000)),List.of(new Client(),new Client(),new Client()));
        var second=waiting(host,List.of(stage(GameMode.RIDDLE,r,1,1000)),List.of(new Client(),new Client(),new Client()));
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{ready.countDown();go.await();return catchThrowable(()->start(host,first,id()));});
            var b=pool.submit(()->{ready.countDown();go.await();return catchThrowable(()->start(host,second,id()));});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();var outcomes=Arrays.asList(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(Objects::isNull).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(Objects::nonNull).findFirst().orElseThrow()).hasMessage("USER_ACTIVE_GAME");
            assertThat(jdbc.queryForObject("select count(*) from game_session where room_id in (?,?)",Integer.class,first.id(),second.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from user_active_game where user_id=?",Integer.class,host.uid)).isEqualTo(1);
        }
    }
    @Test void startBoundaryOrdersBeforeLeaveAndConfigAndLeaveFirstChangesRoster()throws Exception{
        var host=new Client();long r=set(host,GameMode.RIDDLE,1,"PRIVATE","Riddle");var roster=List.of(new Client(),new Client(),new Client());
        var room=waiting(host,List.of(stage(GameMode.RIDDLE,r,1,1000)),roster);var entered=new CountDownLatch(1);var allow=new CountDownLatch(1);var submitted=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(3)){
            var start=pool.submit(()->operations.execute(host.auth,room.id(),id(),"START_GAME",Map.of("revision",room.revision(),"questionCount",1),null,()->{},()->{
                entered.countDown();await(allow);return transactions.startMultimodeSnapshot(host.uid,room.id(),room.revision(),System.currentTimeMillis());},(receipt,replay)->{}));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            var leave=pool.submit(()->{submitted.countDown();return catchThrowable(()->operations.execute(roster.getFirst().auth,room.id(),id(),"LEAVE_ROOM",Map.of(),null,()->{},()->rooms.leave(roster.getFirst().uid,room.id()),(receipt,replay)->{}));});
            var edit=pool.submit(()->{submitted.countDown();return catchThrowable(()->operations.execute(host.auth,room.id(),id(),"EDIT_ROOM",Map.of("revision",room.revision()),null,()->{},()->rooms.edit(host.uid,room.id(),room.revision(),new RoomConfigRequest(null,"Edit",10,null,Participation.SPECTATOR,List.of(new RoomStageRequest(GameMode.RIDDLE,r,1,2000L)))),(receipt,replay)->{}));});
            assertThat(submitted.await(5,TimeUnit.SECONDS)).isTrue();allow.countDown();long game=start.get(10,TimeUnit.SECONDS).gameSessionId();
            assertThat(leave.get(10,TimeUnit.SECONDS)).hasMessage("INVALID_STATE");assertThat(edit.get(10,TimeUnit.SECONDS)).hasMessage("INVALID_STATE");
            assertThat(jdbc.queryForObject("select count(*) from game_member where game_session_id=?",Integer.class,game)).isEqualTo(4);
        }finally{allow.countDown();}
        var host2=new Client();long r2=set(host2,GameMode.RIDDLE,1,"PRIVATE","Riddle");var roster2=List.of(new Client(),new Client(),new Client());var other=waiting(host2,List.of(stage(GameMode.RIDDLE,r2,1,1000)),roster2);
        operations.execute(roster2.getFirst().auth,other.id(),id(),"LEAVE_ROOM",Map.of(),null,()->{},()->rooms.leave(roster2.getFirst().uid,other.id()),(receipt,replay)->{});
        assertThatThrownBy(()->start(host2,rooms.get(host2.uid,other.id()),id())).hasMessage("NOT_ENOUGH_PLAYERS");
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,other.id())).isZero();
    }
    @Test void allSevenModesHaveFrozenGlobalOrderPayloadAndMediaReferences()throws Exception {
        var host=new Client();var plan=new ArrayList<Map<String,Object>>();var sources=new ArrayList<Long>();
        int index=0;
        for(var mode:GameMode.values()) {
            long set;
            if(mode==GameMode.QUIZ || mode==GameMode.RIDDLE) set=set(host,mode,1,"PRIVATE","Snapshot "+mode);
            else {
                set=insert("insert into quiz(owner_user_id,title,visibility,created_at_ms,mode) values(?,'Typed fixture','PRIVATE',?,?)",host.uid,System.currentTimeMillis(),mode.name());
                Map<String,Object> payload=new LinkedHashMap<>();
                if(mode==GameMode.VIETNAMESE_PUZZLE || mode==GameMode.ORDERING) {
                    payload.put(mode==GameMode.ORDERING?"items":"pieces",List.of(Map.of("id","one","text","A"),Map.of("id","two","text","B")));
                    payload.put("correctOrder",List.of("two","one"));
                }else{
                    payload.put("acceptedAnswers",List.of("bàn"));payload.put("matchingPolicy","NFC_CASE_INSENSITIVE_WHITESPACE");
                    if(mode==GameMode.SONG || mode==GameMode.IMAGE_WORD)payload.put("mediaRef","sha256:"+"a".repeat(64));
                    if(mode==GameMode.CLUES)payload.put("hints",List.of(Map.of("offsetMs",0,"text","First"),Map.of("offsetMs",1000,"text","Second")));
                }
                insert("insert into question(quiz_id,order_index,content,created_at_ms,schema_version,mode,payload) values(?,1,?,?,2,?,?)",set,"Snapshot "+mode,System.currentTimeMillis(),mode.name(),json.writeValueAsString(payload));
            }
            sources.add(set);plan.add(stage(mode,set,1,1000+(++index)*100));
        }
        var room=waiting(host,plan,List.of(new Client(),new Client(),new Client()));long game=start(host,room,id()).gameSessionId();
        var stageRows=ctx.getBean(GameStageRepository.class).findByGameSessionIdOrderByOrderIndex(game);
        assertThat(stageRows).hasSize(7);
        var data=ctx.getBean(GameQuestionRepository.class).findByGameSessionIdOrderByOrderIndex(game);
        assertThat(data).hasSize(7);assertThat(data.stream().map(q->q.getOrderIndex()).toList()).containsExactly(1,2,3,4,5,6,7);
        for(int i=0;i<7;i++) {
            assertThat(stageRows.get(i).getMode()).isEqualTo(GameMode.values()[i]);
            assertThat(data.get(i).getMode()).isEqualTo(stageRows.get(i).getMode());assertThat(data.get(i).getStageQuestionIndex()).isEqualTo(1);
            assertThat(data.get(i).getQuestionDurationMs()).isEqualTo(1100L+i*100);
        }
        assertThat(ctx.getBean(PlayerSessionRepository.class).findByGameSessionId(game)).allSatisfy(p->{
            assertThat(p.getRemainingSpins()).isZero();assertThat(p.getStarAvailable()).isTrue();
        });
        var song=data.stream().filter(q->q.getMode()==GameMode.SONG).findFirst().orElseThrow();
        assertThat(song.getPayload()).containsEntry("mediaRef","sha256:"+"a".repeat(64));
        var original=ctx.getBean(vn.edu.multigame.questionbank.repository.QuestionRepository.class).findById(song.getSourceQuestionId()).orElseThrow();
        var changed=new LinkedHashMap<>(original.getPayload());changed.put("mediaRef","sha256:"+"b".repeat(64));
        jdbc.update("update question set payload=? where id=?",json.writeValueAsString(changed),original.getId());
        assertThat(ctx.getBean(GameQuestionRepository.class).findById(song.getId()).orElseThrow().getPayload()).containsEntry("mediaRef","sha256:"+"a".repeat(64));
        assertThat(data.stream().filter(q->q.getMode()==GameMode.CLUES).findFirst().orElseThrow().getPayload()).containsKey("hints");
        // Data-only fixtures for five modes: no CRUD/playback/engine for them is claimed.
    }
    @Test void rechecksSourceCapacityAccessAuthorsAndRoomStageConstraints()throws Exception {
        var host=new Client();long r=set(host,GameMode.RIDDLE,2,"PRIVATE","Riddle");
        var room=waiting(host,List.of(stage(GameMode.RIDDLE,r,2,1000)),List.of(new Client(),new Client(),new Client()));
        ok(host.call("PUT","/api/quizzes/"+r,Map.of("revision",0,"title","Smaller","visibility","PRIVATE","mode","RIDDLE","questions",List.of(Map.of("content","One","acceptedAnswers",List.of("bàn"))))),200);
        assertThatThrownBy(()->start(host,room,id())).hasMessage("NOT_ENOUGH_QUESTIONS");
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,room.id())).isZero();
        var author=new Client();var host2=new Client();long q=set(author,GameMode.QUIZ,10,"PUBLIC","Quiz");
        var second=waiting(host2,List.of(stage(GameMode.QUIZ,q,1,1000)),List.of(new Client(),new Client(),new Client()));
        jdbc.update("update quiz set visibility='PRIVATE' where id=?",q);
        assertThatThrownBy(()->start(host2,second,id())).isInstanceOf(vn.edu.multigame.questionbank.service.QuestionBankFailure.class);
        jdbc.update("update quiz set visibility='PUBLIC' where id=?",q);
        operations.execute(author.auth,second.id(),id(),"JOIN_ROOM",Map.of("roomCode",second.roomCode(),"participation","SPECTATOR"),second.roomCode(),()->{},
            ()->rooms.join(author.uid,second.id(),second.roomCode(),Participation.SPECTATOR),(receipt,replay)->{});
        jdbc.update("update room_member set participation='PLAYER' where room_id=? and user_id=?",second.id(),author.uid);
        assertThatThrownBy(()->start(host2,rooms.get(host2.uid,second.id()),id())).isInstanceOf(vn.edu.multigame.questionbank.service.QuestionBankFailure.class);
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,second.id())).isZero();
        var draft=create(host,List.of(stage(GameMode.RIDDLE,r,1,1000)));long rid=draft.path("id").asLong();
        sqlRejected(1452,()->insert("insert into room_stage(room_id,plan_revision,order_index,mode,quiz_id,question_count,question_duration_ms) values(?,1,2,'SONG',?,1,1000)",rid,r));
        sqlRejected(1062,()->insert("insert into room_stage(room_id,plan_revision,order_index,mode,quiz_id,question_count,question_duration_ms) values(?,1,2,'RIDDLE',?,1,1000)",rid,r));
        sqlRejected(3819,()->insert("insert into room_stage(room_id,plan_revision,order_index,mode,quiz_id,question_count,question_duration_ms) values(?,1,2,'QUIZ',?,0,1000)",rid,q));
        sqlRejected(1644,()->jdbc.update("update room_stage set question_count=2 where room_id=?",rid));
    }
    void sqlRejected(int expected,Runnable action){
        var failure=catchThrowable(action::run);assertThat(failure).isNotNull();
        while(failure.getCause()!=null)failure=failure.getCause();
        assertThat(failure).isInstanceOf(java.sql.SQLException.class);assertThat(((java.sql.SQLException)failure).getErrorCode()).isEqualTo(expected);
    }

    void await(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Synchronization timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    @Test void rollbackAfterSnapshotWritesCannotPublishInstallOrRetainSuccess()throws Exception{
        var host=new Client();long r=set(host,GameMode.RIDDLE,1,"PRIVATE","FAIL_SNAPSHOT");
        var room=waiting(host,List.of(stage(GameMode.RIDDLE,r,1,1000)),List.of(new Client(),new Client(),new Client()));
        // Fault only in this private schema; stages/members/UAG SQL have executed before question flush fails.
        jdbc.execute("CREATE TRIGGER task17_snapshot_fault BEFORE INSERT ON game_question FOR EACH ROW BEGIN IF NEW.content='FAIL_SNAPSHOT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Task17 injected rollback'; END IF; END");
        int before=events.size();assertThatThrownBy(()->start(host,room,id())).hasMessage("ROOM_UNAVAILABLE");
        assertThat(events).hasSize(before);
        assertThat(jdbc.queryForObject("select count(*) from game_session where room_id=?",Integer.class,room.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_active_game where user_id=?",Integer.class,host.uid)).isZero();
        assertThat(rooms.get(host.uid,room.id()).status().name()).isEqualTo("WAITING");
        assertThatThrownBy(()->start(host,room,id())).hasMessage("ROOM_UNAVAILABLE");
    }
}
