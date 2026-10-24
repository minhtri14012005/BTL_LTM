package vn.edu.multigame.system.controller;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Full HTTP/WS lifecycle on the owned integration schema; no production fixtures. */
final class ManagementLifecycleChecks {
 private static final ObjectMapper JSON=new ObjectMapper();
 private static final String PASSWORD="StudentPass123!",NEW_PASSWORD="ReplacementPass456!";
 private static class Client {
  final int port;final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
  Client(int port){this.port=port;}
  Client login(String name,String password) throws Exception{call("POST","/api/auth/login",Map.of("username",name,"password",password),200);return this;}
  JsonNode call(String method,String path,Object body,int expected) throws Exception {
   var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30));
   if(!method.equals("GET")){var csrf=call("GET","/api/auth/csrf",null,200);request.header(csrf.path("headerName").asText(),csrf.path("token").asText());}
   var response=http.send(request.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
   assertThat(response.statusCode()).as(method+" "+path+": "+response.body()).isEqualTo(expected);
   return response.body().isEmpty()?JSON.nullNode():JSON.readTree(response.body());
  }
 }
 private static class Wire implements WebSocket.Listener,AutoCloseable {
  final CompletableFuture<Integer> closed=new CompletableFuture<>();final BlockingQueue<String> events=new LinkedBlockingQueue<>();final StringBuilder part=new StringBuilder();final WebSocket ws;
  Wire(Client client)throws Exception{ws=client.http.newWebSocketBuilder().header("Origin","http://localhost:8080").buildAsync(URI.create("ws://127.0.0.1:"+client.port+"/ws"),this).get(10,TimeUnit.SECONDS);await("AUTH_READY");}
  public void onOpen(WebSocket socket){socket.request(1);}
  public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){part.append(data);if(last){try{var frame=JSON.readTree(part.toString());events.add(frame.path("type").asText());if(frame.has("code"))events.add(frame.path("code").asText());part.setLength(0);}catch(Exception e){closed.completeExceptionally(e);}}socket.request(1);return null;}
  public CompletionStage<?> onClose(WebSocket socket,int code,String reason){closed.complete(code);return null;}
  public void onError(WebSocket socket,Throwable error){closed.completeExceptionally(error);}
  void await(String type)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<end){if(type.equals(events.poll(100,TimeUnit.MILLISECONDS)))return;}throw new AssertionError("Missing WS event "+type);}
  public void close(){ws.abort();}
 }
 private static Map<String,Object> decision(long revision){return Map.of("requestId",UUID.randomUUID().toString(),"revision",revision);}
 private static long version(JdbcTemplate jdbc,long user){return jdbc.queryForObject("SELECT ban_version FROM app_user WHERE id=?",Long.class,user);}
 private static Map<String,Object> row(String name,int from,Integer to){var map=new HashMap<String,Object>();map.put("name",name);map.put("from",from);map.put("to",to);return map;}
 private static long teacher(Client admin,String name)throws Exception{return admin.call("POST","/api/admin/teachers",Map.of("username",name,"hoTen",name,"password",PASSWORD),201).path("id").asLong();}
 private static Client student(int port,String name)throws Exception{var client=new Client(port);client.call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password",PASSWORD),201);return client.login(name,PASSWORD);}
 private static void join(Client student,String code)throws Exception{student.call("POST","/api/student/classes/join",Map.of("requestId",UUID.randomUUID().toString(),"code",code),200);}
 private static void sanction(Client admin,JdbcTemplate jdbc,long user,String action)throws Exception{admin.call("POST","/api/admin/users/"+user+"/"+action,Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",version(jdbc,user),"reason","Owned management integration fixture"),200);}
 static void verify(JdbcTemplate jdbc,int port)throws Exception {
  var admin=new Client(port).login("admin_test",PASSWORD);
  long original=teacher(admin,"management_teacher"),replacement=teacher(admin,"management_replacement"),outsider=teacher(admin,"management_outsider");
  var beforeLogin=new Client(port).login("management_teacher",PASSWORD);
  admin.call("PUT","/api/admin/teachers/"+original,Map.of("expectedVersion",0,"username","management_replacement","hoTen","Duplicate"),409);
  var edited=admin.call("PUT","/api/admin/teachers/"+original,Map.of("expectedVersion",0,"username","management_renamed","hoTen","Updated teacher"),200);
  assertThat(edited.path("id").asLong()).isEqualTo(original);
  assertThat(jdbc.queryForObject("SELECT display_name FROM app_user WHERE id=?",String.class,original)).isEqualTo("Updated teacher");
  beforeLogin.call("GET","/api/auth/me",null,401);
  var beforePassword=new Client(port).login("management_renamed",PASSWORD);
  try(var wire=new Wire(beforePassword)) {
   admin.call("POST","/api/admin/teachers/"+original+"/password",Map.of("expectedVersion",version(jdbc,original),"password",NEW_PASSWORD),200);
   assertThat(wire.closed.get(10,TimeUnit.SECONDS)).isEqualTo(4001);
  }
  beforePassword.call("GET","/api/auth/me",null,401);
  new Client(port).call("POST","/api/auth/login",Map.of("username","management_renamed","password",PASSWORD),401);
  var teacher=new Client(port).login("management_renamed",NEW_PASSWORD);
  var other=new Client(port).login("management_outsider",PASSWORD);
  long offering=jdbc.queryForObject("SELECT MIN(id) FROM subject_offering",Long.class);
  long first=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","MANAGE1","name","Management first","requiredLessons",3),201).path("id").asLong();
  long second=admin.call("POST","/api/admin/academic/classes",Map.of("offeringId",offering,"code","MANAGE2","name","Management second","requiredLessons",3),201).path("id").asLong();
  for(long course:List.of(first,second))admin.call("PUT","/api/admin/academic/classes/"+course+"/teachers/"+original,null,200);
  admin.call("PUT","/api/admin/academic/classes/"+second+"/teachers/"+outsider,null,200);
  String room1=teacher.call("GET","/api/teaching/classes/"+first+"/room",null,200).path("joinCode").asText();
  String room2=teacher.call("GET","/api/teaching/classes/"+second+"/room",null,200).path("joinCode").asText();
  var s1=student(port,"management_s1");var s2=student(port,"management_s2");var s3=student(port,"management_s3");
  long sid=s1.call("GET","/api/auth/me",null,200).path("id").asLong(),blocked=s3.call("GET","/api/auth/me",null,200).path("id").asLong();
  for(var student:List.of(s1,s2,s3))join(student,room1);join(s1,room2);
  s1.call("PUT","/api/admin/teachers/"+original,Map.of("expectedVersion",version(jdbc,original),"username","hacked","hoTen","Hacked"),403);
  sanction(admin,jdbc,blocked,"expel");
  String bulk="/api/teaching/classes/"+first+"/members/approve-all";var request=Map.of("requestId",UUID.randomUUID().toString());
  other.call("POST",bulk,request,403);s1.call("POST",bulk,request,403);
  var teacherAgain=new Client(port).login("management_renamed",NEW_PASSWORD);
  try(var executor=Executors.newFixedThreadPool(2)){
   var a=executor.submit(()->teacher.call("POST",bulk,request,200));var b=executor.submit(()->teacherAgain.call("POST",bulk,request,200));
   var result=a.get(30,TimeUnit.SECONDS);assertThat(b.get(30,TimeUnit.SECONDS)).isEqualTo(result);
   assertThat(result.path("approved").asInt()).isEqualTo(2);assertThat(result.path("invalid").asInt()).isEqualTo(1);
   assertThat(result.path("skipped").get(0).path("reason").asText()).isEqualTo("ACCOUNT_UNAVAILABLE");
  }
  teacher.call("POST","/api/teaching/classes/"+second+"/members/approve-all",request,409);
  assertThat(jdbc.queryForObject("SELECT status FROM class_membership m JOIN class_room r ON r.id=m.class_room_id WHERE r.course_class_id=? AND student_id=?",String.class,second,sid)).isEqualTo("PENDING");
  String member="/api/teaching/classes/"+first+"/members/"+sid;
  teacher.call("POST",member+"/remove",decision(1),200);
  other.call("POST","/api/teaching/classes/"+second+"/members/"+sid+"/restore",decision(1),403);
  sanction(admin,jdbc,sid,"expel");
  assertThat(teacher.call("POST",member+"/restore",decision(2),409).path("code").asText()).isEqualTo("STUDENT_ACCOUNT_UNAVAILABLE");
  teacher.call("GET","/api/auth/me",null,200);
  sanction(admin,jdbc,sid,"restore");
  var restore=decision(2);teacher.call("POST",member+"/restore",restore,200);teacher.call("POST",member+"/restore",restore,200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subject_year_exclusion WHERE student_id=?",Integer.class,sid)).isZero();
  assertThat(jdbc.queryForObject("SELECT status FROM class_membership m JOIN class_room r ON r.id=m.class_room_id WHERE r.course_class_id=? AND student_id=?",String.class,second,sid)).isEqualTo("PENDING");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_membership WHERE student_id=?",Integer.class,sid)).isEqualTo(2);
  s1=new Client(port).login("management_s1",PASSWORD);
  String ranks="/api/teaching/classes/"+first+"/rank-config";
  var saved=teacher.call("PUT",ranks,Map.of("revision",0,"ranges",List.of(row("Gold",1,10),row("Iron",11,null))),200);
  assertThat(saved.path("ranges").get(1).path("to").isNull()).isTrue();
  teacher.call("PUT",ranks,Map.of("revision",1,"ranges",List.of(row("Iron",1,null),row("Other",2,3))),409);
  teacher.call("PUT",ranks,Map.of("revision",1,"ranges",List.of(Map.of("name","Invalid fractional rank","from",1,"to",10.5))),400);
  String shared=teacher.call("POST",ranks+"/share",Map.of("revision",1),200).path("code").asText();
  assertThat(teacher.call("POST","/api/teaching/classes/"+second+"/rank-config/import",Map.of("revision",0,"code",shared),200).path("ranges")).isEqualTo(saved.path("ranges"));
  long subject=jdbc.queryForObject("SELECT subject_id FROM subject_offering WHERE id=?",Long.class,offering);
  var bank=teacher.call("POST","/api/quizzes",Map.of("title","Teacher retained bank","mode","QUIZ","visibility","PRIVATE","subjectId",subject,"questions",List.of(Map.of("content","Retained question","options",Map.of("A","One","B","Two","C","Three","D","Four"),"correctAnswer","A"))),201);
  var plan=Map.of("requestId",UUID.randomUUID().toString(),"title","Retained management lesson","slots",List.of(Map.of("mode","LUCKY_WHEEL","title","Wheel","budget",20),Map.of("mode","QUIZ","title","Quiz","budget",40),Map.of("mode","FILL_BLANK","title","Fill","budget",40)));
  var lesson=teacher.call("POST","/api/teaching/classes/"+first+"/lessons",plan,201);long lessonId=lesson.path("id").asLong();
  String contentPath="/api/teaching/lessons/"+lessonId+"/games/2/content";
  var content=teacher.call("POST",contentPath,Map.of("quizId",bank.path("id").asLong(),"lessonRevision",lesson.path("revision").asLong()),200);
  lesson=teacher.call("GET","/api/teaching/lessons/"+lessonId,null,200);
  teacher.call("PUT","/api/teaching/lessons/"+lessonId+"/visibility",Map.of("revision",lesson.path("revision").asLong(),"visible",true),200);
  String lessonPath="/api/teaching/lessons/"+lessonId;
  long revision=teacher.call("GET",lessonPath,null,200).path("revision").asLong();
  teacher.call("PUT",lessonPath+"/games/1/configuration",Map.of("revision",revision,"budget",20,"durationMs",1000,"wheelCells",List.of("Explain TCP"),"selectedCount",1),200);
  revision=teacher.call("GET",lessonPath,null,200).path("revision").asLong();
  teacher.call("POST",lessonPath+"/start",Map.of("revision",revision),200);
  s1.call("POST","/api/student/lessons/"+lessonId+"/attend",null,200);
  revision=teacher.call("GET",lessonPath,null,200).path("revision").asLong();
  teacher.call("POST",lessonPath+"/games/1/start",Map.of("revision",revision),200);
  var unchanged=jdbc.queryForList("SELECT * FROM lesson_game WHERE lesson_id=? ORDER BY position",lessonId);
  var members=jdbc.queryForList("SELECT m.* FROM class_membership m JOIN class_room r ON r.id=m.class_room_id WHERE r.course_class_id=? ORDER BY m.id",first);
  var activeTeacher=new Client(port).login("management_renamed",NEW_PASSWORD);
  try(var teacherWire=new Wire(activeTeacher);var studentWire=new Wire(s1)){
   admin.call("POST","/api/admin/teachers/"+original+"/delete",Map.of("expectedVersion",version(jdbc,original)),200);
   assertThat(teacherWire.closed.get(10,TimeUnit.SECONDS)).isEqualTo(4001);studentWire.await("CLASS_STAFFING_UPDATED");
   assertThat(s1.call("GET","/api/student/classes/"+first+"/room",null,200).path("hasTeacher").asBoolean()).isFalse();
   s1.call("GET","/api/student/classes/"+first+"/lessons",null,200);
   assertThat(s1.call("POST","/api/student/lessons/"+lessonId+"/games/1/participate",null,409).path("code").asText()).isEqualTo("CLASS_AWAITING_TEACHER");
   studentWire.ws.sendText(JSON.writeValueAsString(Map.of("v",1,"kind","COMMAND","type","PARTICIPATE_LESSON_GAME","requestId",UUID.randomUUID().toString(),"target",Map.of("kind","LESSON","id",lessonId),"payload",Map.of("position",1))),true).join();
   studentWire.await("CLASS_AWAITING_TEACHER");
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM teaching_assignment WHERE teacher_id=?",Integer.class,original)).isZero();
   assertThat(jdbc.queryForList("SELECT * FROM lesson_game WHERE lesson_id=? ORDER BY position",lessonId)).isEqualTo(unchanged);
   assertThat(jdbc.queryForList("SELECT m.* FROM class_membership m JOIN class_room r ON r.id=m.class_room_id WHERE r.course_class_id=? ORDER BY m.id",first)).isEqualTo(members);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE id=? AND deleted_at_ms IS NULL",Integer.class,bank.path("id").asLong())).isEqualTo(1);
   assertThat(admin.call("GET","/api/admin/users?role=GIANG_VIEN&q=management_renamed",null,200).path("items")).isEmpty();
   new Client(port).call("POST","/api/auth/login",Map.of("username","management_renamed","password",NEW_PASSWORD),401);
   activeTeacher.call("GET","/api/teaching/classes/"+first,null,401);
   admin.call("PUT","/api/admin/academic/classes/"+first+"/teachers/"+original,null,409);
   admin.call("GET","/api/auth/me",null,200);
   admin.call("PUT","/api/admin/academic/classes/"+first+"/teachers/"+replacement,null,200);studentWire.await("CLASS_STAFFING_UPDATED");
   assertThat(s1.call("GET","/api/student/classes/"+first+"/room",null,200).path("hasTeacher").asBoolean()).isTrue();
  }
  admin.call("PUT","/api/admin/academic/classes/"+first+"/teachers/"+replacement,null,200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM teaching_assignment WHERE course_class_id=? AND teacher_id=?",Integer.class,first,replacement)).isEqualTo(1);
  var next=new Client(port).login("management_replacement",PASSWORD);
  next.call("GET","/api/teaching/lessons/"+lessonId,null,200);
  assertThat(next.call("GET",contentPath,null,200)).isEqualTo(content);
  assertThat(next.call("GET","/api/teaching/classes/"+first+"/room",null,200).path("joinCode").asText()).isEqualTo(room1);
  assertThat(next.call("GET",ranks,null,200).path("ranges")).isEqualTo(saved.path("ranges"));
  System.out.println("Management HTTP/WS: teacher edit/reset/delete, handover, scoped restore, bulk race/replay, unlimited Rank/share PASS");
 }
}
