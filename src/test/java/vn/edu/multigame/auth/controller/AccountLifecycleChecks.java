package vn.edu.multigame.auth.controller;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** Invoked by the baseline suite while its owned MySQL schema is alive. */
public class AccountLifecycleChecks {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String PASSWORD="StudentPass123!";
    static class Client {
        final int port;
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        Client(int port) { this.port=port; }
        HttpResponse<String> send(String method,String path,Object body,boolean csrf) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
            if(csrf) {
                var token=JSON.readTree(send("GET","/api/auth/csrf",null,false).body());
                builder.header(token.get("headerName").asText(),token.get("token").asText());
            }
            builder.header("Content-Type","application/json");
            return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode ok(String method,String path,Object body,int status) throws Exception {
            var response=send(method,path,body,!method.equals("GET"));
            assertThat(response.statusCode()).as(path+": "+response.body()).isEqualTo(status);
            assertThat(response.body()).doesNotContain("password_hash","$2a$");
            return response.body().isEmpty()?null:JSON.readTree(response.body());
        }
        void login(String name,String password,int status) throws Exception {ok("POST","/api/auth/login",Map.of("username",name,"password",password),status);}
    }
    public static void verify(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        long legacyId=jdbc.queryForObject("SELECT id FROM app_user WHERE username='legacy'",Long.class);
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?",String.class,legacyId)).isEqualTo("SINH_VIEN");
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM quiz WHERE title='Retained history'",Long.class)).isEqualTo(legacyId);
        var legacy=new Client(port);legacy.login("legacy","LegacyPass123!",200);
        assertThat(legacy.ok("GET","/api/auth/me",null,200).get("profileComplete").asBoolean()).isFalse();
        legacy.ok("GET","/api/rooms",null,403);
        legacy.ok("PUT","/api/auth/profile",Map.of("hoTen","Sinh viên cũ","maSinhVien","SVOLD"),200);
        String hash=context.getBean(PasswordEncoder.class).encode(PASSWORD);
        jdbc.update("INSERT INTO app_user(username,password_hash,display_name,created_at_ms,role) VALUES('admin_test',?,'Admin',1,'ADMIN')",hash);
        var admin=new Client(port);admin.login("admin_test",PASSWORD,200);
        var anonymous=new Client(port);
        var registration=Map.of("username","student_test","hoTen","Nguyễn Văn An","maSinhVien","SV001","password",PASSWORD,"role","ADMIN");
        anonymous.ok("POST","/api/auth/register",registration,201);
        anonymous.ok("POST","/api/auth/register",Map.of("username","missing","hoTen","Missing","password",PASSWORD),400);
        anonymous.ok("POST","/api/auth/register",Map.of("username","duplicate","hoTen","Duplicate","maSinhVien","sv001","password",PASSWORD),409);
        var student=new Client(port);student.login("student_test",PASSWORD,200);
        var me=student.ok("GET","/api/auth/me",null,200);
        long id=me.get("id").asLong();
        assertThat(me.get("role").asText()).isEqualTo("SINH_VIEN");
        assertThat(me.get("hoTen").asText()).isEqualTo("Nguyễn Văn An");
        assertThat(me.get("maSinhVien").asText()).isEqualTo("SV001");
        var teacherInput=Map.of("username","teacher_test","hoTen","Giảng viên","password",PASSWORD);
        student.ok("POST","/api/admin/teachers",teacherInput,403);
        assertThat(admin.ok("POST","/api/admin/teachers",teacherInput,201).get("role").asText()).isEqualTo("GIANG_VIEN");
        admin.ok("POST","/api/admin/teachers",teacherInput,409);
        var teacher=new Client(port);teacher.login("teacher_test",PASSWORD,200);
        assertThat(teacher.ok("GET","/api/auth/me",null,200).get("role").asText()).isEqualTo("GIANG_VIEN");
        var sanction=Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0,"reason","Test expulsion");
        teacher.ok("POST","/api/admin/users/"+id+"/expel",sanction,403);
        student.ok("POST","/api/admin/users/"+id+"/restore",sanction,403);
        // CSRF remains mandatory even for an authenticated administrator.
        assertThat(admin.send("POST","/api/admin/teachers",teacherInput,false).statusCode()).isEqualTo(403);
        var closed=new CompletableFuture<Integer>();
        WebSocket socket=student.http.newWebSocketBuilder().header("Origin","http://localhost:8080")
                .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws"),new WebSocket.Listener(){
                    public void onOpen(WebSocket ws){ws.request(1);}
                    public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){ws.request(1);return null;}
                    public CompletionStage<?> onClose(WebSocket ws,int status,String reason){closed.complete(status);return null;}
                    public void onError(WebSocket ws,Throwable error){closed.completeExceptionally(error);}
                }).get(5,TimeUnit.SECONDS);
        try {
            assertThat(admin.ok("POST","/api/admin/users/"+id+"/expel",sanction,200).get("status").asText()).isEqualTo("EXPELLED");
            assertThat(closed.get(5,TimeUnit.SECONDS)).isEqualTo(4001);
            assertThat(student.send("GET","/api/auth/me",null,false).statusCode()).isIn(401,403);
            assertThat(jdbc.queryForObject("SELECT account_status FROM app_user WHERE id=?",String.class,id)).isEqualTo("EXPELLED");
            new Client(port).login("student_test",PASSWORD,403);
            admin.ok("POST","/api/admin/users/"+id+"/expel",sanction,200); // idempotent retry
            assertThat(admin.ok("POST","/api/admin/users/"+id+"/restore",Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",1,"reason","Restore"),200).get("status").asText()).isEqualTo("ACTIVE");
            assertThat(student.send("GET","/api/auth/me",null,false).statusCode()).isIn(401,403);
            var restored=new Client(port);restored.login("student_test",PASSWORD,200);
            restored.ok("POST","/api/auth/logout",Map.of(),204);
            restored.ok("GET","/api/auth/me",null,401);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE owner_user_id=?",Integer.class,legacyId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account_action WHERE target_user_id=?",Integer.class,id)).isEqualTo(2);
        } finally {socket.abort();}
    }
}
