package vn.edu.multigame.admin.controller;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.multigame.admin.service.AdminProvisionService;
import vn.edu.multigame.auth.security.AuthSessionRegistry;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000"})
@ActiveProfiles("mysql")
class AdminSecurityIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AdminProvisionService provision;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthSessionRegistry sessions;
    final List<String> names=new ArrayList<>();final List<Long> rooms=new ArrayList<>(),quizzes=new ArrayList<>();final List<Client> clients=new ArrayList<>();final List<WebSocket> sockets=new ArrayList<>();
    String name(){String n="admtest_"+UUID.randomUUID().toString().replace("-","").substring(0,15);names.add(n);return n;}
    String secret(){return UUID.randomUUID()+"!Aa";}
    class Client implements AutoCloseable {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();String token;long id;String name;
        Client(){clients.add(this);}
        HttpResponse<String> request(String method,String path,Object body,boolean csrf)throws Exception{
            var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5));if(csrf&&token!=null)r.header("X-CSRF-TOKEN",token);if(body!=null)r.header("Content-Type","application/json");
            return http.send(r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void csrf()throws Exception{token=json.readTree(request("GET","/api/auth/csrf",null,false).body()).path("token").asText();}
        String session(){return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).map(HttpCookie::getValue).findFirst().orElse(null);}
        public void close(){if(session()!=null)sessions.revoke(session(),"LOGGED_OUT");http.close();}
    }
    JsonNode ok(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body());}
    Client user(boolean admin)throws Exception{
        var c=new Client();c.csrf();c.name=name();String pass=secret();
        if(admin)c.id=provision.provision(c.name,"Admin",pass.toCharArray());else c.id=ok(c.request("POST","/api/auth/register",Map.of("username",c.name,"displayName","User","password",pass),true),201).path("id").asLong();
        var login=ok(c.request("POST","/api/auth/login",Map.of("username",c.name,"password",pass),true),200);assertThat(login.path("role").asText()).isEqualTo(admin?"ADMIN":"USER");c.csrf();return c;
    }
    JsonNode room(Client host)throws Exception{
        var q=ok(host.request("POST","/api/quizzes",Map.of("title","Admin projection secret","mode","RIDDLE","visibility","PRIVATE","questions",List.of(Map.of("content","Secret question","acceptedAnswers",List.of("secret alias")))),true),201);long qid=q.path("id").asLong();quizzes.add(qid);
        var r=ok(host.request("POST","/api/rooms",Map.of("requestId",UUID.randomUUID().toString(),"config",Map.of("name","Admin orphan fixture","maxPlayers",3,"hostParticipation","SPECTATOR","stages",List.of(Map.of("mode","RIDDLE","quizId",qid,"questionCount",1,"questionDurationMs",60000)))),true),201);rooms.add(r.path("id").asLong());return r;
    }
    @AfterEach void cleanup(){
        sockets.forEach(WebSocket::abort);clients.forEach(Client::close);
        for(long rid:rooms){jdbc.update("delete from room_stage where room_id=?",rid);jdbc.update("delete from room_member where room_id=?",rid);jdbc.update("delete from room where id=?",rid);}
        for(long qid:quizzes){jdbc.update("delete from question where quiz_id=?",qid);jdbc.update("delete from quiz where id=?",qid);}
        for(String name:names)jdbc.update("delete from app_user where username=?",name);
    }
    @Test void sharedLoginDbRoleRegistrationCannotRequestAdminAndAnonymousUserHostDenied()throws Exception{
        var anon=new Client();for(String path:List.of("/admin","/api/admin/users","/api/admin/games","/api/admin/orphan-rooms","/ws/admin"))assertThat(anon.request("GET",path,null,false).statusCode()).as(path).isEqualTo(401);
        var ordinary=user(false);var owned=room(ordinary);
        for(String path:List.of("/admin","/api/admin/users","/api/admin/games","/api/admin/orphan-rooms","/ws/admin"))assertThat(ordinary.request("GET",path,null,false).statusCode()).as(path).isEqualTo(403);
        var forged=new Client();forged.csrf();String n=name();var response=ok(forged.request("POST","/api/auth/register",Map.of("username",n,"displayName","Forged","password",secret(),"role","ADMIN","authorities",List.of("ROLE_ADMIN")),true),201);
        assertThat(response.path("role").asText()).isEqualTo("USER");assertThat(jdbc.queryForObject("select role from app_user where id=?",String.class,response.path("id").asLong())).isEqualTo("USER");
        var admin=user(true);assertThat(admin.request("GET","/admin",null,false).body()).contains("/app.js");assertThat(admin.request("GET","/admin/login",null,false).statusCode()).isEqualTo(404);
    }
    @Test void explicitDtosPaginationNoSecretsNoMutationsAndOrphanMeansDeletedNotOffline()throws Exception{
        var admin=user(true);var host=user(false);var r=room(host);long rid=r.path("id").asLong();
        ok(host.request("POST","/api/rooms/"+rid+"/open",Map.of("requestId",UUID.randomUUID().toString(),"revision",r.path("revision").asLong()),true),200);
        var before=ok(admin.request("GET","/api/admin/orphan-rooms?size=100",null,false),200);assertThat(before.path("items").toString()).doesNotContain("\"id\":"+rid+",");
        jdbc.update("update app_user set deleted_at_ms=created_at_ms+1 where id=?",host.id);
        var orphans=ok(admin.request("GET","/api/admin/orphan-rooms?size=100",null,false),200);assertThat(orphans.path("items").toString()).contains("\"id\":"+rid+",").contains("HOST_DELETED");
        for(String endpoint:List.of("users","games","orphan-rooms")){
            var out=admin.request("GET","/api/admin/"+endpoint+"?size=1&page=0",null,false);ok(out,200);assertThat(out.headers().firstValue("Cache-Control")).contains("no-store");assertThat(out.body()).doesNotContain("password","hash","sessionId","roomCode","acceptedAnswers","Secret question","secret alias");
            assertThat(admin.request("GET","/api/admin/"+endpoint+"?size=101",null,false).statusCode()).isEqualTo(400);
            assertThat(admin.request("POST","/api/admin/"+endpoint,Map.of(),true).statusCode()).isEqualTo(403);
        }
        assertThat(admin.request("GET","/api/admin/users?page=-1",null,false).statusCode()).isEqualTo(400);
        assertThat(admin.request("GET","/api/admin/users?page=nope",null,false).statusCode()).isEqualTo(400);
        // T09 now authorizes this ADMIN endpoint; an empty body must fail validation without mutation.
        assertThat(admin.request("POST","/api/admin/users/"+host.id+"/ban",Map.of(),true).statusCode()).isEqualTo(400);
        assertThat(admin.request("GET","/api/admin/audit",null,false).statusCode()).isEqualTo(404);
    }
    @Test void bootstrapIsIdempotentConcurrentAndCannotPromoteOrReactivate()throws Exception{
        String n=name(),pass=secret();long id=provision.provision(n,"Bootstrap",pass.toCharArray());String hash=jdbc.queryForObject("select password_hash from app_user where id=?",String.class,id);
        assertThat(passwords.matches(pass,hash)).isTrue();assertThat(hash).startsWith("$2a$10$");assertThat(provision.provision(n,"New name",secret().toCharArray())).isEqualTo(id);assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,id)).isEqualTo(hash);
        var user=user(false);String original=jdbc.queryForObject("select password_hash from app_user where id=?",String.class,user.id);
        assertThatThrownBy(()->provision.provision(user.name,"No promotion",secret().toCharArray())).isInstanceOf(IllegalStateException.class);assertThat(jdbc.queryForObject("select role from app_user where id=?",String.class,user.id)).isEqualTo("USER");assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,user.id)).isEqualTo(original);
        jdbc.update("update app_user set deleted_at_ms=created_at_ms+1 where id=?",id);assertThatThrownBy(()->provision.provision(n,"No resurrection",secret().toCharArray())).isInstanceOf(IllegalStateException.class);
        String concurrent=name();try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->provision.provision(concurrent,"Concurrent",secret().toCharArray()));var b=pool.submit(()->provision.provision(concurrent,"Concurrent",secret().toCharArray()));assertThat(a.get(5,TimeUnit.SECONDS)).isEqualTo(b.get(5,TimeUnit.SECONDS));}assertThat(jdbc.queryForObject("select count(*) from app_user where username=?",Long.class,concurrent)).isEqualTo(1);
    }
    @Test void roleDemotionAndSessionRevocationAreCheckedBeyondCachedAuthority()throws Exception{
        var admin=user(true);ok(admin.request("GET","/api/admin/users",null,false),200);jdbc.update("update app_user set role='USER' where id=?",admin.id);
        assertThat(admin.request("GET","/api/admin/users",null,false).statusCode()).isEqualTo(403);assertThat(admin.request("GET","/admin",null,false).statusCode()).isEqualTo(403);
        assertThat(ok(admin.request("GET","/api/auth/me",null,false),200).path("role").asText()).isEqualTo("USER");
        var other=user(true);sessions.revoke(other.session(),"LOGGED_OUT");assertThat(other.request("GET","/api/admin/users",null,false).statusCode()).isEqualTo(401);
    }
    class Wire implements WebSocket.Listener {
        final BlockingQueue<JsonNode> messages=new LinkedBlockingQueue<>();final CompletableFuture<Integer> close=new CompletableFuture<>();final StringBuilder text=new StringBuilder();
        public void onOpen(WebSocket ws){ws.request(1);}
        public CompletionStage<?> onText(WebSocket ws,CharSequence part,boolean last){text.append(part);if(last){try{messages.add(json.readTree(text.toString()));}catch(Exception e){throw new AssertionError(e);}text.setLength(0);}ws.request(1);return null;}
        public CompletionStage<?> onClose(WebSocket ws,int status,String reason){close.complete(status);return null;}
    }
    WebSocket connect(Client c,String origin,Wire wire)throws Exception{var socket=c.http.newWebSocketBuilder().header("Origin",origin).buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/admin"),wire).get(5,TimeUnit.SECONDS);sockets.add(socket);assertThat(wire.messages.poll(3,TimeUnit.SECONDS).path("type").asText()).isEqualTo("AUTH_READY");return socket;}
    @Test void adminWsSharesOriginSessionRegistryIsReadOnlyAndLogoutRevokes()throws Exception{
        var admin=user(true);var wire=new Wire();var socket=connect(admin,"http://localhost:8080",wire);socket.sendText("{\"type\":\"KICK_PLAYER\"}",true).join();assertThat(wire.close.get(3,TimeUnit.SECONDS)).isEqualTo(1008);
        var logout=user(true);var w=new Wire();connect(logout,"http://localhost:8080",w);assertThat(logout.request("POST","/api/auth/logout",null,false).statusCode()).isEqualTo(403);assertThat(logout.request("POST","/api/auth/logout",null,true).statusCode()).isEqualTo(204);assertThat(w.close.get(5,TimeUnit.SECONDS)).isEqualTo(4001);assertThat(logout.request("GET","/api/admin/users",null,false).statusCode()).isEqualTo(401);
        var forbidden=user(true);assertThatThrownBy(()->connect(forbidden,"http://outside.invalid",new Wire())).hasCauseInstanceOf(WebSocketHandshakeException.class);
        var demoted=user(true);var d=new Wire();var live=connect(demoted,"http://localhost:8080",d);jdbc.update("update app_user set role='USER' where id=?",demoted.id);live.sendText("{}",true).join();assertThat(d.close.get(5,TimeUnit.SECONDS)).isEqualTo(4001);
        assertThatThrownBy(()->connect(demoted,"http://localhost:8080",new Wire())).hasCauseInstanceOf(WebSocketHandshakeException.class);
    }
}
