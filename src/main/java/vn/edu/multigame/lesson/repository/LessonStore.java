package vn.edu.multigame.lesson.repository;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import vn.edu.multigame.lesson.domain.*;
import vn.edu.multigame.lesson.dto.*;
import vn.edu.multigame.lesson.service.LessonFailure;

@Repository @Profile("mysql")
public class LessonStore {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    public LessonStore(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private static final RowMapper<LessonState> ROW=(r,n)->new LessonState(r.getLong("id"),r.getLong("course_class_id"),r.getInt("lesson_number"),r.getString("title"),r.getBoolean("visible"),r.getString("status"),r.getLong("revision"),r.getString("create_fingerprint"));
    public LessonState lesson(long id,boolean lock) {
        return jdbc.query("SELECT * FROM lesson WHERE id=? AND deleted_at_ms IS NULL"+(lock?" FOR UPDATE":""),ROW,id).stream().findFirst()
                .orElseThrow(()->new LessonFailure(HttpStatus.NOT_FOUND,"LESSON_NOT_FOUND"));
    }
    public boolean deleted(long id){return jdbc.queryForObject("SELECT deleted_at_ms IS NOT NULL FROM lesson WHERE id=?",Boolean.class,id);}
    public Optional<LessonState> prior(long classId,String request) {return jdbc.query("SELECT * FROM lesson WHERE course_class_id=? AND create_request=?",ROW,classId,request).stream().findFirst();}
    public List<LessonState> list(long classId,boolean teacher) {return jdbc.query("SELECT * FROM lesson WHERE course_class_id=? AND deleted_at_ms IS NULL"+(teacher?"":" AND visible=TRUE")+" ORDER BY lesson_number",ROW,classId);}
    public int lockClass(long classId) {
        return jdbc.query("SELECT required_lessons FROM course_class WHERE id=? FOR UPDATE",(r,n)->r.getInt(1),classId).stream().findFirst()
                .orElseThrow(()->new LessonFailure(HttpStatus.NOT_FOUND,"CLASS_NOT_FOUND"));
    }
    public LessonView.Progress progress(long classId) {
        int quota=jdbc.queryForObject("SELECT required_lessons FROM course_class WHERE id=?",Integer.class,classId);
        return jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(status='COMPLETED'),0) FROM lesson WHERE course_class_id=? AND deleted_at_ms IS NULL",(r,n)->new LessonView.Progress(quota,r.getInt(1),r.getInt(2)),classId);
    }
    public long create(long classId,int number,LessonRequests.Plan input,String hash) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var p=c.prepareStatement("INSERT INTO lesson(course_class_id,lesson_number,title,create_request,create_fingerprint) VALUES(?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            p.setLong(1,classId);p.setInt(2,number);p.setString(3,input.title().strip());p.setString(4,input.requestId());p.setString(5,hash);return p;},key);
        long id=Objects.requireNonNull(key.getKey()).longValue();slots(id,input.slots());return id;
    }
    private void slots(long lesson,List<LessonRequests.Slot> slots) {
        for(int i=0;i<slots.size();i++) {var slot=slots.get(i);jdbc.update("INSERT INTO lesson_game(lesson_id,position,mode,title,budget) VALUES(?,?,?,?,?)",lesson,i+1,slot.mode().name(),slot.title().strip(),slot.budget());}
    }
    public void edit(long id,LessonRequests.Plan input) {
        var current=games(id,0);
        for(int i=0;i<3;i++) {
            if(current.get(i).mode()!=input.slots().get(i).mode() && jdbc.queryForObject("SELECT COUNT(*) FROM lesson_game_content WHERE game_id=?",Integer.class,current.get(i).id())>0)
                throw LessonFailure.conflict("CONTENT_MODE_FROZEN");
        }
        jdbc.update("UPDATE lesson_game SET budget=0 WHERE lesson_id=?",id);
        // Remove only changed empty slots, keeping all unchanged game IDs, settings and snapshots.
        for(int i=0;i<3;i++) if(current.get(i).mode()!=input.slots().get(i).mode())
            jdbc.update("DELETE FROM lesson_game WHERE id=?",current.get(i).id());
        for(int i=0;i<3;i++) {
            var slot=input.slots().get(i);
            if(current.get(i).mode()!=slot.mode()) jdbc.update("INSERT INTO lesson_game(lesson_id,position,mode,title,budget) VALUES(?,?,?,?,?)",id,i+1,slot.mode().name(),slot.title().strip(),slot.budget());
            else jdbc.update("UPDATE lesson_game SET title=?,budget=? WHERE id=?",slot.title().strip(),slot.budget(),current.get(i).id());
        }
        jdbc.update("UPDATE lesson SET title=?,revision=revision+1 WHERE id=?",input.title().strip(),id);
    }
    public List<LessonState> templates(long classId,long teacher) {
        return jdbc.query("SELECT l.* FROM lesson l JOIN course_class c ON c.id=l.course_class_id JOIN subject_offering o ON o.id=c.offering_id JOIN teaching_assignment a ON a.course_class_id=c.id WHERE a.teacher_id=? AND l.status='COMPLETED' AND l.deleted_at_ms IS NULL AND o.subject_id=(SELECT t.subject_id FROM course_class d JOIN subject_offering t ON t.id=d.offering_id WHERE d.id=?) ORDER BY l.id DESC",ROW,teacher,classId);
    }
    public void delete(long id,long course) {
        jdbc.update("UPDATE lesson SET deleted_at_ms=?,revision=revision+1 WHERE id=?",System.currentTimeMillis(),id);
        String games="SELECT id FROM lesson_game WHERE lesson_id=?";
        for(String table:List.of("achievement_award","lesson_assignment_grade","lesson_assignment_deadline","lesson_quiz_answer","lesson_quiz_award_roster","lesson_quiz_question","lesson_game_score"))
            jdbc.update("DELETE FROM "+table+" WHERE "+(table.equals("achievement_award")?"lesson_game_id":"game_id")+" IN ("+games+")",id);
        for(String table:List.of("lesson_action_input","lesson_action_window","lesson_wheel_round","lesson_command_receipt","lesson_wheel_receipt","lesson_score","lesson_attendance"))
            jdbc.update("DELETE FROM "+table+" WHERE lesson_id=?",id);
        jdbc.update("DELETE FROM lesson_game_participant WHERE game_id IN ("+games+")",id);
        for(long game:jdbc.queryForList(games,Long.class,id))jdbc.update("DELETE FROM lesson_game_content WHERE game_id=?",game);
        jdbc.update("DELETE FROM lesson_game WHERE lesson_id=?",id);
        // Ascending updates avoid transient collisions on the class's unique ordinal key.
        int number=1;
        for(var remaining:list(course,true)) {
            if(remaining.number()!=number)jdbc.update("UPDATE lesson SET lesson_number=?,revision=revision+1 WHERE id=?",number,remaining.id());
            number++;
        }
    }
    public void visibility(long id,boolean visible) {jdbc.update("UPDATE lesson SET visible=?,revision=revision+1 WHERE id=?",visible,id);}
    public void start(long id) {jdbc.update("UPDATE lesson SET status='RUNNING',revision=revision+1 WHERE id=?",id);}
    public void bump(long id) {jdbc.update("UPDATE lesson SET revision=revision+1 WHERE id=?",id);}
    public void finishIfReady(long id) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_game WHERE lesson_id=? AND status='COMPLETED'",Integer.class,id)==3)
            jdbc.update("UPDATE lesson SET status='COMPLETED',revision=revision+1 WHERE id=?",id);
        else bump(id);
    }
    public List<LessonView.Game> games(long lesson,long user) {
        return jdbc.query("SELECT g.*,EXISTS(SELECT 1 FROM lesson_game_participant p WHERE p.game_id=g.id AND p.student_id=?) AS participating,COALESCE((SELECT s.score FROM lesson_game_score s WHERE s.game_id=g.id AND s.student_id=?),0) AS earned FROM lesson_game g WHERE g.lesson_id=? ORDER BY position",
                (r,n)->new LessonView.Game(r.getLong("id"),r.getInt("position"),LessonMode.valueOf(r.getString("mode")),r.getString("title"),r.getBigDecimal("budget"),r.getString("status"),r.getBoolean("participating"),r.getBigDecimal("earned"),r.getBoolean("visible"),r.getInt("duration_ms"),(Integer)r.getObject("question_count"),cells(r.getString("wheel_cells")),r.getInt("selected_count")),user,user,lesson);
    }
    private List<String> cells(String value) {
        if(value==null)return null;
        try{return List.of(new com.fasterxml.jackson.databind.ObjectMapper().readValue(value,String[].class));}
        catch(Exception e){throw new IllegalStateException("Invalid wheel settings",e);}
    }
    public void gameVisibility(long game,boolean visible){jdbc.update("UPDATE lesson_game SET visible=? WHERE id=?",visible,game);}
    public void configure(long game,BigDecimal budget,int duration,Integer questionCount,List<String> cells,int count){
        String encoded=null;
        try{if(cells!=null)encoded=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cells);}catch(Exception e){throw new IllegalArgumentException(e);}
        jdbc.update("UPDATE lesson_game SET budget=?,duration_ms=?,question_count=?,wheel_cells=?,selected_count=? WHERE id=?",budget,duration,questionCount,encoded,count,game);
    }
    public boolean attending(long lesson,long student) {return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_attendance WHERE lesson_id=? AND student_id=?",Integer.class,lesson,student)>0;}
    public void attend(long lesson,long student,long now) {
        jdbc.update("INSERT INTO lesson_attendance(lesson_id,student_id,joined_at_ms) VALUES(?,?,?) ON DUPLICATE KEY UPDATE joined_at_ms=joined_at_ms",lesson,student,now);
        jdbc.update("INSERT INTO lesson_score(lesson_id,student_id) VALUES(?,?) ON DUPLICATE KEY UPDATE score=score",lesson,student);
    }
    public void participate(long game,long student,long now) {jdbc.update("INSERT INTO lesson_game_participant(game_id,student_id,joined_at_ms) VALUES(?,?,?) ON DUPLICATE KEY UPDATE joined_at_ms=joined_at_ms",game,student,now);}
    public List<Long> participants(long game) {return jdbc.queryForList("SELECT student_id FROM lesson_game_participant WHERE game_id=? ORDER BY student_id",Long.class,game);}
    public int correct(long game,long student) {return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_quiz_answer WHERE game_id=? AND student_id=? AND is_correct=TRUE",Integer.class,game,student);}
    public void gameStatus(long game,String status) {jdbc.update("UPDATE lesson_game SET status=? WHERE id=?",status,game);}
    public void score(long game,long student,BigDecimal score) {jdbc.update("INSERT INTO lesson_game_score(game_id,student_id,score) VALUES(?,?,?)",game,student,score);}
    public void total(long lesson,long student) {
        jdbc.update("UPDATE lesson_score SET score=(SELECT COALESCE(SUM(s.score),0) FROM lesson_game_score s JOIN lesson_game g ON g.id=s.game_id WHERE g.lesson_id=? AND s.student_id=?) WHERE lesson_id=? AND student_id=?",lesson,student,lesson,student);
        long course=jdbc.queryForObject("SELECT course_class_id FROM lesson WHERE id=?",Long.class,lesson);events.publishEvent(new vn.edu.multigame.leaderboard.service.LearningScoreChanged(course));
    }
    public BigDecimal scoreTotal(long lesson,long student) {return jdbc.query("SELECT score FROM lesson_score WHERE lesson_id=? AND student_id=?",(r,n)->r.getBigDecimal(1),lesson,student).stream().findFirst().orElse(new BigDecimal("0.00"));}
}
