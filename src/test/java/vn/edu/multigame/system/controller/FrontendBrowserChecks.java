package vn.edu.multigame.system.controller;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in browser acceptance against the packaged jar, inside the baseline-owned schema. */
final class FrontendBrowserChecks {
    static void verifyPackaged(String url,String username,String password) throws Exception {
        long course;
        try(var connection=DriverManager.getConnection(url,username,password);var sql=connection.createStatement()) {
            assertThat(connection.getCatalog()).matches("multigame_test_[0-9a-f]{32}");
            String hash=new BCryptPasswordEncoder().encode("BrowserPass123!");
            for(String[] account:new String[][]{{"ui_teacher","Giảng viên giao diện","GIANG_VIEN"},{"ui_student","Nguyễn Minh Anh","SINH_VIEN"},{"ui_admin","Quản trị giao diện","ADMIN"},{"ui_empty","Sinh viên chưa có lớp","SINH_VIEN"},{"ui_legacy","Sinh viên cần bổ sung mã","SINH_VIEN"}}) {
                try(var insert=connection.prepareStatement("INSERT INTO app_user(username,password_hash,display_name,role,ma_sinh_vien,created_at_ms) VALUES(?,?,?,?,?,1)")) {
                    insert.setString(1,account[0]);insert.setString(2,hash);insert.setString(3,account[1]);insert.setString(4,account[2]);
                    insert.setString(5,account[2].equals("SINH_VIEN")&&!account[0].equals("ui_legacy")?account[0].toUpperCase():null);insert.executeUpdate();
                }
            }
            sql.executeUpdate("INSERT INTO course_class(offering_id,code,name,required_lessons) SELECT MIN(id),'UI13','Lập trình mạng · Nhóm thực hành giao diện',6 FROM subject_offering");
            try(var row=sql.executeQuery("SELECT id FROM course_class WHERE code='UI13'")){row.next();course=row.getLong(1);}
            sql.executeUpdate("INSERT INTO teaching_assignment(course_class_id,teacher_id) SELECT "+course+",id FROM app_user WHERE username='ui_teacher'");
            sql.executeUpdate("INSERT INTO class_room(course_class_id,join_code) VALUES("+course+",'"+java.util.UUID.randomUUID().toString().replace("-","").toUpperCase()+"')");
            sql.executeUpdate("INSERT INTO class_membership(class_room_id,student_id,status,requested_at_ms) SELECT r.id,u.id,'APPROVED',1 FROM class_room r CROSS JOIN app_user u WHERE r.course_class_id="+course+" AND u.username='ui_student'");
        }
        int port;try(var reservation=new ServerSocket(0)){port=reservation.getLocalPort();}
        String address="127.0.0.1";
        for(var network:java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces()))if(network.isUp()&&!network.isLoopback())for(var candidate:java.util.Collections.list(network.getInetAddresses()))if(candidate instanceof java.net.Inet4Address && candidate.isSiteLocalAddress())address=candidate.getHostAddress();
        String base="http://"+address+":"+port;
        System.out.println("Browser server URL: "+base+" (clients run on this machine)");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar","target/multigame-0.0.1-SNAPSHOT.jar",
                "--spring.profiles.active=mysql,baseline","--server.address=0.0.0.0","--server.port="+port,
                "--spring.datasource.url="+url,"--spring.jpa.hibernate.ddl-auto=validate","--spring.flyway.clean-disabled=true")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        var env=builder.environment();env.put("QUIZ_IMAGE_DIRECTORY",Path.of(".cache","baseline-media",URI.create(url.substring(5)).getPath().substring(1)).toAbsolutePath().toString());env.put("BASELINE_DB_URL",url);env.put("SPRING_DATASOURCE_USERNAME",username);env.put("SPRING_DATASOURCE_PASSWORD",password);
        env.put("ALLOWED_ORIGINS",base+",http://localhost:5173,http://127.0.0.1:5173");
        Process server=builder.start();
        try {
            var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();boolean ready=false;
            for(int i=0;i<150 && server.isAlive();i++) {
                try {ready=http.send(HttpRequest.newBuilder(URI.create(base+"/api/system/ready")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200;}
                catch(java.io.IOException unavailable) { /* startup */ }
                if(ready)break;Thread.sleep(400);
            }
            assertThat(ready).as("Packaged application becomes ready").isTrue();
            String node=System.getProperty("frontend.node","node");
            Process browser=new ProcessBuilder(node,"frontend/e2e/shell.mjs",base,Long.toString(course)).redirectErrorStream(true).start();
            Thread output=Thread.startVirtualThread(()->{
                try(var reader=browser.inputReader(StandardCharsets.UTF_8)){reader.lines().forEach(System.out::println);}
                catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
            });
            try {assertThat(browser.waitFor(600,TimeUnit.SECONDS)).as("Browser checks finish").isTrue();assertThat(browser.exitValue()).as("Browser checks pass").isZero();}
            finally {if(browser.isAlive())browser.destroyForcibly().waitFor();output.join(5000);}
            var before=persisted(url,username,password);
            server.destroy();if(!server.waitFor(15,TimeUnit.SECONDS))server.destroyForcibly().waitFor();
            server=builder.start();ready=false;
            for(int i=0;i<150&&server.isAlive();i++){
                try{ready=http.send(HttpRequest.newBuilder(URI.create(base+"/api/system/ready")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200;}catch(java.io.IOException unavailable){}
                if(ready)break;Thread.sleep(400);
            }
            assertThat(ready).as("Application restarts with validated migrated schema").isTrue();
            assertThat(persisted(url,username,password)).as("Lesson outcomes, scores, submissions and awards survive restart").isEqualTo(before);
            System.out.println("Restart persistence, Flyway validation and JPA validate PASS");
        } finally {
            server.destroy();if(!server.waitFor(15,TimeUnit.SECONDS))server.destroyForcibly().waitFor();
        }
    }
    private static java.util.List<java.util.Map<String,Object>> persisted(String url,String username,String password)throws Exception{
        try(var connection=DriverManager.getConnection(url,username,password)){
            assertThat(connection.getCatalog()).matches("multigame_test_[0-9a-f]{32}");
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            return java.util.List.of(java.util.Map.of("games",jdbc.queryForList("SELECT * FROM lesson_game ORDER BY id"),"lessons",jdbc.queryForList("SELECT * FROM lesson ORDER BY id"),"scores",jdbc.queryForList("SELECT * FROM lesson_score ORDER BY lesson_id,student_id"),"submissions",jdbc.queryForList("SELECT * FROM lesson_action_input ORDER BY id"),"awards",jdbc.queryForList("SELECT * FROM achievement_award ORDER BY id")));
        }
    }
}
