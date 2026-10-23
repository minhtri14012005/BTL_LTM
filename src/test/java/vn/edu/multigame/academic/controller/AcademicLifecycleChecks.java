package vn.edu.multigame.academic.controller;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Runs inside BaselineMySqlIT's owned schema, including real HTTP authorization. */
public class AcademicLifecycleChecks {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String ROOT="/api/admin/academic";
    private static class Client {
        final int port;
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        Client(int port,String username) throws Exception {
            this.port=port;
            if(username!=null) call("POST","/api/auth/login",Map.of("username",username,"password","StudentPass123!"),200);
        }
        HttpResponse<String> send(String method,String path,Object body,boolean csrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
            if(csrf) {
                var token=JSON.readTree(send("GET","/api/auth/csrf",null,false).body());
                request.header(token.get("headerName").asText(),token.get("token").asText());
            }
            return http.send(request.header("Content-Type","application/json").method(method,body==null?
                    HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode call(String method,String path,Object body,int status) throws Exception {
            var response=send(method,path,body,!method.equals("GET"));
            assertThat(response.statusCode()).as(method+" "+path+": "+response.body()).isEqualTo(status);
            return JSON.readTree(response.body());
        }
        long create(String path,Object body) throws Exception {return call("POST",ROOT+path,body,201).get("id").asLong();}
    }
    public static void verify(ConfigurableApplicationContext context,JdbcTemplate jdbc,int port) throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM academic_year",Integer.class)).isEqualTo(1);
        var admin=new Client(port,"admin_test");
        var teacher=new Client(port,"teacher_test");
        var student=new Client(port,"student_test");
        new Client(port,null).call("GET",ROOT+"/years",null,401);
        teacher.call("GET",ROOT+"/classes",null,403);
        student.call("POST",ROOT+"/years",Map.of("code","BAD","name","Denied"),403);
        assertThat(admin.send("POST",ROOT+"/years",Map.of("code","BAD","name","Denied"),false).statusCode()).isEqualTo(403);
        long year=admin.create("/years",Map.of("code","2026-2027","name","Năm học 2026–2027"));
        long nextYear=admin.create("/years",Map.of("code","2027-2028","name","Năm học 2027–2028"));
        admin.call("POST",ROOT+"/years",Map.of("code","2026-2027","name","Duplicate"),409);
        admin.call("POST",ROOT+"/years",Map.of("code","INVALID CODE","name","Invalid"),400);
        long semester=admin.create("/semesters",Map.of("academicYearId",year,"code","HK1","name","Học kỳ 1"));
        long semester2=admin.create("/semesters",Map.of("academicYearId",nextYear,"code","HK1","name","Học kỳ 1"));
        admin.call("POST",ROOT+"/semesters",Map.of("academicYearId",year,"code","hk1","name","Duplicate"),409);
        admin.call("POST",ROOT+"/semesters",Map.of("academicYearId",999999,"code","HK1","name","Missing"),404);
        long subject=admin.create("/subjects",Map.of("code","LTM","name","Lập trình mạng"));
        admin.call("POST",ROOT+"/subjects",Map.of("code","ltm","name","Duplicate"),409);
        admin.call("POST",ROOT+"/subjects",Map.of("code","X","name","  "),400);
        long offering=admin.create("/offerings",Map.of("semesterId",semester,"subjectId",subject));
        long offering2=admin.create("/offerings",Map.of("semesterId",semester2,"subjectId",subject));
        admin.call("POST",ROOT+"/offerings",Map.of("semesterId",semester,"subjectId",subject),409);
        admin.call("POST",ROOT+"/offerings",Map.of("semesterId",semester,"subjectId",999999),404);
        long first=admin.create("/classes",Map.of("offeringId",offering,"code","01","name","LTM 01","requiredLessons",12));
        long second=admin.create("/classes",Map.of("offeringId",offering,"code","02","name","LTM 02","requiredLessons",8));
        long other=admin.create("/classes",Map.of("offeringId",offering2,"code","01","name","LTM năm sau","requiredLessons",10));
        admin.call("POST",ROOT+"/classes",Map.of("offeringId",offering,"code","01","name","Duplicate","requiredLessons",10),409);
        for(int invalid:new int[]{0,-1}) admin.call("POST",ROOT+"/classes",Map.of("offeringId",offering,"code","BAD","name","Invalid","requiredLessons",invalid),400);
        admin.call("POST",ROOT+"/classes",Map.of("offeringId",999999,"code","BAD","name","Missing","requiredLessons",5),404);
        assertThat(teacher.call("GET","/api/teaching/classes",null,200)).isEmpty();
        long teacherId=teacher.call("GET","/api/auth/me",null,200).get("id").asLong();
        long studentId=student.call("GET","/api/auth/me",null,200).get("id").asLong();
        admin.call("PUT",ROOT+"/classes/"+first+"/teachers/"+studentId,null,409);
        admin.call("PUT",ROOT+"/classes/"+first+"/teachers/999999",null,404);
        admin.call("PUT",ROOT+"/classes/999999/teachers/"+teacherId,null,404);
        teacher.call("PUT",ROOT+"/classes/"+other+"/teachers/"+teacherId,null,403);
        // Two simultaneous retries produce one persisted assignment and both return successfully.
        try(var executor=Executors.newFixedThreadPool(2)) {
            var admin2=new Client(port,"admin_test");
            var a=executor.submit(()->admin.call("PUT",ROOT+"/classes/"+first+"/teachers/"+teacherId,null,200));
            var b=executor.submit(()->admin2.call("PUT",ROOT+"/classes/"+first+"/teachers/"+teacherId,null,200));
            a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
        }
        admin.call("PUT",ROOT+"/classes/"+second+"/teachers/"+teacherId,null,200);
        assertThat(teacher.call("GET","/api/teaching/classes",null,200)).hasSize(2);
        var view=teacher.call("GET","/api/teaching/classes/"+first,null,200);
        assertThat(view.get("requiredLessons").asInt()).isEqualTo(12);
        assertThat(view.get("academicYearId").asLong()).isEqualTo(year);
        assertThat(view.get("subjectId").asLong()).isEqualTo(subject);
        teacher.call("GET","/api/teaching/classes/"+other,null,403);
        student.call("GET","/api/teaching/classes",null,403);
        student.call("GET","/api/teaching/classes/"+first,null,403);
        admin.call("POST","/api/admin/teachers",Map.of("username","teacher_second","hoTen","Giảng viên khác","password","StudentPass123!"),201);
        var teacher2=new Client(port,"teacher_second");
        assertThat(teacher2.call("GET","/api/teaching/classes",null,200)).isEmpty();
        teacher2.call("GET","/api/teaching/classes/"+first,null,403);
        for(String resource:List.of("years","semesters","subjects","offerings","classes"))
            assertThat(admin.call("GET",ROOT+"/"+resource,null,200).size()).isGreaterThan(0);
        assertThat(admin.call("GET",ROOT+"/classes",null,200)).hasSize(4);
        // Direct MySQL checks protect the same invariants outside the HTTP validation layer.
        assertThatThrownBy(()->jdbc.update("UPDATE course_class SET required_lessons=0 WHERE id=?",first))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .satisfies(failure->assertThat(((java.sql.SQLException)((org.springframework.dao.DataAccessException)failure).getMostSpecificCause()).getErrorCode()).isEqualTo(3819));
        assertThatThrownBy(()->jdbc.update("INSERT INTO subject_offering(semester_id,subject_id) VALUES(?,999999)",semester))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO teaching_assignment(course_class_id,teacher_id) VALUES(?,?)",first,teacherId))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM teaching_assignment WHERE teacher_id=?",Integer.class,teacherId)).isEqualTo(2);
        // A fresh JPA read after the requests sees the committed lesson count.
        assertThat(context.getBean(vn.edu.multigame.academic.repository.CourseClassRepository.class).findById(second).orElseThrow().getRequiredLessons()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE title='Retained history'",Integer.class)).isEqualTo(1);
        verifyAdminMetadata(admin,teacher,student,year,semester,subject);
    }
    private static void verifyAdminMetadata(Client admin,Client teacher,Client student,long usedYear,long usedSemester,long usedSubject) throws Exception {
        long year=admin.create("/years",Map.of("code","ADMIN_CRUD","name","Temporary year"));
        long subject=admin.create("/subjects",Map.of("code","ADMIN_CRUD","name","Temporary subject"));
        long semester=admin.create("/semesters",Map.of("academicYearId",year,"code","ADMIN_CRUD","name","Temporary semester"));
        assertThat(admin.call("PUT",ROOT+"/years/"+year,Map.of("code","ADMIN_CRUD","name","Updated year"),200).get("name").asText()).isEqualTo("Updated year");
        assertThat(admin.call("PUT",ROOT+"/subjects/"+subject,Map.of("code","ADMIN_CRUD","name","Updated subject"),200).get("name").asText()).isEqualTo("Updated subject");
        assertThat(admin.call("PUT",ROOT+"/semesters/"+semester,Map.of("academicYearId",year,"code","ADMIN_CRUD","name","Updated semester"),200).get("name").asText()).isEqualTo("Updated semester");
        admin.call("PUT",ROOT+"/semesters/"+semester,Map.of("academicYearId",usedYear,"code","ADMIN_CRUD","name","Move"),409);
        admin.call("PUT",ROOT+"/years/"+year,Map.of("code","2026-2027","name","Duplicate update"),409);
        for(var linked:Map.of("years",usedYear,"semesters",usedSemester,"subjects",usedSubject).entrySet())
            assertThat(admin.call("DELETE",ROOT+"/"+linked.getKey()+"/"+linked.getValue(),null,409).get("code").asText()).isEqualTo("RESOURCE_IN_USE");
        teacher.call("PUT",ROOT+"/subjects/"+subject,Map.of("code","X","name","Denied"),403);
        student.call("DELETE",ROOT+"/years/"+year,null,403);
        admin.call("DELETE",ROOT+"/years/"+year,null,409);
        for(var unused:Map.of("semesters",semester,"subjects",subject).entrySet())admin.call("DELETE",ROOT+"/"+unused.getKey()+"/"+unused.getValue(),null,204);
        admin.call("DELETE",ROOT+"/years/"+year,null,204);
        admin.call("DELETE",ROOT+"/years/"+year,null,404);
        var students=admin.call("GET","/api/admin/users?role=SINH_VIEN&q=student_test&size=1",null,200);
        assertThat(students.get("totalElements").asLong()).isEqualTo(1);
        assertThat(students.get("items").get(0).get("role").asText()).isEqualTo("SINH_VIEN");
        assertThat(students.get("items").get(0).get("maSinhVien").asText()).isNotBlank();
        assertThat(students.get("items").get(0).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(admin.call("GET","/api/admin/users?role=SINH_VIEN&q=%25",null,200).get("totalElements").asLong()).isZero();
        assertThat(admin.call("GET","/api/admin/users?role=GIANG_VIEN&q=teacher&size=1&page=1",null,200).get("items").size()).isEqualTo(1);
        admin.call("GET","/api/admin/users?role=SINH_VIEN&status=INVALID",null,400);
        teacher.call("GET","/api/admin/users?role=SINH_VIEN",null,403);
    }
}
