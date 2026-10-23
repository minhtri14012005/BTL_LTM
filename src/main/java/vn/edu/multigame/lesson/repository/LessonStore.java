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
        return jdbc.query("SELECT * FROM lesson WHERE id=?"+(lock?" FOR UPDATE":""),ROW,id).stream().findFirst()
                .orElseThrow(()->new LessonFailure(HttpStatus.NOT_FOUND,"LESSON_NOT_FOUND"));
    }
    public Optional<LessonState> prior(long classId,String request) {return jdbc.query("SELECT * FROM lesson WHERE course_class_id=? AND create_request=?",ROW,classId,request).stream().findFirst();}
    public List<LessonState> list(long classId,boolean teacher) {return jdbc.query("SELECT * FROM lesson WHERE course_class_id=?"+(teacher?"":" AND visible=TRUE")+" ORDER BY lesson_number",ROW,classId);}
    public int lockClass(long classId) {
        return jdbc.query("SELECT required_lessons FROM course_class WHERE id=? FOR UPDATE",(r,n)->r.getInt(1),classId).stream().findFirst()
                .orElseThrow(()->new LessonFailure(HttpStatus.NOT_FOUND,"CLASS_NOT_FOUND"));
    }
    public LessonView.Progress progress(long classId) {
        int quota=jdbc.queryForObject("SELECT required_lessons FROM course_class WHERE id=?",Integer.class,classId);
        return jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(status='COMPLETED'),0) FROM lesson WHERE course_class_id=?",(r,n)->new LessonView.Progress(quota,r.getInt(1),r.getInt(2)),classId);
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
        boolean sameModes=true;
        for(int i=0;i<3;i++) if(!current.get(i).mode().equals(input.slots().get(i).mode())) sameModes=false;
        if(sameModes) {
            // Preserve selected content and game identity when editing draft budgets/titles.
            jdbc.update("UPDATE lesson_game SET budget=0 WHERE lesson_id=?",id);
            for(int i=0;i<3;i++) jdbc.update("UPDATE lesson_game SET title=?,budget=? WHERE lesson_id=? AND position=?",input.slots().get(i).title().strip(),input.slots().get(i).budget(),id,i+1);
        } else {
            jdbc.update("DELETE FROM lesson_game_content WHERE game_id IN (SELECT id FROM lesson_game WHERE lesson_id=?)",id);
            jdbc.update("DELETE FROM lesson_game WHERE lesson_id=?",id);slots(id,input.slots());
        }
        jdbc.update("UPDATE lesson SET title=?,revision=revision+1 WHERE id=?",input.title().strip(),id);
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
                (r,n)->new LessonView.Game(r.getLong("id"),r.getInt("position"),LessonMode.valueOf(r.getString("mode")),r.getString("title"),r.getBigDecimal("budget"),r.getString("status"),r.getBoolean("participating"),r.getBigDecimal("earned")),user,user,lesson);
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
