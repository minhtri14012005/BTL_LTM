package vn.edu.multigame.lesson.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import vn.edu.multigame.realtime.message.command.LessonCommand;

@Repository @Profile("mysql")
public class LessonRealtimeStore {
    public record Window(long lessonId,long gameId,int position,long token,int questionIndex,String status,long opened,long deadline) {}
    public record Receipt(String fingerprint,long sequence,long receivedAtMs) {}
    private final JdbcTemplate jdbc;
    public LessonRealtimeStore(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public Optional<Window> window(long lesson) {return jdbc.query("SELECT * FROM lesson_action_window WHERE lesson_id=?",(r,n)->new Window(lesson,r.getLong("game_id"),r.getInt("position"),r.getLong("token"),r.getInt("question_index"),r.getString("status"),r.getLong("opened_at_ms"),r.getLong("deadline_at_ms")),lesson).stream().findFirst();}
    public List<Long> openLessons() {return jdbc.queryForList("SELECT lesson_id FROM lesson_action_window WHERE status='OPEN'",Long.class);}
    public boolean completed(long lesson) {return "COMPLETED".equals(jdbc.queryForObject("SELECT status FROM lesson WHERE id=?",String.class,lesson));}
    public void open(long lesson,long game,int position,long token,int question,long now,long deadline) {
        jdbc.update("INSERT INTO lesson_action_window(lesson_id,game_id,position,token,question_index,status,opened_at_ms,deadline_at_ms) VALUES(?,?,?,?,?,'OPEN',?,?) ON DUPLICATE KEY UPDATE game_id=VALUES(game_id),position=VALUES(position),token=VALUES(token),question_index=VALUES(question_index),status='OPEN',opened_at_ms=VALUES(opened_at_ms),deadline_at_ms=VALUES(deadline_at_ms)",lesson,game,position,token,question,now,deadline);
    }
    public boolean close(long lesson,long token,String status) {return jdbc.update("UPDATE lesson_action_window SET status=? WHERE lesson_id=? AND token=? AND status='OPEN'",status,lesson,token)>0;}
    public Optional<Receipt> receipt(long actor,String request) {return jdbc.query("SELECT * FROM lesson_command_receipt WHERE actor_id=? AND request_id=?",(r,n)->new Receipt(r.getString("fingerprint"),r.getLong("ingress_sequence"),r.getLong("received_at_ms")),actor,request).stream().findFirst();}
    public void receipt(long actor,LessonCommand command,String fingerprint,long sequence,long now) {jdbc.update("INSERT INTO lesson_command_receipt(actor_id,request_id,lesson_id,fingerprint,command,ingress_sequence,received_at_ms) VALUES(?,?,?,?,?,?,?)",actor,command.requestId(),command.target().id(),fingerprint,command.type(),sequence,now);}
    public boolean submitted(long game,long token,long user) {return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_action_input WHERE game_id=? AND window_token=? AND student_id=?",Integer.class,game,token,user)>0;}
    public void input(Window window,long student,String request,String key,String value,long now,long sequence) {jdbc.update("INSERT INTO lesson_action_input(lesson_id,game_id,window_token,student_id,request_id,submission_key,input_value,received_at_ms,ingress_sequence) VALUES(?,?,?,?,?,?,?,?,?)",window.lessonId(),window.gameId(),window.token(),student,request,key,value,now,sequence);}
}
