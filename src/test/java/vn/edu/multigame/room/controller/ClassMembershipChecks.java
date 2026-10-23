package vn.edu.multigame.room.controller;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP, concurrency and persistence checks inside the owned baseline schema. */
public class ClassMembershipChecks {
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
            if(csrf) {
                var token=JSON.readTree(send("GET","/api/auth/csrf",null,false).body());
                request.header(token.get("headerName").asText(),token.get("token").asText());
            }
            return http.send(request.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():
                    HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode call(String method,String path,Object body,int status) throws Exception {
            var response=send(method,path,body,!method.equals("GET"));
            assertThat(response.statusCode()).as(method+" "+path+": "+response.body()).isEqualTo(status);
            return JSON.readTree(response.body());
        }
        JsonNode join(String code,Map<String,Object> input,int status) throws Exception {return call("POST","/api/student/classes/join",input,status);}
        JsonNode decide(long course,long student,String action,Map<String,Object> body,int status) throws Exception {
            return call("POST","/api/teaching/classes/"+course+"/members/"+student+"/"+action,body,status);
        }
    }
    private static Map<String,Object> join(String code) {return Map.of("requestId",UUID.randomUUID().toString(),"code",code);}
    private static Map<String,Object> decision(long revision) {return Map.of("requestId",UUID.randomUUID().toString(),"revision",revision);}
    private static long create(Client admin,String resource,Object input) throws Exception {return admin.call("POST","/api/admin/academic/"+resource,input,201).get("id").asLong();}
    private static String code(Client teacher,long course) throws Exception {return teacher.call("GET","/api/teaching/classes/"+course+"/room",null,200).get("joinCode").asText();}
    private static Client register(int port,String name) throws Exception {
        new Client(port,null).call("POST","/api/auth/register",Map.of("username",name,"hoTen",name,"maSinhVien",name,"password","StudentPass123!"),201);
        return new Client(port,name);
    }
    public static void verify(JdbcTemplate jdbc,int port) throws Exception {
        var admin=new Client(port,"admin_test");var teacher=new Client(port,"teacher_test");var outsider=new Client(port,"teacher_second");
        var student=new Client(port,"student_test");var studentAgain=new Client(port,"student_test");
        long uid=student.call("GET","/api/auth/me",null,200).get("id").asLong();
        long tid=teacher.call("GET","/api/auth/me",null,200).get("id").asLong();
        long first=jdbc.queryForObject("SELECT id FROM course_class WHERE name='LTM 01'",Long.class);
        long second=jdbc.queryForObject("SELECT id FROM course_class WHERE name='LTM 02'",Long.class);
        long nextYear=jdbc.queryForObject("SELECT id FROM course_class WHERE required_lessons=10",Long.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_room",Integer.class))
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM course_class",Integer.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_room r JOIN course_class c ON c.id=r.course_class_id WHERE c.code='UPGRADE'",Integer.class)).isEqualTo(1);
        String firstCode=code(teacher,first),secondCode=code(teacher,second);
        assertThat(firstCode).hasSize(32).isNotEqualTo(secondCode);
        outsider.call("GET","/api/teaching/classes/"+first+"/room",null,403);
        outsider.call("GET","/api/teaching/classes/"+first+"/members",null,403);
        teacher.call("POST","/api/student/classes/join",join(firstCode),403);
        student.call("GET","/api/teaching/classes/"+first+"/members",null,403);
        student.call("POST","/api/student/classes/join",Map.of("code",firstCode),400);
        student.call("POST","/api/student/classes/join",join("F".repeat(32)),404);
        assertThat(student.send("POST","/api/student/classes/join",join(firstCode),false).statusCode()).isEqualTo(403);
        var request=join(firstCode);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->student.join(firstCode,request,200));
            var b=executor.submit(()->studentAgain.join(firstCode,request,200));
            assertThat(a.get(10,TimeUnit.SECONDS).get("status").asText()).isEqualTo("PENDING");
            assertThat(b.get(10,TimeUnit.SECONDS).get("status").asText()).isEqualTo("PENDING");
        }
        student.call("GET","/api/student/classes/"+first+"/room",null,403);
        outsider.decide(first,uid,"approve",decision(0),403);
        var approval=decision(0);
        teacher.decide(first,uid,"approve",approval,200);
        assertThat(teacher.decide(first,uid,"approve",approval,200).get("revision").asLong()).isEqualTo(1);
        assertThat(student.join(firstCode,join(firstCode),200).get("status").asText()).isEqualTo("APPROVED");
        // A fresh login restores the exact same room and does not require another approval.
        var reconnected=new Client(port,"student_test");
        assertThat(reconnected.call("GET","/api/student/classes/"+first+"/room",null,200).get("joinCode").asText()).isEqualTo(firstCode);
        student.join(secondCode,join(secondCode),200);
        outsider.decide(first,uid,"remove",decision(1),403);
        teacher.decide(first,uid,"remove",decision(0),409);
        var removal=decision(1);
        assertThat(teacher.decide(first,uid,"remove",removal,200).get("status").asText()).isEqualTo("REMOVED");
        teacher.decide(first,uid,"remove",removal,200);
        assertThat(teacher.decide(first,uid,"approve",approval,200).get("status").asText()).isEqualTo("REMOVED");
        student.join(firstCode,request,403);student.join(secondCode,join(secondCode),403);
        student.call("GET","/api/student/classes/"+first+"/room",null,403);
        teacher.decide(second,uid,"approve",decision(0),403);
        long subject=jdbc.queryForObject("SELECT id FROM academic_subject WHERE code='LTM'",Long.class);
        long year=jdbc.queryForObject("SELECT id FROM academic_year WHERE code='2026-2027'",Long.class);
        long semester=create(admin,"semesters",Map.of("academicYearId",year,"code","HK2","name","Second semester"));
        long offering=create(admin,"offerings",Map.of("semesterId",semester,"subjectId",subject));
        long otherSemester=create(admin,"classes",Map.of("offeringId",offering,"code","01","name","Same year other semester","requiredLessons",3));
        admin.call("PUT","/api/admin/academic/classes/"+otherSemester+"/teachers/"+tid,null,200);
        student.join(code(teacher,otherSemester),join(code(teacher,otherSemester)),403);
        long differentSubject=create(admin,"subjects",Map.of("code","OTHER_SUBJECT","name","Different subject"));
        long otherOffering=create(admin,"offerings",Map.of("semesterId",semester,"subjectId",differentSubject));
        long otherClass=create(admin,"classes",Map.of("offeringId",otherOffering,"code","01","name","Other subject class","requiredLessons",4));
        admin.call("PUT","/api/admin/academic/classes/"+otherClass+"/teachers/"+tid,null,200);
        admin.call("PUT","/api/admin/academic/classes/"+nextYear+"/teachers/"+tid,null,200);
        for(long allowed:new long[]{otherClass,nextYear}) {
            student.join(code(teacher,allowed),join(code(teacher,allowed)),200);
            teacher.decide(allowed,uid,"approve",decision(0),200);
            student.call("GET","/api/student/classes/"+allowed+"/room",null,200);
        }
        // Global account restore must not undo the independent course/year exclusion.
        admin.call("POST","/api/admin/users/"+uid+"/expel",Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",2,"reason","Test"),200);
        admin.call("POST","/api/admin/users/"+uid+"/restore",Map.of("requestId",UUID.randomUUID().toString(),"expectedVersion",3,"reason","Test"),200);
        var restoredStudent=new Client(port,"student_test");restoredStudent.join(secondCode,join(secondCode),403);
        var rejected=register(port,"membership_reject");long rejectedId=rejected.call("GET","/api/auth/me",null,200).get("id").asLong();
        rejected.join(firstCode,join(firstCode),200);teacher.decide(first,rejectedId,"reject",decision(0),200);
        assertThat(rejected.join(firstCode,join(firstCode),200).get("status").asText()).isEqualTo("REJECTED");
        teacher.decide(first,rejectedId,"approve",decision(1),409);
        // Distinct concurrent approvals: exactly one review advances revision 0.
        var race=register(port,"membership_race");long raceId=race.call("GET","/api/auth/me",null,200).get("id").asLong();
        race.join(firstCode,join(firstCode),200);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var teacherAgain=new Client(port,"teacher_test");
            var a=executor.submit(()->teacher.send("POST","/api/teaching/classes/"+first+"/members/"+raceId+"/approve",decision(0),true).statusCode());
            var b=executor.submit(()->teacherAgain.send("POST","/api/teaching/classes/"+first+"/members/"+raceId+"/approve",decision(0),true).statusCode());
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->race.send("POST","/api/student/classes/join",join(secondCode),true).statusCode());
            var b=executor.submit(()->teacher.decide(first,raceId,"remove",decision(1),200));
            assertThat(a.get(10,TimeUnit.SECONDS)).isIn(200,403);b.get(10,TimeUnit.SECONDS);
        }
        race.join(secondCode,join(secondCode),403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_membership WHERE student_id=? AND status IN ('PENDING','APPROVED')",Integer.class,raceId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subject_year_exclusion WHERE student_id=?",Integer.class,uid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_membership m JOIN class_room r ON r.id=m.class_room_id WHERE m.student_id=? AND r.course_class_id IN (?,?) AND m.status='REMOVED'",Integer.class,uid,first,second)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE title='Retained history'",Integer.class)).isEqualTo(1);
        assertThat(code(teacher,first)).isEqualTo(firstCode);
        // Membership grants no game participation/score rows.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_session",Integer.class)).isZero();
    }
}
