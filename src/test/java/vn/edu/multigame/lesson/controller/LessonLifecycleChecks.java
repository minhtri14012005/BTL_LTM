package vn.edu.multigame.lesson.controller;

import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.lesson.service.*;
import vn.edu.multigame.user.repository.UserRepository;
import static org.assertj.core.api.Assertions.*;

/** Acceptance checks use only BaselineMySqlIT's owned schema. */
public class LessonLifecycleChecks {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static class Client {
        final int port;
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        Client(int port,String name) throws Exception {
            this.port=port;
            if(name!=null) call("POST","/api/auth/login",Map.of("username",name,"password","StudentPass123!"),200);
        }
        HttpResponse<String> send(String method,String path,Object body,boolean csrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
            if(csrf) {var token=JSON.readTree(send("GET","/api/auth/csrf",null,false).body());request.header(token.get("headerName").asText(),token.get("token").asText());}
            return http.send(request.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode call(String method,String path,Object body,int status) throws Exception {
            var response=send(method,path,body,!method.equals("GET"));
            assertThat(response.statusCode()).as(method+" "+path+": "+response.body()).isEqualTo(status);
            return JSON.readTree(response.body());
        }
    }
    private static Map<String,Object> slot(String mode,String budget) {return Map.of("mode",mode,"title","Private "+mode,"budget",new BigDecimal(budget));}
    private static Map<String,Object> plan() {return Map.of("requestId",UUID.randomUUID().toString(),"title","Hidden lesson title","slots",List.of(slot("LUCKY_WHEEL","20"),slot("QUIZ","40"),slot("FILL_BLANK","40")));}
    private static Map<String,Object> revision(JsonNode lesson) {return Map.of("revision",lesson.get("revision").asLong());}
    public static void verify(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var teacherAgain=new Client(port,"teacher_test");
        var outsider=new Client(port,"teacher_second");
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","LESSON","name","Lesson class","requiredLessons",1),201).get("id").asLong();
        long tid=teacher.call("GET","/api/auth/me",null,200).get("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+tid,null,200);
        String code=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).get("joinCode").asText();
        var students=new ArrayList<Client>();var ids=new ArrayList<Long>();
        for(String name:List.of("lesson_a","lesson_b","lesson_c","lesson_d")) {
            new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password","StudentPass123!"),201);
            var client=new Client(port,name);students.add(client);
            long id=client.call("GET","/api/auth/me",null,200).get("id").asLong();ids.add(id);
            client.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+id+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        }
        String collection="/api/teaching/classes/"+course+"/lessons";
        outsider.call("POST",collection,plan(),403);students.getFirst().call("POST",collection,plan(),403);
        assertThat(teacher.send("POST",collection,plan(),false).statusCode()).isEqualTo(403);
        for(var invalid:List.of(List.of(slot("QUIZ","20"),slot("LUCKY_WHEEL","40"),slot("FILL_BLANK","40")),
                List.of(slot("LUCKY_WHEEL","20"),slot("QUIZ","40"),slot("QUIZ","40")),
                List.of(slot("LUCKY_WHEEL","20.01"),slot("QUIZ","40"),slot("ASSIGNMENT","40"))))
            teacher.call("POST",collection,Map.of("requestId",UUID.randomUUID().toString(),"title","Invalid","slots",invalid),409);
        teacher.call("POST",collection,Map.of("requestId",UUID.randomUUID().toString(),"title","Invalid","slots",List.of(slot("LUCKY_WHEEL","0.001"),slot("QUIZ","40"),slot("ASSIGNMENT","40"))),400);
        teacher.call("POST",collection,Map.of("requestId",UUID.randomUUID().toString(),"title","Invalid","slots",List.of(slot("LUCKY_WHEEL","20"),slot("QUIZ","40"))),400);
        var p1=plan();var p2=plan();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->teacher.send("POST",collection,p1,true));
            var b=executor.submit(()->teacherAgain.send("POST",collection,p2,true));
            var ra=a.get(10,TimeUnit.SECONDS);var rb=b.get(10,TimeUnit.SECONDS);
            assertThat(List.of(ra.statusCode(),rb.statusCode())).containsExactlyInAnyOrder(201,409);
            var winner=ra.statusCode()==201?p1:p2;
            teacher.call("POST",collection,winner,201); // Create retry at the quota limit.
        }
        var lesson=teacher.call("GET",collection,null,200).get(0);long id=lesson.get("id").asLong();
        long quizGame=jdbc.queryForObject("SELECT id FROM lesson_game WHERE lesson_id=? AND position=2",Long.class,id);
        context.getBean(vn.edu.multigame.lesson.repository.GameContentStore.class).select(quizGame,jdbc.queryForObject("SELECT id FROM quiz ORDER BY id LIMIT 1",Long.class),0,vn.edu.multigame.game.enums.GameMode.QUIZ,"Test quiz",List.of(new vn.edu.multigame.questionbank.dto.response.QuestionResponse(0,1,"Question",Map.of(vn.edu.multigame.quiz.enums.Option.A,"A",vn.edu.multigame.quiz.enums.Option.B,"B",vn.edu.multigame.quiz.enums.Option.C,"C",vn.edu.multigame.quiz.enums.Option.D,"D"),vn.edu.multigame.quiz.enums.Option.A,null,null,null,null,null,null,null)));
        long fillGame=jdbc.queryForObject("SELECT id FROM lesson_game WHERE lesson_id=? AND position=3",Long.class,id);
        context.getBean(vn.edu.multigame.lesson.repository.GameContentStore.class).select(fillGame,jdbc.queryForObject("SELECT id FROM quiz ORDER BY id LIMIT 1",Long.class),0,vn.edu.multigame.game.enums.GameMode.FILL_BLANK,"Test fill",List.of(new vn.edu.multigame.questionbank.dto.response.QuestionResponse(0,1,"Text {{blank}}",null,null,null,List.of("Word"),null,null,null,null,null,vn.edu.multigame.questionbank.enums.TextMatchPolicy.EXACT)));
        String path="/api/teaching/lessons/"+id,studentPath="/api/student/lessons/"+id;
        assertThat(students.getFirst().call("GET","/api/student/classes/"+course+"/lessons",null,200)).isEmpty();
        assertThat(students.getFirst().call("GET",studentPath,null,404).toString()).doesNotContain("Hidden lesson title","Private QUIZ");
        outsider.call("GET",path,null,403);
        teacher.call("POST",path+"/start",revision(lesson),409);
        // MySQL independently rejects N+1, a fourth slot, and a budget sum >100.
        assertThatThrownBy(()->jdbc.update("INSERT INTO lesson(course_class_id,lesson_number,title,create_request,create_fingerprint) VALUES(?,2,'Extra',?,?)",course,UUID.randomUUID().toString(),"a".repeat(64))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO lesson_game(lesson_id,position,mode,title,budget) VALUES(?,4,'ASSIGNMENT','Extra',0)",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE lesson_game SET budget=21 WHERE lesson_id=? AND position=1",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE lesson SET status='COMPLETED' WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        lesson=teacher.call("PUT",path,Map.of("revision",lesson.get("revision").asLong(),"plan",plan()),200);
        lesson=teacher.call("PUT",path+"/visibility",Map.of("revision",lesson.get("revision").asLong(),"visible",true),200);
        students.getFirst().call("GET",studentPath,null,200);
        lesson=teacher.call("PUT",path+"/visibility",Map.of("revision",lesson.get("revision").asLong(),"visible",false),200);
        students.getFirst().call("GET",studentPath,null,404);
        lesson=teacher.call("PUT",path+"/visibility",Map.of("revision",lesson.get("revision").asLong(),"visible",true),200);
        lesson=teacher.call("POST",path+"/start",revision(lesson),200);
        teacher.call("PUT",path,Map.of("revision",lesson.get("revision").asLong(),"plan",plan()),409);
        teacher.call("PUT",path+"/visibility",Map.of("revision",lesson.get("revision").asLong(),"visible",false),409);
        teacher.call("POST",path+"/games/2/start",revision(lesson),409);
        lesson=teacher.call("POST",path+"/games/1/start",revision(lesson),200);
        students.getFirst().call("POST",studentPath+"/games/1/participate",null,409);
        for(int i=0;i<3;i++) students.get(i).call("POST",studentPath+"/attend",null,200);
        for(int i=0;i<2;i++) students.get(i).call("POST",studentPath+"/games/1/participate",null,200);
        students.getFirst().call("POST",studentPath+"/games/1/participate",null,200);
        var principal=new AuthPrincipal(context.getBean(UserRepository.class).findById(tid).orElseThrow());
        var auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        var service=context.getBean(LessonService.class);
        assertThatThrownBy(()->service.completeGame(auth,id,1,Map.of(ids.get(0),new BigDecimal("21")))).isInstanceOf(LessonFailure.class);
        assertThatThrownBy(()->service.completeGame(auth,id,1,Map.of(ids.get(3),BigDecimal.ONE))).isInstanceOf(LessonFailure.class);
        var scores=Map.of(ids.get(0),new BigDecimal("20.00"),ids.get(1),new BigDecimal("15.00"));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->service.completeGame(auth,id,1,scores));var b=executor.submit(()->service.completeGame(auth,id,1,scores));
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_game_score",Integer.class)).isEqualTo(2);
        lesson=teacher.call("GET",path,null,200);assertThat(lesson.get("status").asText()).isEqualTo("RUNNING");
        lesson=teacher.call("POST",path+"/games/2/start",revision(lesson),200);
        for(int i:new int[]{1,2}) students.get(i).call("POST",studentPath+"/games/2/participate",null,200);
        service.completeGame(auth,id,2,Map.of(ids.get(1),new BigDecimal("40"),ids.get(2),new BigDecimal("10")));
        lesson=teacher.call("GET",path,null,200);assertThat(lesson.get("status").asText()).isEqualTo("RUNNING");
        lesson=teacher.call("POST",path+"/games/3/start",revision(lesson),200);
        for(int i:new int[]{0,2}) students.get(i).call("POST",studentPath+"/games/3/participate",null,200);
        service.completeGame(auth,id,3,Map.of(ids.get(0),new BigDecimal("40"),ids.get(2),new BigDecimal("40")));
        service.completeGame(auth,id,3,Map.of(ids.get(0),new BigDecimal("40"),ids.get(2),new BigDecimal("40")));
        lesson=teacher.call("GET",path,null,200);assertThat(lesson.get("status").asText()).isEqualTo("COMPLETED");
        for(int i=0;i<4;i++) {
            var snapshot=students.get(i).call("GET",studentPath,null,200);
            assertThat(snapshot.get("score").decimalValue()).isEqualByComparingTo(List.of("60","55","50","0").get(i));
        }
        assertThat(new Client(port,"lesson_a").call("GET",studentPath,null,200).get("score").decimalValue()).isEqualByComparingTo("60");
        var progress=teacher.call("GET","/api/teaching/classes/"+course+"/lesson-progress",null,200);
        assertThat(progress.get("created").asInt()).isEqualTo(1);assertThat(progress.get("completed").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id=?",Integer.class,id)).isEqualTo(3);
        students.getFirst().call("POST",studentPath+"/complete",Map.of("score",100),404);
        assertThatThrownBy(()->jdbc.update("UPDATE lesson_game SET budget=99 WHERE lesson_id=? AND position=1",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE lesson_game_score SET score=99 WHERE student_id=?",ids.getFirst())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
