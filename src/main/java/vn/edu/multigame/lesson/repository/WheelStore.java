package vn.edu.multigame.lesson.repository;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
@Repository @Profile("mysql")
public class WheelStore {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 public WheelStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 private JsonNode read(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
 public JsonNode config(long course){return jdbc.query("SELECT revision,cells FROM class_wheel_config WHERE course_class_id=?",(r,n)->json.createObjectNode().put("revision",r.getLong(1)).<JsonNode>set("cells",read(r.getString(2))),course).stream().findFirst().orElseGet(()->json.createObjectNode().put("revision",0).<JsonNode>set("cells",json.createArrayNode()));}
 public void config(long course,JsonNode cells){jdbc.update("INSERT INTO class_wheel_config(course_class_id,revision,cells) VALUES(?,1,?) ON DUPLICATE KEY UPDATE revision=revision+1,cells=VALUES(cells)",course,cells.toString());}
 public JsonNode round(long lesson){return jdbc.query("SELECT state FROM lesson_wheel_round WHERE lesson_id=?",(r,n)->read(r.getString(1)),lesson).stream().findFirst().orElse(null);}
 public void save(long lesson,long game,JsonNode state){jdbc.update("INSERT INTO lesson_wheel_round(lesson_id,game_id,state) VALUES(?,?,?) ON DUPLICATE KEY UPDATE state=VALUES(state)",lesson,game,state.toString());}
 public String receipt(long actor,String request){return jdbc.query("SELECT fingerprint FROM lesson_wheel_receipt WHERE actor_id=? AND request_id=?",(r,n)->r.getString(1),actor,request).stream().findFirst().orElse(null);}
 public void receipt(long actor,String request,String hash){jdbc.update("INSERT INTO lesson_wheel_receipt VALUES(?,?,?)",actor,request,hash);}
 public void score(long lesson,long game,long student,java.math.BigDecimal score){jdbc.update("INSERT INTO lesson_game_score(game_id,student_id,score) VALUES(?,?,?) ON DUPLICATE KEY UPDATE score=VALUES(score)",game,student,score);}
}
