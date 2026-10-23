package vn.edu.multigame.questionbank.controller;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.user.repository.UserRepository;
import vn.edu.multigame.lesson.service.LessonService;
import static org.assertj.core.api.Assertions.*;

/** Exercises source ownership and detached game snapshots on the owned baseline database. */
public class LearningBankChecks {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static class Client {
        final int port;
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        Client(int port,String name) throws Exception {
            this.port=port;call("POST","/api/auth/login",Map.of("username",name,"password","StudentPass123!"),200);
        }
        HttpResponse<String> send(String method,String path,Object body,boolean csrf) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
            if(csrf) {var token=JSON.readTree(send("GET","/api/auth/csrf",null,false).body());builder.header(token.get("headerName").asText(),token.get("token").asText());}
            return http.send(builder.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode call(String method,String path,Object body,int status) throws Exception {
            var response=send(method,path,body,!method.equals("GET"));
            assertThat(response.statusCode()).as(method+" "+path+": "+response.body()).isEqualTo(status);
            return response.body().isEmpty()?null:JSON.readTree(response.body());
        }
    }
    private static Map<String,Object> quizQuestion(String text,String answer) {
        return Map.of("content",text,"options",Map.of("A","ANSWER_SECRET_A","B","Option B","C","Option C","D","Option D"),"correctAnswer",answer);
    }
    private static Map<String,Object> bank(String mode,String visibility,List<?> questions) {return Map.of("title","Source "+mode,"mode",mode,"visibility",visibility,"questions",questions);}
    private static Map<String,Object> plan(String last) {
        return Map.of("requestId",UUID.randomUUID().toString(),"title","Bank content lesson","slots",List.of(
                Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","QUIZ","title","Quiz","budget",40),Map.of("mode",last,"title","Third game","budget",40)));
    }
    private static JsonNode select(Client teacher,String path,long lesson,long quiz) throws Exception {
        var parent=teacher.call("GET","/api/teaching/lessons/"+lesson,null,200);
        return teacher.call("POST",path,Map.of("quizId",quiz,"lessonRevision",parent.get("revision").asLong()),200);
    }
    public static void verify(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var a=new Client(port,"teacher_test");var b=new Client(port,"teacher_second");var bAgain=new Client(port,"teacher_second");
        var admin=new Client(port,"admin_test");var student=new Client(port,"lesson_a");
        long teacherB=b.call("GET","/api/auth/me",null,200).get("id").asLong();
        int originalBCount=jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE owner_user_id=?",Integer.class,teacherB);
        var question=quizQuestion("Original question","A");
        long publicId=a.call("POST","/api/quizzes",bank("QUIZ","PUBLIC",List.of(question)),201).get("id").asLong();
        long privateId=a.call("POST","/api/quizzes",bank("QUIZ","PRIVATE",List.of(question)),201).get("id").asLong();
        var preview=b.call("GET","/api/quizzes/"+publicId+"/preview",null,200);
        assertThat(preview.get("questions").get(0).get("correctAnswer").asText()).isEqualTo("A");
        assertThat(preview.get("questions").get(0).get("options").get("A").asText()).isEqualTo("ANSWER_SECRET_A");
        b.call("GET","/api/quizzes/"+privateId+"/preview",null,404);
        b.call("GET","/api/quizzes/"+privateId,null,404);
        b.call("PUT","/api/quizzes/"+publicId,Map.of("revision",0,"title","Hijack","visibility","PUBLIC","questions",List.of(question)),403);
        b.call("DELETE","/api/quizzes/"+publicId+"?revision=0",null,403);
        for(String route:List.of("/api/quizzes","/api/quizzes/"+publicId,"/api/quizzes/"+publicId+"/preview")) student.call("GET",route,null,403);
        student.call("POST","/api/quizzes",bank("QUIZ","PUBLIC",List.of(question)),403);
        a.call("POST","/api/quizzes",bank("SONG","PUBLIC",List.of(question)),400);
        for(String text:List.of("No blank","Two {{blank}} and {{blank}}")) a.call("POST","/api/quizzes",bank("FILL_BLANK","PUBLIC",List.of(Map.of("content",text,"acceptedAnswers",List.of("Answer"),"matchingPolicy","EXACT"))),400);
        var blankQuestion=Map.of("content","Java: {{blank}} loads classes","acceptedAnswers",List.of("ClassLoader","CLASS_LOADER"),"matchingPolicy","EXACT");
        long fill=a.call("POST","/api/quizzes",bank("FILL_BLANK","PUBLIC",List.of(blankQuestion,Map.of("content","Lịch sử: {{blank}}","acceptedAnswers",List.of("Đáp Án Gốc"),"matchingPolicy","CASE_INSENSITIVE"))),201).get("id").asLong();
        var fillPreview=b.call("GET","/api/quizzes/"+fill+"/preview",null,200);
        assertThat(fillPreview.get("questions").get(0).get("acceptedAnswers").get(0).asText()).isEqualTo("ClassLoader");
        assertThat(fillPreview.get("questions").get(1).get("acceptedAnswers").get(0).asText()).isEqualTo("Đáp Án Gốc");
        long assignment=a.call("POST","/api/quizzes",bank("ASSIGNMENT","PUBLIC",List.of(Map.of("content","Explain the lesson in your own words"))),201).get("id").asLong();
        a.call("POST","/api/quizzes",bank("ASSIGNMENT","PUBLIC",List.of(Map.of("content","One"),Map.of("content","Two"))),400);
        assertThat(b.call("GET","/api/quizzes?mode=FILL_BLANK&scope=SHARED",null,200).get("items").size()).isEqualTo(1);
        assertThat(b.call("GET","/api/quizzes?scope=SHARED&q=fill_blank",null,200).get("items")).hasSize(1);
        assertThat(b.call("GET","/api/quizzes?q=no_such_title",null,200).get("items")).isEmpty();
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","BANK_COPY","name","Snapshot class","requiredLessons",2),201).get("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+teacherB,null,200);
        long lesson=b.call("POST","/api/teaching/classes/"+course+"/lessons",plan("FILL_BLANK"),201).get("id").asLong();
        String quizPath="/api/teaching/lessons/"+lesson+"/games/2/content",fillPath="/api/teaching/lessons/"+lesson+"/games/3/content";
        a.call("GET",quizPath,null,403);student.call("GET",quizPath,null,403);
        b.call("POST",quizPath,Map.of("quizId",privateId,"lessonRevision",0),404);
        b.call("POST",quizPath,Map.of("quizId",fill,"lessonRevision",0),409);
        var copied=select(b,quizPath,lesson,publicId);select(b,fillPath,lesson,fill);
        assertThat(copied.get("sourceRevision").asLong()).isEqualTo(0);
        var localQuestion=quizQuestion("Local question only","B");
        var localEdit=Map.of("revision",0,"title","Local copy title","questions",List.of(localQuestion,quizQuestion("Added locally","D")));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var left=executor.submit(()->b.send("PUT",quizPath,localEdit,true).statusCode());
            var right=executor.submit(()->bAgain.send("PUT",quizPath,localEdit,true).statusCode());
            assertThat(List.of(left.get(10,TimeUnit.SECONDS),right.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(a.call("GET","/api/quizzes/"+publicId+"/preview",null,200).get("questions")).hasSize(1);
        assertThat(b.call("GET",quizPath,null,200).get("questions")).hasSize(2);
        // Changing budgets/titles in a draft preserves the copied game content.
        var parent=b.call("GET","/api/teaching/lessons/"+lesson,null,200);
        b.call("PUT","/api/teaching/lessons/"+lesson,Map.of("revision",parent.get("revision").asLong(),"plan",plan("FILL_BLANK")),200);
        assertThat(b.call("GET",quizPath,null,200).get("title").asText()).isEqualTo("Local copy title");
        a.call("PUT","/api/quizzes/"+publicId,Map.of("revision",0,"title","Changed original","visibility","PRIVATE","questions",List.of(quizQuestion("Changed original question","C"))),200);
        b.call("GET","/api/quizzes/"+publicId+"/preview",null,404);
        assertThat(b.call("GET",quizPath,null,200).get("questions").get(0).get("content").asText()).isEqualTo("Local question only");
        a.call("DELETE","/api/quizzes/"+publicId+"?revision=1",null,204);
        assertThat(b.call("GET",quizPath,null,200).get("sourceRevision").asLong()).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE owner_user_id=?",Integer.class,teacherB)).isEqualTo(originalBCount);
        String code=b.call("GET","/api/teaching/classes/"+course+"/room",null,200).get("joinCode").asText();
        long sid=student.call("GET","/api/auth/me",null,200).get("id").asLong();
        student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
        b.call("POST","/api/teaching/classes/"+course+"/members/"+sid+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        student.call("GET","/api/student/lessons/"+lesson,null,404);
        parent=b.call("GET","/api/teaching/lessons/"+lesson,null,200);
        parent=b.call("PUT","/api/teaching/lessons/"+lesson+"/visibility",Map.of("revision",parent.get("revision").asLong(),"visible",true),200);
        assertThat(student.call("GET","/api/student/lessons/"+lesson,null,200).toString()).doesNotContain("acceptedAnswers","correctAnswer","Local question only","ANSWER_SECRET_A","ClassLoader");
        parent=b.call("POST","/api/teaching/lessons/"+lesson+"/start",Map.of("revision",parent.get("revision").asLong()),200);
        // A READY game's private copy can still be edited before that game starts.
        b.call("PUT",fillPath,Map.of("revision",0,"title","Local fill","questions",List.of(blankQuestion)),200);
        parent=b.call("GET","/api/teaching/lessons/"+lesson,null,200);
        b.call("POST","/api/teaching/lessons/"+lesson+"/games/1/start",Map.of("revision",parent.get("revision").asLong()),200);
        var principal=new AuthPrincipal(context.getBean(UserRepository.class).findById(teacherB).orElseThrow());
        var auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        context.getBean(LessonService.class).completeGame(auth,lesson,1,Map.of());
        parent=b.call("GET","/api/teaching/lessons/"+lesson,null,200);
        b.call("POST","/api/teaching/lessons/"+lesson+"/games/2/start",Map.of("revision",parent.get("revision").asLong()),200);
        b.call("PUT",quizPath,Map.of("revision",1,"title","Rewrite running game","questions",List.of(question)),409);
        assertThatThrownBy(()->jdbc.update("UPDATE lesson_game_content SET title='Rewrite' WHERE game_id=?",copied.get("gameId").asLong())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        long lesson2=b.call("POST","/api/teaching/classes/"+course+"/lessons",plan("ASSIGNMENT"),201).get("id").asLong();
        var assignmentCopy=select(b,"/api/teaching/lessons/"+lesson2+"/games/3/content",lesson2,assignment);
        assertThat(assignmentCopy.get("questions").get(0).get("content").asText()).isEqualTo("Explain the lesson in your own words");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE title='Retained history'",Integer.class)).isEqualTo(1);
    }
}
