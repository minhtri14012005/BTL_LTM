package vn.edu.multigame.lesson.repository;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.questionbank.enums.GameMode;
import vn.edu.multigame.lesson.dto.GameContentView;
import vn.edu.multigame.questionbank.dto.response.QuestionResponse;

@Repository @Profile("mysql")
public class GameContentStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public GameContentStore(JdbcTemplate jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    public Optional<GameContentView> get(long game) {
        return jdbc.query("SELECT c.*,s.name subject_name FROM lesson_game_content c JOIN academic_subject s ON s.id=c.subject_id WHERE game_id=?",(r,n)->new GameContentView(r.getLong("game_id"),r.getLong("source_quiz_id"),r.getLong("source_revision"),r.getLong("revision"),GameMode.valueOf(r.getString("mode")),r.getString("title"),read(r.getString("questions")),r.getLong("subject_id"),r.getString("subject_name")),game).stream().findFirst();
    }
    private List<QuestionResponse> read(String data) {
        try {return List.of(json.readValue(data,QuestionResponse[].class));}
        catch(Exception failure) {throw new IllegalStateException("Invalid persisted game content",failure);}
    }
    private String write(List<QuestionResponse> data) {
        try {return json.writeValueAsString(data);}
        catch(Exception failure) {throw new IllegalStateException("Cannot encode game content",failure);}
    }
    public void select(long game,long source,long sourceRevision,GameMode mode,String title,List<QuestionResponse> questions,long subjectId) {
        jdbc.update("INSERT INTO lesson_game_content(game_id,source_quiz_id,source_revision,mode,title,questions,subject_id) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE source_quiz_id=VALUES(source_quiz_id),source_revision=VALUES(source_revision),mode=VALUES(mode),title=VALUES(title),questions=VALUES(questions),subject_id=VALUES(subject_id),revision=revision+1",game,source,sourceRevision,mode.name(),title,write(questions),subjectId);
    }
    public void edit(long game,String title,List<QuestionResponse> questions) {jdbc.update("UPDATE lesson_game_content SET title=?,questions=?,revision=revision+1 WHERE game_id=?",title,write(questions),game);}
}
