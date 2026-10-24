package vn.edu.multigame.realtime.session;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.edu.multigame.auth.security.AuthPrincipal;
import vn.edu.multigame.user.repository.UserRepository;
import vn.edu.multigame.lesson.service.*;
import vn.edu.multigame.lesson.repository.LessonRealtimeStore;
import vn.edu.multigame.realtime.connection.AuthenticatedSocketRegistry;
import vn.edu.multigame.realtime.timer.ServerClock;
import vn.edu.multigame.realtime.message.command.LessonCommand;
import static org.assertj.core.api.Assertions.*;

/** Real native WebSockets, transactions and timers on BaselineMySqlIT's owned schema. */
public class LessonRealtimeChecks {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static class Client {
        final int port;final String name;
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        Client(int port,String name) throws Exception {this.port=port;this.name=name;if(name!=null)call("POST","/api/auth/login",Map.of("username",name,"password","StudentPass123!"),200);}
        HttpResponse<String> send(String method,String path,Object body,boolean csrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
            if(csrf){var token=JSON.readTree(send("GET","/api/auth/csrf",null,false).body());request.header(token.get("headerName").asText(),token.get("token").asText());}
            return http.send(request.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode call(String method,String path,Object body,int expected) throws Exception {
            var result=send(method,path,body,!method.equals("GET"));assertThat(result.statusCode()).as(path+": "+result.body()).isEqualTo(expected);
            return result.body().isEmpty()?null:JSON.readTree(result.body());
        }
        Wire connect() throws Exception {return new Wire(this);}
    }
    private static class Wire implements WebSocket.Listener,AutoCloseable {
        final BlockingQueue<JsonNode> messages=new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed=new CompletableFuture<>();final StringBuilder partial=new StringBuilder();
        final java.util.concurrent.atomic.AtomicInteger classEvents=new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger scoreEvents=new java.util.concurrent.atomic.AtomicInteger();
        final WebSocket socket;
        Wire(Client client) throws Exception {
            socket=client.http.newWebSocketBuilder().header("Origin","http://localhost:8080").buildAsync(URI.create("ws://127.0.0.1:"+client.port+"/ws"),this).get(10,TimeUnit.SECONDS);
            await(n->n.path("type").asText().equals("AUTH_READY"));
        }
        public void onOpen(WebSocket socket){socket.request(1);}
        public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last) {
            partial.append(data);if(last)try {var frame=JSON.readTree(partial.toString());if(frame.path("type").asText().equals("CLASS_LESSONS_UPDATED"))classEvents.incrementAndGet();if(frame.path("type").asText().equals("SCORES_UPDATED"))scoreEvents.incrementAndGet();messages.add(frame);partial.setLength(0);}catch(Exception invalid){closed.completeExceptionally(invalid);}
            socket.request(1);return null;
        }
        public CompletionStage<?> onClose(WebSocket socket,int code,String reason){closed.complete(code);return null;}
        public void onError(WebSocket socket,Throwable failure){closed.completeExceptionally(failure);}
        JsonNode await(Predicate<JsonNode> predicate) throws Exception {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<until){var message=messages.poll(100,TimeUnit.MILLISECONDS);if(message!=null && predicate.test(message))return message;}
            throw new AssertionError("Expected WS frame did not arrive");
        }
        JsonNode send(LessonCommand command,String kind) throws Exception {
            socket.sendText(JSON.writeValueAsString(command),true).get(5,TimeUnit.SECONDS);
            var result=await(n->n.path("requestId").asText().equals(command.requestId()));
            assertThat(result.path("kind").asText()).as(result.toString()).isEqualTo(kind);return result;
        }
        JsonNode command(long lesson,String type,Object payload) throws Exception {return send(commandOf(lesson,type,payload),"ACK");}
        public void close(){socket.abort();}
    }
    private static LessonCommand commandOf(long lesson,String type,Object payload) {return new LessonCommand(1,"COMMAND",UUID.randomUUID().toString(),type,LessonCommand.Target.lesson(lesson),JSON.valueToTree(payload));}
    private static Authentication auth(ConfigurableApplicationContext context,String name) {
        var principal=new AuthPrincipal(context.getBean(UserRepository.class).findByUsername(name).orElseThrow());
        return UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
    }
    private static void awaitStatus(JdbcTemplate jdbc,long lesson,String expected) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until){if(expected.equals(jdbc.queryForObject("SELECT status FROM lesson_action_window WHERE lesson_id=?",String.class,lesson)))return;Thread.sleep(10);}
        assertThat(jdbc.queryForObject("SELECT status FROM lesson_action_window WHERE lesson_id=?",String.class,lesson)).isEqualTo(expected);
    }
    private static JsonNode awaitQuestion(Wire host,long lesson,int position,int question) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
        JsonNode frame=null;
        while(System.nanoTime()<until){
            frame=host.command(lesson,"RECONNECT_LESSON",Map.of());var window=frame.path("payload").path("window");
            if(window.path("position").asInt()==position && window.path("questionIndex").asInt()==question && window.path("status").asText().equals("OPEN"))return frame;
            Thread.sleep(25);
        }
        throw new AssertionError("Automatic question "+question+" did not open: "+frame);
    }
    private static JsonNode awaitAdvance(Wire host,long lesson,long token) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
        while(System.nanoTime()<until){
            var frame=host.command(lesson,"RECONNECT_LESSON",Map.of());var window=frame.path("payload").path("window");
            if(window.path("token").asLong()!=token || !window.path("status").asText().equals("OPEN"))return frame;
            Thread.sleep(25);
        }
        throw new AssertionError("Automatic deadline did not advance");
    }
    private static String fillAnswer(int question,boolean correct) {
        return switch(question) {
            case 1 -> correct?"\u0111\u1ea3ng c\u1ed9ng s\u1ea3n vi\u1ec7t nam":"Dang Cong san Viet Nam";
            case 2 -> correct?"java.lang.ClassLoader":"classloader";
            default -> correct?"H\u1ed3 Ch\u00ed Minh":"H\u1ed3 Ch\u00ed minh";
        };
    }
    public static void verifyFillBlank(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        long teacherId=teacher.call("GET","/api/auth/me",null,200).path("id").asLong();
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","FILLPLAY","name","Realtime class","requiredLessons",1),201).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+teacherId,null,200);
        String code=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).path("joinCode").asText();
        var students=new ArrayList<Client>();var ids=new ArrayList<Long>();
        for(String name:List.of("fill_a","fill_b","fill_c","fill_d")) {
            new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password","StudentPass123!"),201);
            var student=new Client(port,name);students.add(student);long id=student.call("GET","/api/auth/me",null,200).path("id").asLong();ids.add(id);
            student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+id+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        }
        var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Secret draft title","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","FILL_BLANK","title","Fill blank","budget",40),Map.of("mode","QUIZ","title","Quiz","budget",40)));
        long lesson=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",plan,201).path("id").asLong();String path="/api/teaching/lessons/"+lesson;
        long bank=teacher.call("POST","/api/quizzes",Map.of("title","Runtime bank","mode","FILL_BLANK","visibility","PRIVATE","questions",List.of(
                Map.of("content","L\u1ecbch s\u1eed \u0110\u1ea3ng: {{blank}}","acceptedAnswers",List.of("\u0110\u1ea3ng C\u1ed9ng s\u1ea3n Vi\u1ec7t Nam","\u0110CSVN"),"matchingPolicy","CASE_INSENSITIVE"),
                Map.of("content","Java: {{blank}} loads classes","acceptedAnswers",List.of("ClassLoader","java.lang.ClassLoader"),"matchingPolicy","EXACT"),
                Map.of("content","L\u1ecbch s\u1eed: {{blank}}","acceptedAnswers",List.of("H\u1ed3 Ch\u00ed Minh"),"matchingPolicy","EXACT"))),201).path("id").asLong();
        teacher.call("POST",path+"/games/2/content",Map.of("quizId",bank,"lessonRevision",0),200);
        jdbc.update("UPDATE lesson_game SET duration_ms=3000 WHERE lesson_id=? AND position=2",lesson);


        var wires=new ArrayList<Wire>();
        try(var host=teacher.connect()) {
            for(var student:students)wires.add(student.connect());
            teacher.call("PUT",path+"/visibility",Map.of("revision",1,"visible",true),200);
            var started=host.command(lesson,"START_LESSON",Map.of("revision",2));
            host.command(lesson,"START_LESSON_GAME",Map.of("position",1,"revision",started.path("payload").path("lesson").path("revision").asLong()));
            for(var wire:wires)wire.command(lesson,"ATTEND_LESSON",Map.of());
            var runtime=context.getBean(LessonRuntime.class);var service=context.getBean(LessonService.class);var teacherAuth=auth(context,"teacher_test");
            runtime.serial(teacherAuth,lesson,()->service.completeGame(teacherAuth,lesson,1,Map.of()));
            long revision=teacher.call("GET",path,null,200).path("revision").asLong();
            host.command(lesson,"START_LESSON_GAME",Map.of("position",2,"revision",revision));
            for(int i=0;i<3;i++)wires.get(i).command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",2));
            long game=jdbc.queryForObject("SELECT id FROM lesson_game WHERE lesson_id=? AND position=2",Long.class,lesson);
            for(int question=1;question<=3;question++) {
                var open=awaitQuestion(host,lesson,2,question);
                long token=open.path("payload").path("window").path("token").asLong();
                assertThat(open.toString()).doesNotContain("correctAnswer","acceptedAnswers","matchingPolicy","ClassLoader");
                var answer=commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value",fillAnswer(question,true)));
                wires.getFirst().send(answer,"ACK");if(question==1)assertThat(jdbc.queryForObject("SELECT score FROM lesson_game_score WHERE game_id=? AND student_id=?",java.math.BigDecimal.class,game,ids.getFirst())).isEqualByComparingTo("13.33");assertThat(wires.getFirst().send(answer,"ACK").path("replay").asBoolean()).isTrue();
                wires.getFirst().send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value",fillAnswer(question,false))),"ERROR");
                wires.get(2).command(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value",fillAnswer(question,false)));
                wires.get(3).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value",fillAnswer(question,true))),"ERROR");
                if(question==3) {
                    var previous=wires.getFirst();var replacement=students.getFirst().connect();wires.set(0,replacement);
                    assertThat(previous.closed.get(5,TimeUnit.SECONDS)).isEqualTo(4002);
                    var restored=replacement.command(lesson,"RECONNECT_LESSON",Map.of());
                    assertThat(restored.path("payload").path("window").path("submitted").asBoolean()).isTrue();
                    assertThat(restored.path("payload").path("fillBlank").path(Long.toString(game))).hasSize(3);
                    assertThat(restored.toString()).doesNotContain("correctAnswer");
                }
                if(question==1) {
                    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
                    long deadline=open.path("payload").path("window").path("deadlineAtMs").asLong();
                    try(var executor=Executors.newSingleThreadExecutor()) {
                        var block=executor.submit(()->runtime.serial(teacherAuth,lesson,()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
                        assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                        var queued=runtime.command(auth(context,"fill_b"),commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value",fillAnswer(1,true))),()->{});
                        try{Thread.sleep(Math.max(1,deadline-System.currentTimeMillis()+100));}finally{release.countDown();}
                        block.get(5,TimeUnit.SECONDS);assertThat(queued.get(5,TimeUnit.SECONDS).receivedAtMs()).isLessThan(deadline);
                    }finally{release.countDown();}
                    awaitAdvance(host,lesson,token);
                    wires.get(1).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value",fillAnswer(question,true))),"ERROR");
                } else {
                    awaitAdvance(host,lesson,token);
                }
                if(question<3)host.send(commandOf(lesson,"OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",question,"durationMs",1000)),"ERROR");
                var reconnect=wires.getFirst().command(lesson,"RECONNECT_LESSON",Map.of());
                assertThat(reconnect.path("payload").path("fillBlank").path(Long.toString(game))).hasSize(question);
            }
            assertThat(jdbc.queryForObject("SELECT status FROM lesson_game WHERE id=?",String.class,game)).isEqualTo("COMPLETED");
            for(int i=0;i<3;i++)assertThat(jdbc.queryForObject("SELECT score FROM lesson_game_score WHERE game_id=? AND student_id=?",java.math.BigDecimal.class,game,ids.get(i))).isEqualByComparingTo(new String[]{"40.00","13.33","0.00"}[i]);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_answer WHERE game_id=?",Integer.class,game)).isEqualTo(7);
            var legacy=JSON.createObjectNode().put("v",1).put("kind","COMMAND").put("requestId",UUID.randomUUID().toString()).put("type","USE_SPIN").put("questionIndex",1);
            legacy.set("target",JSON.valueToTree(Map.of("kind","GAME","id",1)));legacy.set("payload",JSON.createObjectNode());
            host.socket.sendText(legacy.toString(),true).get(5,TimeUnit.SECONDS);
            assertThat(host.await(n->n.path("kind").asText().equals("ERROR")).path("code").asText()).isEqualTo("LEGACY_GAME_DISABLED");
            assertThat(students.getFirst().send("POST","/api/rooms/1/start",Map.of("requestId",UUID.randomUUID().toString(),"revision",0,"questionCount",1),true).statusCode()).isEqualTo(403);
        }finally{wires.forEach(Wire::close);}
    }
    private static long boardLesson(ConfigurableApplicationContext context,Client teacher,List<Client> students,List<Long> ids,long course,int[] points) throws Exception {
        var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Recorded points","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",100),Map.of("mode","QUIZ","title","Quiz","budget",0),Map.of("mode","ASSIGNMENT","title","Assignment","budget",0)));
        var created=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",plan,201);long lesson=created.path("id").asLong();
        var service=context.getBean(LessonService.class);var actor=auth(context,teacher.name);
        service.visible(actor,lesson,new vn.edu.multigame.lesson.dto.LessonRequests.Visibility(0L,true));
        service.start(actor,lesson,1);service.startGame(actor,lesson,1,2);
        for(var student:students){service.attend(auth(context,student.name),lesson,null);service.attend(auth(context,student.name),lesson,1);}
        if(points!=null){var scores=new HashMap<Long,java.math.BigDecimal>();for(int i=0;i<4;i++)scores.put(ids.get(i),java.math.BigDecimal.valueOf(points[i]));service.completeGame(actor,lesson,1,scores);}
        return lesson;
    }
    public static void verifyBoards(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var a=new Client(port,"teacher_test");var b=new Client(port,"teacher_second");
        long teacherA=a.call("GET","/api/auth/me",null,200).path("id").asLong(),teacherB=b.call("GET","/api/auth/me",null,200).path("id").asLong();
        String root="/api/admin/academic/";
        long year=admin.call("POST",root+"years",Map.of("code","BOARD_Y1","name","Board year one"),201).path("id").asLong();
        long next=admin.call("POST",root+"years",Map.of("code","BOARD_Y2","name","Board year two"),201).path("id").asLong();
        long semester=admin.call("POST",root+"semesters",Map.of("academicYearId",year,"code","ONE","name","First"),201).path("id").asLong();
        long semester2=admin.call("POST",root+"semesters",Map.of("academicYearId",next,"code","ONE","name","First"),201).path("id").asLong();
        long subject=admin.call("POST",root+"subjects",Map.of("code","BOARD_S1","name","Board subject one"),201).path("id").asLong();
        long otherSubject=admin.call("POST",root+"subjects",Map.of("code","BOARD_S2","name","Board subject two"),201).path("id").asLong();
        long offering=admin.call("POST",root+"offerings",Map.of("semesterId",semester,"subjectId",subject),201).path("id").asLong();
        long offering2=admin.call("POST",root+"offerings",Map.of("semesterId",semester,"subjectId",otherSubject),201).path("id").asLong();
        long offering3=admin.call("POST",root+"offerings",Map.of("semesterId",semester2,"subjectId",subject),201).path("id").asLong();
        var students=new ArrayList<Client>();var ids=new ArrayList<Long>();
        for(String name:List.of("board_a","board_b","board_c","board_d")){
            new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen","Full name "+name,"maSinhVien","SV_"+name,"password","StudentPass123!"),201);
            var student=new Client(port,name);students.add(student);ids.add(student.call("GET","/api/auth/me",null,200).path("id").asLong());
        }
        long[] courses=new long[4];long[] offerings={offering,offering,offering2,offering3};
        for(int i=0;i<4;i++){
            courses[i]=admin.call("POST",root+"classes",Map.of("offeringId",offerings[i],"code","BOARD_"+i,"name","Board class "+i,"requiredLessons",2),201).path("id").asLong();
            var teacher=i==1?b:a;admin.call("PUT",root+"classes/"+courses[i]+"/teachers/"+(i==1?teacherB:teacherA),null,200);
            String code=teacher.call("GET","/api/teaching/classes/"+courses[i]+"/room",null,200).path("joinCode").asText();
            for(int n=0;n<4;n++){students.get(n).call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);teacher.call("POST","/api/teaching/classes/"+courses[i]+"/members/"+ids.get(n)+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);}
        }
        String config="/api/teaching/classes/"+courses[0]+"/rank-config",target="/api/teaching/classes/"+courses[1]+"/rank-config";
        var ranges=List.of(Map.of("name","Top","from",1,"to",2),Map.of("name","Next","from",3,"to",4));
        a.call("PUT",config,Map.of("revision",0,"ranges",ranges),200);b.call("PUT",config,Map.of("revision",1,"ranges",ranges),403);
        students.getFirst().call("PUT",config,Map.of("revision",1,"ranges",ranges),403);
        a.call("PUT",config,Map.of("revision",1,"ranges",List.of(Map.of("name","A","from",1,"to",2),Map.of("name","B","from",2,"to",3))),409);
        String share=a.call("POST",config+"/share",Map.of("revision",1),200).path("code").asText();
        b.call("POST",target+"/import",Map.of("revision",0,"code",share),200);
        b.call("PUT",target,Map.of("revision",1,"ranges",List.of(Map.of("name","Custom","from",1,"to",10))),200);
        assertThat(a.call("GET",config,null,200).path("ranges").get(0).path("name").asText()).isEqualTo("Top");
        long first=boardLesson(context,a,students,ids,courses[0],new int[]{40,40,30,0});
        boardLesson(context,b,students,ids,courses[1],new int[]{20,20,30,10});
        boardLesson(context,a,students,ids,courses[2],new int[]{90,0,0,0});boardLesson(context,a,students,ids,courses[3],new int[]{95,95,0,0});
        long second=boardLesson(context,a,students,ids,courses[0],null);
        var wires=new ArrayList<Wire>();
        try(var host=a.connect()){
            host.command(first,"SUBSCRIBE_LESSON",Map.of());for(var student:students){var wire=student.connect();wires.add(wire);wire.command(first,"SUBSCRIBE_LESSON",Map.of());}
            var scores=Map.of(ids.get(0),new java.math.BigDecimal("10"),ids.get(1),new java.math.BigDecimal("10"),ids.get(2),new java.math.BigDecimal("10"));
            context.getBean(LessonService.class).completeGame(auth(context,"teacher_test"),second,1,scores);
            for(var wire:wires){var event=wire.await(n->n.path("type").asText().equals("SCORES_UPDATED") && n.path("classroom").path("students").get(0).path("score").asInt()==50);assertThat(event.path("global").path("students")).hasSize(4);}
            host.await(n->n.path("type").asText().equals("SCORES_UPDATED"));
            context.getBean(LessonService.class).completeGame(auth(context,"teacher_test"),second,1,scores);
            var classroom=students.getFirst().call("GET","/api/learning/classes/"+courses[0]+"/leaderboard",null,200).path("students");
            assertThat(classroom).hasSize(4);assertThat(classroom.get(0).path("score").decimalValue()).isEqualByComparingTo("50");assertThat(classroom.get(1).path("position").asInt()).isEqualTo(1);assertThat(classroom.get(2).path("rank").asText()).isEqualTo("Next");
            assertThat(classroom.get(0).path("maSinhVien").asText()).startsWith("SV_");assertThat(classroom.get(0).path("hoTen").asText()).startsWith("Full name");
            var global=students.getFirst().call("GET","/api/learning/leaderboard?subjectId="+subject+"&academicYearId="+year,null,200).path("students");
            for(int i=0;i<3;i++){assertThat(global.get(i).path("score").decimalValue()).isEqualByComparingTo("70");assertThat(global.get(i).path("position").asInt()).isEqualTo(1);assertThat(global.get(i).has("rank")).isFalse();}assertThat(global.get(3).path("position").asInt()).isEqualTo(2);
            assertThat(a.call("GET","/api/learning/leaderboard?subjectId="+subject+"&academicYearId="+next,null,200).path("students").get(0).path("score").asInt()).isEqualTo(95);
            assertThat(a.call("GET","/api/learning/leaderboard?subjectId="+otherSubject+"&academicYearId="+year,null,200).path("students").get(0).path("score").asInt()).isEqualTo(90);
            b.call("GET","/api/learning/classes/"+courses[0]+"/leaderboard",null,403);
            // A score in a different class of the same subject/year refreshes this class subscriber's global board.
            long crossClass=boardLesson(context,b,students,ids,courses[1],null);
            context.getBean(LessonService.class).completeGame(auth(context,"teacher_second"),crossClass,1,Map.of(ids.get(3),new java.math.BigDecimal("5")));
            host.await(n->n.path("type").asText().equals("SCORES_UPDATED") && n.path("global").path("students").get(3).path("score").asInt()==15);
            // A rolled-back source-score update must not publish a scoreboard event.
            host.command(first,"RECONNECT_LESSON",Map.of());int delivered=host.scoreEvents.get();
            new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).execute(status->{jdbc.update("UPDATE lesson_score SET score=99 WHERE lesson_id=? AND student_id=?",second,ids.getFirst());context.getBean(vn.edu.multigame.lesson.repository.LessonStore.class).total(second,ids.getFirst());status.setRollbackOnly();return null;});
            assertThat(a.call("GET","/api/learning/classes/"+courses[0]+"/leaderboard",null,200).path("students").get(0).path("score").asInt()).isEqualTo(50);
            host.command(first,"RECONNECT_LESSON",Map.of());assertThat(host.scoreEvents.get()).isEqualTo(delivered);
        }finally{wires.forEach(Wire::close);}
    }
    public static void verifyAssignment(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        long teacherId=teacher.call("GET","/api/auth/me",null,200).path("id").asLong();
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","ASSIGNPLAY","name","Realtime class","requiredLessons",1),201).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+teacherId,null,200);
        String code=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).path("joinCode").asText();
        var students=new ArrayList<Client>();var ids=new ArrayList<Long>();
        for(String name:List.of("assign_a","assign_b","assign_c","assign_d")) {
            new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password","StudentPass123!"),201);
            var student=new Client(port,name);students.add(student);long id=student.call("GET","/api/auth/me",null,200).path("id").asLong();ids.add(id);
            student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+id+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        }
        var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Secret draft title","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","ASSIGNMENT","title","Assignment","budget",40),Map.of("mode","QUIZ","title","Quiz","budget",40)));
        long lesson=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",plan,201).path("id").asLong();String path="/api/teaching/lessons/"+lesson;
        long bank=teacher.call("POST","/api/quizzes",Map.of("title","Runtime bank","mode","ASSIGNMENT","visibility","PRIVATE","questions",List.of(Map.of("content","Explain network sessions"))),201).path("id").asLong();
        teacher.call("POST",path+"/games/2/content",Map.of("quizId",bank,"lessonRevision",0),200);
        jdbc.update("UPDATE lesson_game SET duration_ms=3000 WHERE lesson_id=? AND position=2",lesson);



        var wires=new ArrayList<Wire>();
        try(var host=teacher.connect()) {
            for(var student:students)wires.add(student.connect());
            host.command(lesson,"SET_ASSIGNMENT_DEADLINE",Map.of("position",2,"token",0,"deadlineAtMs",System.currentTimeMillis()+30000));
            teacher.call("PUT",path+"/visibility",Map.of("revision",2,"visible",true),200);
            var started=host.command(lesson,"START_LESSON",Map.of("revision",3));
            host.command(lesson,"START_LESSON_GAME",Map.of("position",1,"revision",started.path("payload").path("lesson").path("revision").asLong()));
            for(var wire:wires)wire.command(lesson,"ATTEND_LESSON",Map.of());
            var runtime=context.getBean(LessonRuntime.class);var teacherAuth=auth(context,"teacher_test");
            runtime.serial(teacherAuth,lesson,()->context.getBean(LessonService.class).completeGame(teacherAuth,lesson,1,Map.of()));
            host.command(lesson,"START_LESSON_GAME",Map.of("position",2,"revision",teacher.call("GET",path,null,200).path("revision").asLong()));
            for(var wire:wires)wire.command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",2));
            long game=jdbc.queryForObject("SELECT id FROM lesson_game WHERE lesson_id=? AND position=2",Long.class,lesson);
            var opened=host.command(lesson,"SET_ASSIGNMENT_DEADLINE",Map.of("position",2,"token",1,"deadlineAtMs",System.currentTimeMillis()+10000));long token=opened.path("payload").path("window").path("token").asLong();
            long latest=0,first=0;
            for(int i=1;i<=3;i++){
                var submit=commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","Draft "+i));
                var reply=wires.getFirst().send(submit,"ACK");wires.getFirst().send(submit,"ACK");
                latest=reply.path("payload").path("assignment").path(Long.toString(game)).path("latest").get(0).path("id").asLong();if(i==1)first=latest;
            }
            host.send(commandOf(lesson,"GRADE_ASSIGNMENT",Map.of("position",2,"studentId",ids.getFirst(),"submissionId",first,"score",20)),"ERROR");
            host.send(commandOf(lesson,"GRADE_ASSIGNMENT",Map.of("position",2,"studentId",ids.getFirst(),"submissionId",latest,"score",41)),"ERROR");
            wires.get(1).send(commandOf(lesson,"GRADE_ASSIGNMENT",Map.of("position",2,"studentId",ids.getFirst(),"submissionId",latest,"score",20)),"ERROR");
            host.command(lesson,"GRADE_ASSIGNMENT",Map.of("position",2,"studentId",ids.getFirst(),"submissionId",latest,"score",25));
            var extended=host.command(lesson,"SET_ASSIGNMENT_DEADLINE",Map.of("position",2,"token",token,"deadlineAtMs",System.currentTimeMillis()+20000));token=extended.path("payload").path("window").path("token").asLong();
            wires.get(2).command(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","Removed student private work"));
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+ids.get(2)+"/remove",Map.of("requestId",UUID.randomUUID().toString(),"revision",1),200);
            wires.get(2).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","Forbidden")),"ERROR");
            wires.get(3).command(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","Expelled student private work"));
            admin.call("POST","/api/admin/users/"+ids.get(3)+"/expel",Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0,"reason","Assignment test"),200);
            assertThat(wires.get(3).closed.get(5,TimeUnit.SECONDS)).isEqualTo(4001);
            // Shrinking the deadline preserves all three previously accepted versions.
            var shrunk=host.command(lesson,"SET_ASSIGNMENT_DEADLINE",Map.of("position",2,"token",token,"deadlineAtMs",System.currentTimeMillis()-1000));token=shrunk.path("payload").path("window").path("token").asLong();
            wires.getFirst().send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","Too late")),"ERROR");
            assertThat(students.getFirst().call("GET","/api/student/lessons/"+lesson+"/games/2/submissions",null,200)).hasSize(3);
            assertThat(students.get(1).call("GET","/api/student/lessons/"+lesson+"/games/2/submissions?studentId="+ids.getFirst(),null,200)).isEmpty();
            // Reopening accepts a new version and makes the previous grade stale.
            var reopened=host.command(lesson,"SET_ASSIGNMENT_DEADLINE",Map.of("position",2,"token",token,"deadlineAtMs",System.currentTimeMillis()+3000));token=reopened.path("payload").path("window").path("token").asLong();
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var submitted=commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","Final revised work"));
            try(var executor=Executors.newSingleThreadExecutor()) {
                var blocked=executor.submit(()->runtime.serial(teacherAuth,lesson,()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                var queued=runtime.command(auth(context,"assign_a"),submitted,()->{});
                long deadline=reopened.path("payload").path("window").path("deadlineAtMs").asLong();
                try{Thread.sleep(Math.max(1,deadline-System.currentTimeMillis()+100));}finally{release.countDown();}
                blocked.get(5,TimeUnit.SECONDS);var result=queued.get(5,TimeUnit.SECONDS);assertThat(result.receivedAtMs()).isLessThan(deadline);
                latest=result.snapshot().assignment().get(Long.toString(game)).latest().getFirst().id();
            }finally{release.countDown();}
            awaitStatus(jdbc,lesson,"CLOSED");
            host.command(lesson,"CLOSE_LESSON_WINDOW",Map.of("token",token));
            host.send(commandOf(lesson,"FINALIZE_ASSIGNMENT",Map.of("position",2)),"ERROR");
            var grading=commandOf(lesson,"GRADE_ASSIGNMENT",Map.of("position",2,"studentId",ids.getFirst(),"submissionId",latest,"score",35.25));host.send(grading,"ACK");host.send(grading,"ACK");
            var finalize=commandOf(lesson,"FINALIZE_ASSIGNMENT",Map.of("position",2));host.send(finalize,"ACK");host.send(finalize,"ACK");
            host.send(commandOf(lesson,"SET_ASSIGNMENT_DEADLINE",Map.of("position",2,"token",token,"deadlineAtMs",System.currentTimeMillis()+10000)),"ERROR");
            assertThat(jdbc.queryForObject("SELECT score FROM lesson_game_score WHERE game_id=? AND student_id=?",java.math.BigDecimal.class,game,ids.getFirst())).isEqualByComparingTo("35.25");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_game_score WHERE game_id=?",Integer.class,game)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_action_input WHERE game_id=?",Integer.class,game)).isEqualTo(6);
            assertThat(wires.getFirst().command(lesson,"RECONNECT_LESSON",Map.of()).path("payload").path("assignment").path(Long.toString(game)).path("latest").get(0).path("id").asLong()).isEqualTo(latest);
        }finally{wires.forEach(Wire::close);}
    }
    public static void verifyAchievements(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        long teacherId=teacher.call("GET","/api/auth/me",null,200).path("id").asLong();
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","PERFECTPLAY","name","Realtime class","requiredLessons",3),201).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+teacherId,null,200);
        String code=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).path("joinCode").asText();
        var students=new ArrayList<Client>();var ids=new ArrayList<Long>();
        for(String name:List.of("perfect_a","perfect_b","perfect_c","perfect_d")) {
            new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password","StudentPass123!"),201);
            var student=new Client(port,name);students.add(student);long id=student.call("GET","/api/auth/me",null,200).path("id").asLong();ids.add(id);
            student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+id+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        }
        var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Secret draft title","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","QUIZ","title","Quiz","budget",40),Map.of("mode","ASSIGNMENT","title","Assignment","budget",40)));
        long lesson=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",plan,201).path("id").asLong();String path="/api/teaching/lessons/"+lesson;
        long bank=teacher.call("POST","/api/quizzes",Map.of("title","Runtime bank","mode","QUIZ","visibility","PRIVATE","questions",java.util.stream.IntStream.rangeClosed(1,10).mapToObj(i->Map.of("content","Question "+i,"options",Map.of("A","One","B","Two","C","Three","D","Four"),"correctAnswer","B")).toList()),201).path("id").asLong();
        teacher.call("POST",path+"/games/2/content",Map.of("quizId",bank,"lessonRevision",0),200);
        jdbc.update("UPDATE lesson_game SET duration_ms=3000 WHERE lesson_id=? AND position=2",lesson);


        var wires=new ArrayList<Wire>();
        try(var host=teacher.connect()) {
            for(var student:students)wires.add(student.connect());
            teacher.call("PUT",path+"/visibility",Map.of("revision",1,"visible",true),200);
            var started=host.command(lesson,"START_LESSON",Map.of("revision",2));
            host.command(lesson,"START_LESSON_GAME",Map.of("position",1,"revision",started.path("payload").path("lesson").path("revision").asLong()));
            for(var wire:wires)wire.command(lesson,"ATTEND_LESSON",Map.of());
            var runtime=context.getBean(LessonRuntime.class);var service=context.getBean(LessonService.class);var teacherAuth=auth(context,"teacher_test");
            runtime.serial(teacherAuth,lesson,()->service.completeGame(teacherAuth,lesson,1,Map.of()));
            long revision=teacher.call("GET",path,null,200).path("revision").asLong();
            host.command(lesson,"START_LESSON_GAME",Map.of("position",2,"revision",revision));
            for(int i=0;i<3;i++)wires.get(i).command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",2));
            long game=jdbc.queryForObject("SELECT id FROM lesson_game WHERE lesson_id=? AND position=2",Long.class,lesson);
            for(int question=1;question<=10;question++) {
                var open=awaitQuestion(host,lesson,2,question);
                long token=open.path("payload").path("window").path("token").asLong();
                assertThat(open.toString()).doesNotContain("correctAnswer","acceptedAnswers");
                if(question==1)wires.getFirst().send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","SPIN")),"ERROR");
                var answer=commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B"));
                wires.getFirst().send(answer,"ACK");if(question==1)assertThat(jdbc.queryForObject("SELECT score FROM lesson_game_score WHERE game_id=? AND student_id=?",java.math.BigDecimal.class,game,ids.getFirst())).isEqualByComparingTo("4.00");assertThat(wires.getFirst().send(answer,"ACK").path("replay").asBoolean()).isTrue();
                wires.getFirst().send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","A")),"ERROR");
                wires.get(2).command(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","A"));
                wires.get(3).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B")),"ERROR");
                if(question==3) {
                    var previous=wires.getFirst();var replacement=students.getFirst().connect();wires.set(0,replacement);
                    assertThat(previous.closed.get(5,TimeUnit.SECONDS)).isEqualTo(4002);
                    var restored=replacement.command(lesson,"RECONNECT_LESSON",Map.of());
                    assertThat(restored.path("payload").path("window").path("submitted").asBoolean()).isTrue();
                    assertThat(restored.path("payload").path("quiz").path(Long.toString(game))).hasSize(3);
                    assertThat(restored.toString()).doesNotContain("correctAnswer");
                }
                if(question==1) {
                    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
                    long deadline=open.path("payload").path("window").path("deadlineAtMs").asLong();
                    try(var executor=Executors.newSingleThreadExecutor()) {
                        var block=executor.submit(()->runtime.serial(teacherAuth,lesson,()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
                        assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                        var queued=runtime.command(auth(context,"perfect_b"),commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B")),()->{});
                        try{Thread.sleep(Math.max(1,deadline-System.currentTimeMillis()+100));}finally{release.countDown();}
                        block.get(5,TimeUnit.SECONDS);assertThat(queued.get(5,TimeUnit.SECONDS).receivedAtMs()).isLessThan(deadline);
                    }finally{release.countDown();}
                    awaitAdvance(host,lesson,token);
                    wires.get(1).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B")),"ERROR");
                } else {
                    awaitAdvance(host,lesson,token);
                }
                if(question<10)host.send(commandOf(lesson,"OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",question,"durationMs",1000)),"ERROR");
                var reconnect=wires.getFirst().command(lesson,"RECONNECT_LESSON",Map.of());
                assertThat(reconnect.path("payload").path("quiz").path(Long.toString(game))).hasSize(question);
            }
            assertThat(jdbc.queryForObject("SELECT status FROM lesson_game WHERE id=?",String.class,game)).isEqualTo("COMPLETED");
            for(int i=0;i<3;i++)assertThat(jdbc.queryForObject("SELECT score FROM lesson_game_score WHERE game_id=? AND student_id=?",java.math.BigDecimal.class,game,ids.get(i))).isEqualByComparingTo(new String[]{"40.00","4.00","0.00"}[i]);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_answer WHERE game_id=?",Integer.class,game)).isEqualTo(21);
            var legacy=JSON.createObjectNode().put("v",1).put("kind","COMMAND").put("requestId",UUID.randomUUID().toString()).put("type","USE_SPIN").put("questionIndex",1);
            legacy.set("target",JSON.valueToTree(Map.of("kind","GAME","id",1)));legacy.set("payload",JSON.createObjectNode());
            host.socket.sendText(legacy.toString(),true).get(5,TimeUnit.SECONDS);
            assertThat(host.await(n->n.path("kind").asText().equals("ERROR")).path("code").asText()).isEqualTo("LEGACY_GAME_DISABLED");
            assertThat(students.getFirst().send("POST","/api/rooms/1/start",Map.of("requestId",UUID.randomUUID().toString(),"revision",0,"questionCount",1),true).statusCode()).isEqualTo(403);
            var profile=students.getFirst().call("GET","/api/learning/profile",null,200);
            assertThat(profile.path("hoTen").asText()).isEqualTo("perfect_a");assertThat(profile.path("maSinhVien").asText()).isEqualTo("PERFECT_A");
            assertThat(profile.path("achievements")).hasSize(1);assertThat(profile.path("achievements").get(0).path("type").asText()).isEqualTo("PERFECT");
            assertThat(profile.path("stats").path("totalPoints").decimalValue()).isEqualByComparingTo("40");
            assertThat(students.get(1).call("GET","/api/learning/profile",null,200).path("achievements")).isEmpty();
            students.get(1).call("GET","/api/learning/profile?userId="+ids.getFirst(),null,403);
            assertThat(students.getFirst().call("GET","/api/learning/history?classId="+course,null,200).path("items")).hasSize(1);
            // A one-question Quiz cannot add another PERFECT, even with full marks and three players.
            var secondPlan=new HashMap<String,Object>(plan);secondPlan.put("requestId",UUID.randomUUID().toString());
            long single=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",secondPlan,201).path("id").asLong();
            long oneBank=teacher.call("POST","/api/quizzes",Map.of("title","One question","mode","QUIZ","visibility","PRIVATE","questions",List.of(Map.of("content","One question","options",Map.of("A","One","B","Two","C","Three","D","Four"),"correctAnswer","B"))),201).path("id").asLong();
            teacher.call("POST","/api/teaching/lessons/"+single+"/games/2/content",Map.of("quizId",oneBank,"lessonRevision",0),200);
            jdbc.update("UPDATE lesson_game SET duration_ms=3000 WHERE lesson_id=? AND position=2",single);
            teacher.call("PUT","/api/teaching/lessons/"+single+"/visibility",Map.of("revision",1,"visible",true),200);
            var singleStarted=host.command(single,"START_LESSON",Map.of("revision",2));host.command(single,"START_LESSON_GAME",Map.of("position",1,"revision",singleStarted.path("payload").path("lesson").path("revision").asLong()));
            for(var wire:wires)wire.command(single,"ATTEND_LESSON",Map.of());
            runtime.serial(teacherAuth,single,()->service.completeGame(teacherAuth,single,1,Map.of()));
            host.command(single,"START_LESSON_GAME",Map.of("position",2,"revision",teacher.call("GET","/api/teaching/lessons/"+single,null,200).path("revision").asLong()));
            for(int i=0;i<3;i++)wires.get(i).command(single,"PARTICIPATE_LESSON_GAME",Map.of("position",2));
            long singleToken=awaitQuestion(host,single,2,1).path("payload").path("window").path("token").asLong();
            wires.getFirst().command(single,"SUBMIT_LESSON_ACTION",Map.of("token",singleToken,"value","B"));
            awaitAdvance(host,single,singleToken);
            host.send(commandOf(single,"CLOSE_LESSON_WINDOW",Map.of("token",singleToken)),"ERROR");
            assertThat(students.getFirst().call("GET","/api/learning/profile",null,200).path("achievements")).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM achievement_award WHERE user_id=? AND lesson_game_id IS NOT NULL",Integer.class,ids.getFirst())).isEqualTo(1);
            // Joining a third player after the first question opens cannot satisfy the initial-player threshold.
            var latePlan=new HashMap<String,Object>(plan);latePlan.put("requestId",UUID.randomUUID().toString());
            long late=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",latePlan,201).path("id").asLong();
            teacher.call("POST","/api/teaching/lessons/"+late+"/games/2/content",Map.of("quizId",bank,"lessonRevision",0),200);
            jdbc.update("UPDATE lesson_game SET duration_ms=1000 WHERE lesson_id=? AND position=2",late);
            teacher.call("PUT","/api/teaching/lessons/"+late+"/visibility",Map.of("revision",1,"visible",true),200);
            var lateStarted=host.command(late,"START_LESSON",Map.of("revision",2));host.command(late,"START_LESSON_GAME",Map.of("position",1,"revision",lateStarted.path("payload").path("lesson").path("revision").asLong()));
            for(var wire:wires)wire.command(late,"ATTEND_LESSON",Map.of());
            runtime.serial(teacherAuth,late,()->service.completeGame(teacherAuth,late,1,Map.of()));
            host.command(late,"START_LESSON_GAME",Map.of("position",2,"revision",teacher.call("GET","/api/teaching/lessons/"+late,null,200).path("revision").asLong()));
            for(int i=0;i<2;i++)wires.get(i).command(late,"PARTICIPATE_LESSON_GAME",Map.of("position",2));
            for(int i=1;i<=10;i++){
                long lateToken=awaitQuestion(host,late,2,i).path("payload").path("window").path("token").asLong();
                if(i==2)wires.get(2).command(late,"PARTICIPATE_LESSON_GAME",Map.of("position",2));
                wires.getFirst().command(late,"SUBMIT_LESSON_ACTION",Map.of("token",lateToken,"value","B"));
                awaitAdvance(host,late,lateToken);
            }
            assertThat(students.getFirst().call("GET","/api/learning/profile",null,200).path("achievements")).hasSize(1);
            var room=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200);
            long subject=room.path("subjectId").asLong(),year=room.path("academicYearId").asLong();
            assertThat(students.getFirst().call("GET","/api/learning/history?subjectId="+subject+"&academicYearId="+year,null,200).path("items")).hasSize(3);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+ids.getFirst()+"/remove",Map.of("requestId",UUID.randomUUID().toString(),"revision",1),200);
            assertThat(students.getFirst().call("GET","/api/learning/profile",null,200).path("achievements")).hasSize(1);
            assertThat(students.getFirst().call("GET","/api/learning/history",null,200).path("items")).hasSize(3);
            admin.call("POST","/api/admin/users/"+ids.getFirst()+"/expel",Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",0,"reason","History retention test"),200);
            assertThat(students.getFirst().send("GET","/api/learning/profile",null,false).statusCode()).isEqualTo(401);
            var retained=admin.call("GET","/api/learning/profile?userId="+ids.getFirst(),null,200);assertThat(retained.path("status").asText()).isEqualTo("EXPELLED");assertThat(retained.path("achievements")).hasSize(1);assertThat(retained.path("stats").path("totalPoints").decimalValue()).isEqualByComparingTo("120");
            assertThat(admin.call("GET","/api/learning/history?userId="+ids.getFirst(),null,200).path("items")).hasSize(3);
        }finally{wires.forEach(Wire::close);}
    }
    public static void verifyQuiz(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        long teacherId=teacher.call("GET","/api/auth/me",null,200).path("id").asLong();
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","QUIZPLAY","name","Realtime class","requiredLessons",1),201).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+teacherId,null,200);
        String code=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).path("joinCode").asText();
        var students=new ArrayList<Client>();var ids=new ArrayList<Long>();
        for(String name:List.of("quiz_a","quiz_b","quiz_c","quiz_d")) {
            new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password","StudentPass123!"),201);
            var student=new Client(port,name);students.add(student);long id=student.call("GET","/api/auth/me",null,200).path("id").asLong();ids.add(id);
            student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+id+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        }
        var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Secret draft title","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","QUIZ","title","Quiz","budget",40),Map.of("mode","ASSIGNMENT","title","Assignment","budget",40)));
        long lesson=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",plan,201).path("id").asLong();String path="/api/teaching/lessons/"+lesson;
        long bank=teacher.call("POST","/api/quizzes",Map.of("title","Runtime bank","mode","QUIZ","visibility","PRIVATE","questions",java.util.stream.IntStream.rangeClosed(1,7).mapToObj(i->Map.of("content","Question "+i,"options",Map.of("A","One","B","Two","C","Three","D","Four"),"correctAnswer","B")).toList()),201).path("id").asLong();
        teacher.call("POST",path+"/games/2/content",Map.of("quizId",bank,"lessonRevision",0),200);
        jdbc.update("UPDATE lesson_game SET duration_ms=3000 WHERE lesson_id=? AND position=2",lesson);


        var wires=new ArrayList<Wire>();
        try(var host=teacher.connect()) {
            for(var student:students)wires.add(student.connect());
            teacher.call("PUT",path+"/visibility",Map.of("revision",1,"visible",true),200);
            var started=host.command(lesson,"START_LESSON",Map.of("revision",2));
            host.command(lesson,"START_LESSON_GAME",Map.of("position",1,"revision",started.path("payload").path("lesson").path("revision").asLong()));
            for(var wire:wires)wire.command(lesson,"ATTEND_LESSON",Map.of());
            var runtime=context.getBean(LessonRuntime.class);var service=context.getBean(LessonService.class);var teacherAuth=auth(context,"teacher_test");
            runtime.serial(teacherAuth,lesson,()->service.completeGame(teacherAuth,lesson,1,Map.of()));
            long revision=teacher.call("GET",path,null,200).path("revision").asLong();
            host.command(lesson,"START_LESSON_GAME",Map.of("position",2,"revision",revision));
            for(int i=0;i<3;i++)wires.get(i).command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",2));
            long game=jdbc.queryForObject("SELECT id FROM lesson_game WHERE lesson_id=? AND position=2",Long.class,lesson);
            for(int question=1;question<=7;question++) {
                var open=awaitQuestion(host,lesson,2,question);
                long token=open.path("payload").path("window").path("token").asLong();
                assertThat(open.toString()).doesNotContain("correctAnswer","acceptedAnswers");
                if(question==1)wires.getFirst().send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","SPIN")),"ERROR");
                var answer=commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B"));
                wires.getFirst().send(answer,"ACK");if(question==1)assertThat(jdbc.queryForObject("SELECT score FROM lesson_game_score WHERE game_id=? AND student_id=?",java.math.BigDecimal.class,game,ids.getFirst())).isEqualByComparingTo("5.71");assertThat(wires.getFirst().send(answer,"ACK").path("replay").asBoolean()).isTrue();
                wires.getFirst().send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","A")),"ERROR");
                wires.get(2).command(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","A"));
                wires.get(3).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B")),"ERROR");
                if(question==3) {
                    var previous=wires.getFirst();var replacement=students.getFirst().connect();wires.set(0,replacement);
                    assertThat(previous.closed.get(5,TimeUnit.SECONDS)).isEqualTo(4002);
                    var restored=replacement.command(lesson,"RECONNECT_LESSON",Map.of());
                    assertThat(restored.path("payload").path("window").path("submitted").asBoolean()).isTrue();
                    assertThat(restored.path("payload").path("quiz").path(Long.toString(game))).hasSize(3);
                    assertThat(restored.toString()).doesNotContain("correctAnswer");
                }
                if(question==1) {
                    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
                    long deadline=open.path("payload").path("window").path("deadlineAtMs").asLong();
                    try(var executor=Executors.newSingleThreadExecutor()) {
                        var block=executor.submit(()->runtime.serial(teacherAuth,lesson,()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
                        assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                        var queued=runtime.command(auth(context,"quiz_b"),commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B")),()->{});
                        try{Thread.sleep(Math.max(1,deadline-System.currentTimeMillis()+100));}finally{release.countDown();}
                        block.get(5,TimeUnit.SECONDS);assertThat(queued.get(5,TimeUnit.SECONDS).receivedAtMs()).isLessThan(deadline);
                    }finally{release.countDown();}
                    awaitAdvance(host,lesson,token);
                    wires.get(1).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B")),"ERROR");
                } else {
                    awaitAdvance(host,lesson,token);
                }
                if(question<7)host.send(commandOf(lesson,"OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",question,"durationMs",1000)),"ERROR");
                var reconnect=wires.getFirst().command(lesson,"RECONNECT_LESSON",Map.of());
                assertThat(reconnect.path("payload").path("quiz").path(Long.toString(game))).hasSize(question);
            }
            assertThat(jdbc.queryForObject("SELECT status FROM lesson_game WHERE id=?",String.class,game)).isEqualTo("COMPLETED");
            for(int i=0;i<3;i++)assertThat(jdbc.queryForObject("SELECT score FROM lesson_game_score WHERE game_id=? AND student_id=?",java.math.BigDecimal.class,game,ids.get(i))).isEqualByComparingTo(new String[]{"40.00","5.71","0.00"}[i]);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_answer WHERE game_id=?",Integer.class,game)).isEqualTo(15);
            var legacy=JSON.createObjectNode().put("v",1).put("kind","COMMAND").put("requestId",UUID.randomUUID().toString()).put("type","USE_SPIN").put("questionIndex",1);
            legacy.set("target",JSON.valueToTree(Map.of("kind","GAME","id",1)));legacy.set("payload",JSON.createObjectNode());
            host.socket.sendText(legacy.toString(),true).get(5,TimeUnit.SECONDS);
            assertThat(host.await(n->n.path("kind").asText().equals("ERROR")).path("code").asText()).isEqualTo("LEGACY_GAME_DISABLED");
            assertThat(students.getFirst().send("POST","/api/rooms/1/start",Map.of("requestId",UUID.randomUUID().toString(),"revision",0,"questionCount",1),true).statusCode()).isEqualTo(403);
        }finally{wires.forEach(Wire::close);}
    }
    public static void verifyWheel(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        long teacherId=teacher.call("GET","/api/auth/me",null,200).path("id").asLong();
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","WHEEL","name","Realtime class","requiredLessons",1),201).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+teacherId,null,200);
        String code=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).path("joinCode").asText();
        var students=new ArrayList<Client>();var ids=new ArrayList<Long>();
        for(String name:List.of("wheel_a","wheel_b","wheel_c","wheel_d")) {
            new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password","StudentPass123!"),201);
            var student=new Client(port,name);students.add(student);long id=student.call("GET","/api/auth/me",null,200).path("id").asLong();ids.add(id);
            student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+id+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        }
        var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Secret draft title","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","QUIZ","title","Quiz","budget",40),Map.of("mode","ASSIGNMENT","title","Assignment","budget",40)));
        long lesson=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",plan,201).path("id").asLong();String path="/api/teaching/lessons/"+lesson;
        long bank=teacher.call("POST","/api/quizzes",Map.of("title","Runtime bank","mode","QUIZ","visibility","PRIVATE","questions",List.of(Map.of("content","Public question","options",Map.of("A","One","B","Two","C","Three","D","Four"),"correctAnswer","B"))),201).path("id").asLong();
        teacher.call("POST",path+"/games/2/content",Map.of("quizId",bank,"lessonRevision",0),200);
        jdbc.update("UPDATE lesson_game SET duration_ms=3000 WHERE lesson_id=? AND position=2",lesson);

        jdbc.update("UPDATE lesson_game SET selected_count=2 WHERE lesson_id=? AND position=1",lesson);
        var wires=new ArrayList<Wire>();
        try(var host=teacher.connect();var other=outsider.connect()) {
            for(var student:students)wires.add(student.connect());
            var config=teacher.call("PUT","/api/teaching/classes/"+course+"/wheel",Map.of("revision",0,"cells",List.of("Push ups","No activity")),200);
            teacher.call("PUT","/api/teaching/classes/"+course+"/wheel",Map.of("revision",1,"cells",List.of("No activity")),200);
            teacher.call("PUT",path+"/visibility",Map.of("revision",1,"visible",true),200);
            var started=host.command(lesson,"START_LESSON",Map.of("revision",2));
            host.command(lesson,"START_LESSON_GAME",Map.of("position",1,"revision",started.path("payload").path("lesson").path("revision").asLong()));
            for(var wire:wires){wire.command(lesson,"ATTEND_LESSON",Map.of());wire.command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",1));}
            String wheelPath=path+"/wheel/";
            var choose=Map.of("requestId",UUID.randomUUID().toString(),"revision",0,"pool",ids.subList(0,3),"k",2);
            outsider.call("POST",wheelPath+"select",choose,403);
            students.getFirst().call("POST",wheelPath+"select",choose,403);
            teacher.call("POST",wheelPath+"select",Map.of("requestId",UUID.randomUUID().toString(),"revision",0,"pool",ids,"k",5),409);
            teacher.call("POST",wheelPath+"select",Map.of("requestId",UUID.randomUUID().toString(),"revision",0,"pool",ids.subList(0,3),"k",1),409);
            var chosen=teacher.call("POST",wheelPath+"select",choose,200);
            assertThat(chosen.path("selected")).hasSize(2);
            assertThat(chosen.path("selected").get(0)).isNotEqualTo(chosen.path("selected").get(1));
            teacher.call("PUT","/api/teaching/classes/"+course+"/wheel",Map.of("revision",2,"cells",List.of("Changed later")),200);
            var spin=Map.of("requestId",UUID.randomUUID().toString(),"revision",1);
            JsonNode result;
            try(var executor=Executors.newFixedThreadPool(2)){
                var a=executor.submit(()->teacher.call("POST",wheelPath+"spin",spin,200));
                var b=executor.submit(()->teacher.call("POST",wheelPath+"spin",spin,200));
                result=a.get(10,TimeUnit.SECONDS);assertThat(b.get(10,TimeUnit.SECONDS)).isEqualTo(result);
            }
            assertThat(result.path("cells").get(0).asText()).isEqualTo("No activity");
            assertThat(result.path("challengeIndex").asInt()).isZero();
            for(int i=0;i<4;i++){
                var broadcast=wires.get(i).await(n->n.path("kind").asText().equals("EVENT") && n.path("payload").path("wheel").path("status").asText().equals("SPUN"));
                assertThat(broadcast.path("payload").path("wheel").path("spinStartedAtMs")).isEqualTo(result.path("spinStartedAtMs"));
                var snapshot=wires.get(i).command(lesson,"RECONNECT_LESSON",Map.of()).path("payload").path("wheel");
                assertThat(snapshot.path("challengeIndex")).isEqualTo(result.path("challengeIndex"));
                assertThat(snapshot.path("spinStartedAtMs")).isEqualTo(result.path("spinStartedAtMs"));
                boolean selected=false;for(var id:result.path("selected"))if(id.asLong()==ids.get(i))selected=true;
                assertThat(snapshot.path("selectedForChallenge").asBoolean()).isEqualTo(selected);
                assertThat(snapshot.has("notification")).isEqualTo(selected);
                assertThat(snapshot.has("grades")).isFalse();
                if(!selected && i<3)assertThat(jdbc.queryForObject("SELECT s.score FROM lesson_game_score s JOIN lesson_game g ON g.id=s.game_id WHERE g.lesson_id=? AND s.student_id=?",java.math.BigDecimal.class,lesson,ids.get(i))).isEqualByComparingTo("20");
            }
            teacher.call("POST",wheelPath+"spin",Map.of("requestId",UUID.randomUUID().toString(),"revision",2),409);
            long selected=result.path("selected").get(0).asLong();
            teacher.call("POST",wheelPath+"grade",Map.of("requestId",UUID.randomUUID().toString(),"revision",2,"grades",Map.of(Long.toString(selected),21)),409);
            teacher.call("POST",wheelPath+"grade",Map.of("requestId",UUID.randomUUID().toString(),"revision",2,"grades",Map.of(Long.toString(selected),10)),200);
            teacher.call("POST",wheelPath+"grade",Map.of("requestId",UUID.randomUUID().toString(),"revision",3,"grades",Map.of(Long.toString(selected),15.25)),200);
            var finish=Map.of("requestId",UUID.randomUUID().toString(),"revision",4);
            teacher.call("POST",wheelPath+"finalize",finish,200);teacher.call("POST",wheelPath+"finalize",finish,200);
            assertThat(jdbc.queryForObject("SELECT SUM(s.score) FROM lesson_game_score s JOIN lesson_game g ON g.id=s.game_id WHERE g.lesson_id=?",java.math.BigDecimal.class,lesson)).isEqualByComparingTo("35.25");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_game_score s JOIN lesson_game g ON g.id=s.game_id WHERE g.lesson_id=?",Integer.class,lesson)).isEqualTo(4);
            teacher.call("POST",wheelPath+"grade",Map.of("requestId",UUID.randomUUID().toString(),"revision",5,"grades",Map.of(Long.toString(selected),20)),409);
        }finally {wires.forEach(Wire::close);}
    }
    public static void verify(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        long teacherId=teacher.call("GET","/api/auth/me",null,200).path("id").asLong();
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","REALTIME","name","Realtime class","requiredLessons",1),201).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+teacherId,null,200);
        String code=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).path("joinCode").asText();
        var students=new ArrayList<Client>();var ids=new ArrayList<Long>();
        for(String name:List.of("realtime_a","realtime_b","realtime_c","realtime_d")) {
            new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password","StudentPass123!"),201);
            var student=new Client(port,name);students.add(student);long id=student.call("GET","/api/auth/me",null,200).path("id").asLong();ids.add(id);
            student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+id+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        }
        var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Secret draft title","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","ASSIGNMENT","title","Assignment","budget",40),Map.of("mode","FILL_BLANK","title","Assignment","budget",40)));
        long lesson=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",plan,201).path("id").asLong();String path="/api/teaching/lessons/"+lesson;
        long bank=teacher.call("POST","/api/quizzes",Map.of("title","Runtime bank","mode","ASSIGNMENT","visibility","PRIVATE","questions",List.of(Map.of("content","Public question"))),201).path("id").asLong();
        teacher.call("POST",path+"/games/2/content",Map.of("quizId",bank,"lessonRevision",0),200);
        jdbc.update("UPDATE lesson_game SET duration_ms=3000 WHERE lesson_id=? AND position=2",lesson);
        var wires=new ArrayList<Wire>();
        try(var host=teacher.connect();var other=outsider.connect()) {
            for(var student:students)wires.add(student.connect());
            assertThat(wires.getFirst().send(commandOf(lesson,"SUBSCRIBE_LESSON",Map.of()),"ERROR").toString()).doesNotContain("Secret draft title","Public question");
            other.send(commandOf(lesson,"SUBSCRIBE_LESSON",Map.of()),"ERROR");
            host.command(lesson,"SUBSCRIBE_LESSON",Map.of());
            teacher.call("PUT",path+"/visibility",Map.of("revision",1,"visible",true),200);
            var start=host.command(lesson,"START_LESSON",Map.of("revision",2));
            host.command(lesson,"START_LESSON_GAME",Map.of("position",1,"revision",start.path("payload").path("lesson").path("revision").asLong()));
            try(var executor=Executors.newFixedThreadPool(4)) {
                var futures=new ArrayList<Future<?>>();
                for(var wire:wires)futures.add(executor.submit(()->{wire.command(lesson,"ATTEND_LESSON",Map.of());wire.command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",1));return null;}));
                for(var future:futures)future.get(15,TimeUnit.SECONDS);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id=?",Integer.class,lesson)).isEqualTo(4);
            var runtime=context.getBean(LessonRuntime.class);var teacherAuth=auth(context,"teacher_test");var service=context.getBean(LessonService.class);
            runtime.serial(teacherAuth,lesson,()->service.completeGame(teacherAuth,lesson,1,Map.of()));
            long revision=teacher.call("GET",path,null,200).path("revision").asLong();
            host.command(lesson,"START_LESSON_GAME",Map.of("position",2,"revision",revision));
            for(int i=0;i<3;i++)wires.get(i).command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",2));
            var opened=host.command(lesson,"OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",1,"durationMs",10000));
            long token=opened.path("payload").path("window").path("token").asLong();
            assertThat(opened.toString()).doesNotContain("correctAnswer","acceptedAnswers","matchingPolicy","sourceQuizId");
            for(int i=0;i<3;i++) {
                var event=wires.get(i).await(n->n.path("kind").asText().equals("EVENT") && n.path("payload").path("window").path("token").asLong()==token);
                assertThat(event.path("payload").path("window").path("question").path("content").asText()).isEqualTo("Public question");
                assertThat(event.toString()).doesNotContain("correctAnswer","acceptedAnswers");
            }
            var unjoined=wires.get(3).command(lesson,"RECONNECT_LESSON",Map.of());
            assertThat(unjoined.path("payload").path("window").path("question").isNull()).isTrue();
            wires.get(3).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B")),"ERROR");
            wires.getFirst().send(commandOf(lesson,"CANCEL_LESSON_WINDOW",Map.of("token",token)),"ERROR");
            var answer=commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B"));
            var accepted=wires.getFirst().send(answer,"ACK");var replay=wires.getFirst().send(answer,"ACK");
            assertThat(replay.path("replay").asBoolean()).isTrue();assertThat(replay.path("sequence")).isEqualTo(accepted.path("sequence"));
            var mismatch=new LessonCommand(1,"COMMAND",answer.requestId(),answer.type(),answer.target(),JSON.valueToTree(Map.of("token",token,"value","C")));
            assertThat(wires.getFirst().send(mismatch,"ERROR").path("code").asText()).isEqualTo("REQUEST_ID_REUSED");
            wires.getFirst().send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","C")),"ACK");
            var previous=wires.getFirst();Wire replacement;
            var registry=context.getBean(AuthenticatedSocketRegistry.class);
            var oldServerSocket=registry.lessonSubscriptions().get(lesson).stream().filter(s->((AuthPrincipal)((Authentication)s.getPrincipal()).getPrincipal()).userId()==ids.getFirst()).findFirst().orElseThrow();
            var waiting=new CountDownLatch(1);var continueActor=new CountDownLatch(1);
            var staleCommand=commandOf(lesson,"ATTEND_LESSON",Map.of());
            try(var executor=Executors.newSingleThreadExecutor()) {
                var blocked=executor.submit(()->runtime.serial(teacherAuth,lesson,()->{waiting.countDown();try {assertThat(continueActor.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException interrupted){throw new IllegalStateException(interrupted);}return true;}));
                assertThat(waiting.await(5,TimeUnit.SECONDS)).isTrue();
                var queued=runtime.command(auth(context,"realtime_a"),staleCommand,()->registry.requireCurrent(oldServerSocket));
                try {replacement=students.getFirst().connect();wires.set(0,replacement);assertThat(previous.closed.get(5,TimeUnit.SECONDS)).isEqualTo(4002);}
                finally {continueActor.countDown();}
                blocked.get(5,TimeUnit.SECONDS);
                assertThatThrownBy(()->queued.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(vn.edu.multigame.realtime.connection.SocketReplaced.class);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_command_receipt WHERE actor_id=? AND request_id=?",Integer.class,ids.getFirst(),staleCommand.requestId())).isZero();
            } finally {continueActor.countDown();}
            assertThat(replacement.command(lesson,"RECONNECT_LESSON",Map.of()).path("payload").path("window").path("submitted").asBoolean()).isTrue();
            assertThat(replacement.send(answer,"ACK").path("replay").asBoolean()).isTrue();
            host.command(lesson,"CANCEL_LESSON_WINDOW",Map.of("token",token));
            awaitStatus(jdbc,lesson,"CANCELLED");
            // A command admitted before the deadline remains valid while its actor is blocked.
            var timed=host.command(lesson,"OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",1,"durationMs",1500));
            long next=timed.path("payload").path("window").path("token").asLong(),deadline=timed.path("payload").path("window").path("deadlineAtMs").asLong();
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var studentAuth=auth(context,"realtime_b");var lateQueued=commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",next,"value","A"));
            try(var executor=Executors.newSingleThreadExecutor()) {
                var blocked=executor.submit(()->runtime.serial(teacherAuth,lesson,()->{entered.countDown();try {assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException interrupted){throw new IllegalStateException(interrupted);}return true;}));
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                var queued=runtime.command(studentAuth,lateQueued,()->{});
                try {Thread.sleep(Math.max(1,deadline-System.currentTimeMillis()+100));}finally {release.countDown();}
                blocked.get(5,TimeUnit.SECONDS);var result=queued.get(5,TimeUnit.SECONDS);
                assertThat(result.receivedAtMs()).isLessThan(deadline);assertThat(System.currentTimeMillis()).isGreaterThan(deadline);
            } finally {release.countDown();}
            awaitStatus(jdbc,lesson,"CLOSED");
            wires.get(2).send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",next,"value","B")),"ERROR");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_action_input WHERE lesson_id=? AND window_token=?",Integer.class,lesson,next)).isEqualTo(1);
            // Cancel/answer/finish share the parent lock and cannot create duplicate input or score.
            var raceOpen=host.command(lesson,"OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",1,"durationMs",5000));long raceToken=raceOpen.path("payload").path("window").path("token").asLong();
            try(var executor=Executors.newFixedThreadPool(2)) {
                var a=executor.submit(()->runtime.command(auth(context,"realtime_c"),commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",raceToken,"value","B")),()->{}));
                var b=executor.submit(()->runtime.command(teacherAuth,commandOf(lesson,"CANCEL_LESSON_WINDOW",Map.of("token",raceToken)),()->{}));
                try {a.get(5,TimeUnit.SECONDS).get(5,TimeUnit.SECONDS);}catch(ExecutionException rejected){assertThat(rejected.getCause()).isInstanceOf(LessonFailure.class);}
                b.get(5,TimeUnit.SECONDS).get(5,TimeUnit.SECONDS);
            }
            awaitStatus(jdbc,lesson,"CANCELLED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_action_input WHERE lesson_id=? AND window_token=?",Integer.class,lesson,raceToken)).isBetween(0,1);
            teacher.call("POST","/api/teaching/classes/"+course+"/members/"+ids.get(3)+"/remove",Map.of("requestId",UUID.randomUUID().toString(),"revision",1),200);
            wires.get(3).await(n->n.path("type").asText().equals("LESSON_ACCESS_REVOKED"));
            wires.get(3).send(commandOf(lesson,"RECONNECT_LESSON",Map.of()),"ERROR");
            students.get(2).call("POST","/api/auth/keepalive",null,204);
            assertThat(students.get(2).send("POST","/api/auth/keepalive",null,false).statusCode()).isEqualTo(403);
            students.get(2).call("POST","/api/auth/logout",null,204);
            assertThat(wires.get(2).closed.get(5,TimeUnit.SECONDS)).isEqualTo(4001);
            assertThat(students.get(2).send("POST","/api/auth/keepalive",null,true).statusCode()).isEqualTo(401);
            // Restart the actor runtime with the same durable state and rearm the persisted deadline.
            host.command(lesson,"OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",1,"durationMs",700));runtime.close();
            try(var recovered=new LessonRuntime(context.getBean(ServerClock.class),context.getBean(LessonRealtimeStore.class),context.getBean(LessonRealtimeTransactions.class),context.getBean(AuthenticatedSocketRegistry.class))) {
                recovered.recover();
                var retry=recovered.command(studentAuth,lateQueued,()->{}).get(5,TimeUnit.SECONDS);
                assertThat(retry.replay()).isTrue();assertThat(retry.snapshot().lesson().attending()).isTrue();
                awaitStatus(jdbc,lesson,"CLOSED");
                var last=recovered.command(teacherAuth,commandOf(lesson,"OPEN_LESSON_WINDOW",Map.of("position",2,"questionIndex",1,"durationMs",100)),()->{}).get(5,TimeUnit.SECONDS);
                long lastToken=last.snapshot().window().token();
                try(var executor=Executors.newFixedThreadPool(3)) {
                    var a=executor.submit(()->service.completeGame(teacherAuth,lesson,2,Map.of()));
                    var b=executor.submit(()->service.completeGame(teacherAuth,lesson,2,Map.of()));
                    var c=recovered.command(studentAuth,commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",lastToken,"value","B")),()->{});
                    a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);
                    try {c.get(5,TimeUnit.SECONDS);}catch(ExecutionException rejected){assertThat(rejected.getCause()).isInstanceOf(LessonFailure.class);}
                }
                awaitStatus(jdbc,lesson,"CLOSED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_game_score s JOIN lesson_game g ON g.id=s.game_id WHERE g.lesson_id=? AND g.position=2",Integer.class,lesson)).isEqualTo(3);
            }
        } finally {wires.forEach(Wire::close);}
    }
    public static void verifyManagement(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        long teacherId=teacher.call("GET","/api/auth/me",null,200).path("id").asLong();
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","MANAGE","name","Management","requiredLessons",5),201).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+teacherId,null,200);
        var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Managed lesson","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","QUIZ","title","Quiz","budget",40),Map.of("mode","FILL_BLANK","title","Fill","budget",40)));
        String collection="/api/teaching/classes/"+course+"/lessons";
        var lessons=new ArrayList<Long>();
        for(int i=0;i<5;i++){var input=new HashMap<String,Object>(plan);input.put("requestId",UUID.randomUUID().toString());lessons.add(teacher.call("POST",collection,input,201).path("id").asLong());}
        String deletedPath="/api/teaching/lessons/"+lessons.get(1);
        outsider.call("DELETE",deletedPath,Map.of("revision",0),403);
        teacher.call("DELETE",deletedPath,Map.of("revision",1),409);
        teacher.call("DELETE",deletedPath,Map.of("revision",0),204);
        var list=teacher.call("GET",collection,null,200);assertThat(list).hasSize(4);
        for(int i=0;i<4;i++)assertThat(list.get(i).path("number").asInt()).isEqualTo(i+1);
        assertThat(list.get(1).path("id").asLong()).isEqualTo(lessons.get(2));
        var newPlan=new HashMap<String,Object>(plan);newPlan.put("requestId",UUID.randomUUID().toString());
        long fifth=teacher.call("POST",collection,newPlan,201).path("id").asLong();assertThat(teacher.call("GET",collection,null,200).get(4).path("number").asInt()).isEqualTo(5);
        String thirdPath="/api/teaching/lessons/"+lessons.get(3);long revision=teacher.call("GET",thirdPath,null,200).path("revision").asLong();
        var editPlan=new HashMap<String,Object>(plan);editPlan.put("title","Edited third");
        teacher.call("PUT",thirdPath,Map.of("revision",revision,"plan",editPlan),200);
        assertThat(teacher.call("GET",thirdPath,null,200).path("number").asInt()).isEqualTo(3);
        long bank=teacher.call("POST","/api/quizzes",Map.of("title","Managed Quiz","mode","QUIZ","visibility","PRIVATE","questions",List.of(Map.of("content","Managed question","options",Map.of("A","One","B","Two","C","Three","D","Four"),"correctAnswer","B"))),201).path("id").asLong();
        long fill=teacher.call("POST","/api/quizzes",Map.of("title","Managed Fill","mode","FILL_BLANK","visibility","PRIVATE","questions",List.of(Map.of("content","Managed {{blank}}","acceptedAnswers",List.of("answer","alias"),"matchingPolicy","EXACT"))),201).path("id").asLong();
        var runtime=context.getBean(LessonRuntime.class);var service=context.getBean(LessonService.class);var hostAuth=auth(context,"teacher_test");
        // Complete one source with automatic deadlines and no student answers at all.
        long source=lessons.getFirst();String sourcePath="/api/teaching/lessons/"+source;
        prepareManaged(teacher,sourcePath,bank,fill,1000);
        try(var host=teacher.connect()) {
            long rev=teacher.call("GET",sourcePath,null,200).path("revision").asLong();host.command(source,"START_LESSON",Map.of("revision",rev));
            rev=teacher.call("GET",sourcePath,null,200).path("revision").asLong();host.command(source,"START_LESSON_GAME",Map.of("revision",rev,"position",1));
            runtime.serial(hostAuth,source,()->service.completeGame(hostAuth,source,1,Map.of()));
            for(int position:List.of(2,3)) {
                rev=teacher.call("GET",sourcePath,null,200).path("revision").asLong();int beforeClassEvents=host.classEvents.get();
                host.command(source,"START_LESSON_GAME",Map.of("revision",rev,"position",position));
                var window=awaitQuestion(host,source,position,1).path("payload").path("window");
                host.send(commandOf(source,"CLOSE_LESSON_WINDOW",Map.of("token",window.path("token").asLong())),"ERROR");
                awaitAdvance(host,source,window.path("token").asLong());
                assertThat(host.classEvents.get()).isGreaterThanOrEqualTo(beforeClassEvents+2);
            }
            assertThat(teacher.call("GET",sourcePath,null,200).path("status").asText()).isEqualTo("COMPLETED");
            rev=teacher.call("GET",sourcePath,null,200).path("revision").asLong();
            teacher.call("DELETE",sourcePath,Map.of("revision",rev),409);
            teacher.call("PUT",sourcePath,Map.of("revision",rev,"plan",editPlan),409);
        }
        new Client(port,null).call("POST","/api/auth/register",Map.of("username","manage_student","hoTen","Managed student","maSinhVien","MANAGE_S","password","StudentPass123!"),201);
        var student=new Client(port,"manage_student");long studentId=student.call("GET","/api/auth/me",null,200).path("id").asLong();
        String join=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).path("joinCode").asText();
        student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",join),200);
        teacher.call("POST","/api/teaching/classes/"+course+"/members/"+studentId+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        // Both kinds can be deleted while their timer is armed; their callbacks cannot restore data.
        for(int modePosition:List.of(2,3)) {
            long lesson=modePosition==2?lessons.get(2):lessons.get(3);String path="/api/teaching/lessons/"+lesson;
            prepareManaged(teacher,path,bank,fill,2000);
            try(var host=teacher.connect();var pupil=student.connect()) {
                long rev=teacher.call("GET",path,null,200).path("revision").asLong();host.command(lesson,"START_LESSON",Map.of("revision",rev));
                rev=teacher.call("GET",path,null,200).path("revision").asLong();host.command(lesson,"START_LESSON_GAME",Map.of("revision",rev,"position",1));
                runtime.serial(hostAuth,lesson,()->service.completeGame(hostAuth,lesson,1,Map.of()));
                if(modePosition==3){rev=teacher.call("GET",path,null,200).path("revision").asLong();host.command(lesson,"START_LESSON_GAME",Map.of("revision",rev,"position",2));var first=awaitQuestion(host,lesson,2,1);awaitAdvance(host,lesson,first.path("payload").path("window").path("token").asLong());}
                rev=teacher.call("GET",path,null,200).path("revision").asLong();host.command(lesson,"START_LESSON_GAME",Map.of("revision",rev,"position",modePosition));
                var active=awaitQuestion(host,lesson,modePosition,1).path("payload").path("window");long oldToken=active.path("token").asLong();
                long game=active.path("gameId").asLong();
                pupil.command(lesson,"ATTEND_LESSON",Map.of());pupil.command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",modePosition));
                pupil.command(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",oldToken,"value",modePosition==2?"B":"alias"));
                assertThat(student.call("GET","/api/learning/profile",null,200).path("stats").path("totalPoints").decimalValue()).isEqualByComparingTo("40");
                rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("DELETE",path,Map.of("revision",rev),204);
                host.await(n->n.path("type").asText().equals("LESSON_DELETED"));
                teacher.call("GET",path,null,404);
                pupil.await(n->n.path("type").asText().equals("LESSON_DELETED"));
                assertThat(student.call("GET","/api/learning/profile",null,200).path("stats").path("totalPoints").decimalValue()).isEqualByComparingTo("0");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_score WHERE lesson_id=?",Integer.class,lesson)).isZero();
                host.send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",oldToken,"value","B")),"ERROR");
                Thread.sleep(2200);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_action_window WHERE lesson_id=?",Integer.class,lesson)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_game WHERE lesson_id=?",Integer.class,lesson)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_answer WHERE game_id=?",Integer.class,game)).isZero();
            }
        }
        // Copy by actual subject identity and verify independent snapshots/config, no runtime or results.
        var template=teacher.call("GET","/api/teaching/classes/"+course+"/lesson-templates/"+source,null,200);
        assertThat(template.path("contents")).hasSize(2);
        var copy=new HashMap<String,Object>(plan);copy.put("requestId",UUID.randomUUID().toString());copy.put("sourceLessonId",source);
        long target=teacher.call("POST",collection,copy,201).path("id").asLong();String targetPath="/api/teaching/lessons/"+target;
        var targetView=teacher.call("GET",targetPath,null,200);assertThat(targetView.path("number").asInt()).isEqualTo(4);assertThat(targetView.path("status").asText()).isEqualTo("DRAFT");
        long targetGame=targetView.path("games").get(1).path("id").asLong();
        var originalContent=teacher.call("GET",sourcePath+"/games/2/content",null,200);var targetContent=teacher.call("GET",targetPath+"/games/2/content",null,200);
        assertThat(targetContent.path("questions")).isEqualTo(originalContent.path("questions"));assertThat(targetContent.path("gameId")).isNotEqualTo(originalContent.path("gameId"));
        for(String table:List.of("lesson_action_window","lesson_attendance","lesson_score","lesson_command_receipt"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE lesson_id=?",Integer.class,target)).isZero();
        for(String table:List.of("lesson_quiz_answer","lesson_game_participant","lesson_game_score","lesson_quiz_question"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE game_id=?",Integer.class,targetGame)).isZero();
        teacher.call("PUT",targetPath+"/games/2/configuration",Map.of("revision",targetView.path("revision").asLong(),"questionCount",1,"durationMs",15000,"budget",40),200);
        assertThat(teacher.call("GET",sourcePath,null,200).path("games").get(1).path("durationMs").asInt()).isEqualTo(1000);
        // Assignment copies retain their prompt/budget, but never reuse an old runtime deadline.
        long assignmentBank=teacher.call("POST","/api/quizzes",Map.of("title","Copy assignment","mode","ASSIGNMENT","visibility","PRIVATE","questions",List.of(Map.of("content","Explain the lesson."))),201).path("id").asLong();
        var assignmentPlan=new HashMap<String,Object>(plan);assignmentPlan.put("requestId",UUID.randomUUID().toString());
        assignmentPlan.put("slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","QUIZ","title","Quiz","budget",40),Map.of("mode","ASSIGNMENT","title","Assignment","budget",40)));
        long assignmentSource=teacher.call("POST",collection,assignmentPlan,201).path("id").asLong();String assignmentPath="/api/teaching/lessons/"+assignmentSource;
        for(int position:List.of(2,3)){
            long rev=teacher.call("GET",assignmentPath,null,200).path("revision").asLong();
            teacher.call("POST",assignmentPath+"/games/"+position+"/content",Map.of("quizId",position==2?bank:assignmentBank,"lessonRevision",rev),200);
        }
        long assignmentDeadline=System.currentTimeMillis()+300000;
        long assignmentGame=teacher.call("GET",assignmentPath,null,200).path("games").get(2).path("id").asLong();
        jdbc.update("INSERT INTO lesson_assignment_deadline(game_id,deadline_at_ms) VALUES(?,?)",assignmentGame,assignmentDeadline);
        jdbc.update("UPDATE lesson_game SET duration_ms=1000 WHERE lesson_id=? AND position=2",assignmentSource);
        long sourceRevision=teacher.call("GET",assignmentPath,null,200).path("revision").asLong();teacher.call("PUT",assignmentPath+"/visibility",Map.of("revision",sourceRevision,"visible",true),200);
        try(var host=teacher.connect()){
            sourceRevision=teacher.call("GET",assignmentPath,null,200).path("revision").asLong();host.command(assignmentSource,"START_LESSON",Map.of("revision",sourceRevision));
            for(int position:List.of(1,2,3)){
                sourceRevision=teacher.call("GET",assignmentPath,null,200).path("revision").asLong();host.command(assignmentSource,"START_LESSON_GAME",Map.of("revision",sourceRevision,"position",position));
                if(position==2){var window=awaitQuestion(host,assignmentSource,2,1).path("payload").path("window");awaitAdvance(host,assignmentSource,window.path("token").asLong());}
                else {int slot=position;runtime.serial(hostAuth,assignmentSource,()->service.completeGame(hostAuth,assignmentSource,slot,Map.of()));}
            }
        }
        long sameSubject=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","COPY_SAME","name","Same subject copy","requiredLessons",2),201).path("id").asLong();admin.call("PUT","/api/admin/academic/classes/"+sameSubject+"/teachers/"+teacherId,null,200);
        var assignmentCopy=new HashMap<String,Object>(assignmentPlan);assignmentCopy.put("requestId",UUID.randomUUID().toString());assignmentCopy.put("sourceLessonId",assignmentSource);
        long assignmentTarget=teacher.call("POST","/api/teaching/classes/"+sameSubject+"/lessons",assignmentCopy,201).path("id").asLong();
        long copiedGame=teacher.call("GET","/api/teaching/lessons/"+assignmentTarget,null,200).path("games").get(2).path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_assignment_deadline WHERE game_id=?",Integer.class,copiedGame)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_action_window WHERE lesson_id=?",Integer.class,assignmentTarget)).isZero();
        assertThat(teacher.call("GET","/api/teaching/lessons/"+assignmentTarget+"/games/3/content",null,200).path("questions")).isEqualTo(teacher.call("GET",assignmentPath+"/games/3/content",null,200).path("questions"));
        assertThat(jdbc.queryForObject("SELECT deadline_at_ms FROM lesson_assignment_deadline WHERE game_id=?",Long.class,assignmentGame)).isEqualTo(assignmentDeadline);
        long differentSubject=admin.call("POST","/api/admin/academic/subjects",Map.of("code","COPY_OTHER","name","Other copy subject"),201).path("id").asLong();
        long semester=jdbc.queryForObject("SELECT semester_id FROM subject_offering WHERE id=?",Long.class,offering);
        long otherOffering=admin.call("POST","/api/admin/academic/offerings",Map.of("semesterId",semester,"subjectId",differentSubject),201).path("id").asLong();
        long otherCourse=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",otherOffering,"code","COPY_OTHER","name","Other subject class","requiredLessons",1),201).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+otherCourse+"/teachers/"+teacherId,null,200);
        teacher.call("GET","/api/teaching/classes/"+otherCourse+"/lesson-templates/"+source,null,409);
        teacher.call("POST","/api/teaching/classes/"+otherCourse+"/lessons",copy,409);
        outsider.call("GET","/api/teaching/classes/"+course+"/lesson-templates/"+source,null,403);
        long outsiderId=outsider.call("GET","/api/auth/me",null,200).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+outsiderId,null,200);
        assertThat(outsider.call("GET","/api/teaching/classes/"+course+"/lesson-templates",null,200)).isEmpty();
        outsider.call("GET","/api/teaching/classes/"+course+"/lesson-templates/"+source,null,404);
        admin.call("PUT","/api/admin/academic/classes/"+sameSubject+"/teachers/"+outsiderId,null,200);
        var deniedCopy=new HashMap<String,Object>(copy);deniedCopy.put("requestId",UUID.randomUUID().toString());outsider.call("POST","/api/teaching/classes/"+sameSubject+"/lessons",deniedCopy,404);
    }
    private static void prepareManaged(Client teacher,String path,long quiz,long fill,int duration) throws Exception {
        for(int position:List.of(2,3)) {
            long rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("POST",path+"/games/"+position+"/content",Map.of("quizId",position==2?quiz:fill,"lessonRevision",rev),200);
            rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/games/"+position+"/configuration",Map.of("revision",rev,"questionCount",1,"durationMs",duration,"budget",40),200);
        }
        long rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/games/1/configuration",Map.of("revision",rev,"budget",20,"wheelCells",List.of("Shared challenge"),"selectedCount",1),200);
        rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/visibility",Map.of("revision",rev,"visible",true),200);
    }

    public static void verifySettingsVisibility(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        long offering=jdbc.queryForObject("SELECT id FROM subject_offering ORDER BY id LIMIT 1",Long.class);
        long course=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","SETTINGS","name","Settings visibility","requiredLessons",1),201).path("id").asLong();
        long tid=teacher.call("GET","/api/auth/me",null,200).path("id").asLong();
        admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+tid,null,200);
        String code=teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).path("joinCode").asText();
        var student=new Client(port,"fill_a");long uid=student.call("GET","/api/auth/me",null,200).path("id").asLong();
        student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);
        teacher.call("POST","/api/teaching/classes/"+course+"/members/"+uid+"/approve",Map.of("requestId",UUID.randomUUID().toString(),"revision",0),200);
        long lesson=teacher.call("POST","/api/teaching/classes/"+course+"/lessons",Map.of("requestId",UUID.randomUUID().toString(),"title","Settings lesson","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","QUIZ","title","Quiz","budget",40),Map.of("mode","FILL_BLANK","title","Fill","budget",40))),201).path("id").asLong();
        String path="/api/teaching/lessons/"+lesson,publicPath="/api/student/lessons/"+lesson;
        for(int position:List.of(2,3)){
            String mode=position==2?"QUIZ":"FILL_BLANK";
            Object question=position==2?Map.of("content","Private quiz prompt","options",Map.of("A","One","B","Two","C","Three","D","Four"),"correctAnswer","B"):Map.of("content","Private {{blank}} prompt","acceptedAnswers",List.of("Exact","Alias"),"matchingPolicy","EXACT");
            long bank=teacher.call("POST","/api/quizzes",Map.of("title",mode+" settings","mode",mode,"visibility","PRIVATE","questions",java.util.Collections.nCopies(20,question)),201).path("id").asLong();
            long rev=teacher.call("GET",path,null,200).path("revision").asLong();
            teacher.call("POST",path+"/games/"+position+"/content",Map.of("quizId",bank,"lessonRevision",rev),200);
            rev=teacher.call("GET",path,null,200).path("revision").asLong();
            var config=Map.of("revision",rev,"durationMs",3000,"questionCount",10,"budget",position==2?23.45:17.13);
            student.call("PUT",path+"/games/"+position+"/configuration",config,403);
            outsider.call("PUT",path+"/games/"+position+"/configuration",config,403);
            teacher.call("PUT",path+"/games/"+position+"/configuration",Map.of("revision",rev,"durationMs",0,"questionCount",10,"budget",40),409);
            teacher.call("PUT",path+"/games/"+position+"/configuration",Map.of("revision",rev,"durationMs",3000,"questionCount",10,"budget",100),409);
            for(int invalid:List.of(0,21))teacher.call("PUT",path+"/games/"+position+"/configuration",Map.of("revision",rev,"durationMs",3000,"questionCount",invalid,"budget",40),409);
            teacher.call("PUT",path+"/games/"+position+"/configuration",Map.of("revision",rev,"durationMs",3000,"questionCount",1.5,"budget",40),400);
            teacher.call("PUT",path+"/games/"+position+"/configuration",config,200);
            assertThat(teacher.call("GET","/api/quizzes/"+bank+"/preview",null,200).path("questions").size()).isEqualTo(20);
            var configured=teacher.call("GET",path+"/snapshot",null,200);long gid=configured.path("lesson").path("games").get(position-1).path("id").asLong();
            assertThat(configured.path("questionCounts").path(Long.toString(gid)).asInt()).isEqualTo(10);
            assertThat(jdbc.queryForObject("SELECT question_count FROM lesson_game WHERE id=?",Integer.class,gid)).isEqualTo(10);

            teacher.call("PUT",path+"/games/"+position+"/configuration",config,409);
        }
        long rev=teacher.call("GET",path,null,200).path("revision").asLong();
        teacher.call("PUT",path+"/games/1/configuration",Map.of("revision",rev,"budget",90,"wheelCells",List.of("Shared challenge"),"selectedCount",1),409);
        teacher.call("PUT",path+"/games/1/configuration",Map.of("revision",rev,"budget",20,"wheelCells",List.of("Shared challenge"),"selectedCount",1),200);
        rev=teacher.call("GET",path,null,200).path("revision").asLong();
        teacher.call("PUT",path+"/visibility",Map.of("revision",rev,"visible",true),200);
        try(var host=teacher.connect();var pupil=student.connect()){
            rev=teacher.call("GET",path,null,200).path("revision").asLong();host.command(lesson,"START_LESSON",Map.of("revision",rev));
            pupil.command(lesson,"ATTEND_LESSON",Map.of());
            rev=teacher.call("GET",path,null,200).path("revision").asLong();host.command(lesson,"START_LESSON_GAME",Map.of("revision",rev,"position",1));
            pupil.command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",1));
            teacher.call("POST",path+"/wheel/select",Map.of("requestId",UUID.randomUUID().toString(),"revision",0,"pool",List.of(uid)),200);
            teacher.call("POST",path+"/wheel/spin",Map.of("requestId",UUID.randomUUID().toString(),"revision",1),200);
            rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/games/1/visibility",Map.of("revision",rev,"visible",false),200);
            assertThat(student.call("GET",publicPath+"/snapshot",null,200).path("wheel").isNull()).isTrue();
            teacher.call("POST",path+"/wheel/grade",Map.of("requestId",UUID.randomUUID().toString(),"revision",2,"grades",Map.of(uid,10)),200);
            teacher.call("POST",path+"/wheel/finalize",Map.of("requestId",UUID.randomUUID().toString(),"revision",3),200);
            for(int position:List.of(2,3)){
                rev=teacher.call("GET",path,null,200).path("revision").asLong();host.command(lesson,"START_LESSON_GAME",Map.of("revision",rev,"position",position));
                pupil.command(lesson,"PARTICIPATE_LESSON_GAME",Map.of("position",position));
                var game=teacher.call("GET",path,null,200).path("games").get(position-1);long gameId=game.path("id").asLong();
                rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/games/"+position+"/configuration",Map.of("revision",rev,"durationMs",1000,"questionCount",1,"budget",40),409);
                host.send(commandOf(lesson,"OPEN_LESSON_WINDOW",Map.of("position",position,"questionIndex",11)),"ERROR");
                var opened=awaitQuestion(host,lesson,position,1).path("payload").path("window");
                assertThat(opened.path("deadlineAtMs").asLong()-opened.path("openedAtMs").asLong()).isNotZero();
                long token=opened.path("token").asLong();
                assertThat(jdbc.queryForObject("SELECT deadline_at_ms-opened_at_ms FROM lesson_action_window WHERE lesson_id=?",Long.class,lesson)).isEqualTo(3000);
                rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/games/"+position+"/visibility",Map.of("revision",rev,"visible",false),200);
                var hidden=student.call("GET",publicPath+"/snapshot",null,200);assertThat(hidden.path("window").isNull()).isTrue();assertThat(hidden.path("questionCounts").has(Long.toString(gameId))).isFalse();
                student.call("GET",publicPath+"/games/"+position+"/snapshot",null,404);
                student.call("POST",publicPath+"/games/"+position+"/participate",null,404);
                pupil.send(commandOf(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value","B")),"ERROR");
                assertThat(pupil.command(lesson,"RECONNECT_LESSON",Map.of()).toString()).doesNotContain("Private quiz prompt","Private {{blank}} prompt");
                rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/visibility",Map.of("revision",rev,"visible",false),200);
                student.call("GET",publicPath+"/snapshot",null,404);pupil.send(commandOf(lesson,"RECONNECT_LESSON",Map.of()),"ERROR");
                assertThat(teacher.call("GET",path+"/snapshot",null,200).path("window").path("token").asLong()).isEqualTo(token);
                rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/visibility",Map.of("revision",rev,"visible",true),200);
                rev=teacher.call("GET",path,null,200).path("revision").asLong();teacher.call("PUT",path+"/games/"+position+"/visibility",Map.of("revision",rev,"visible",true),200);
                pupil.command(lesson,"RECONNECT_LESSON",Map.of());
                pupil.command(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",token,"value",position==2?"B":"Alias"));
                awaitAdvance(host,lesson,token);
                for(int q=2;q<=10;q++){
                    var next=awaitQuestion(host,lesson,position,q).path("payload").path("window");
                    assertThat(next.path("questionCount").asInt()).isEqualTo(10);
                    // Quiz: seven right, two wrong, one unanswered. Fill: three right, six wrong, one unanswered.
                    if(q<10)pupil.command(lesson,"SUBMIT_LESSON_ACTION",Map.of("token",next.path("token").asLong(),"value",position==2?(q<=7?"B":"A"):(q<=3?"Alias":"wrong")));
                    awaitAdvance(host,lesson,next.path("token").asLong());
                }
                assertThat(student.call("GET",publicPath+"/snapshot",null,200).path("lesson").path("games").findValues("score").get(position-2).decimalValue()).isEqualByComparingTo(position==2?"16.42":"5.14");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_question WHERE game_id=?",Integer.class,gameId)).isEqualTo(10);

            }
            assertThat(teacher.call("GET",path,null,200).path("status").asText()).isEqualTo("COMPLETED");
            assertThat(student.call("GET",publicPath,null,200).path("score").decimalValue()).isEqualByComparingTo("31.56");
        }
    }

}
