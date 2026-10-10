package vn.edu.multigame.game.repository;

import java.nio.file.*;
import java.sql.Statement;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import vn.edu.multigame.MultigameApplication;
import vn.edu.multigame.game.dto.*;
import vn.edu.multigame.game.enums.*;
import vn.edu.multigame.game.service.GameHistoryService;
import vn.edu.multigame.game.enums.GameMode;
import static org.assertj.core.api.Assertions.*;

/** Creates a private test schema, migrates V1-V3, saves real v1 rows, then upgrades.
 * No DROP/TRUNCATE/cleanup of existing databases; schema name is printed for audit. */
class MultimodeMigrationIT {
    JdbcTemplate jdbc;
    ObjectMapper json=new ObjectMapper();
    long author,user,quiz,source,room,legacy,cancelled;
    long insert(String sql,Object...args) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},keys);
        return keys.getKey().longValue();
    }
    String property(Properties p,String key,String fallback) {
        return Optional.ofNullable(System.getenv(key)).orElse(p.getProperty(key,fallback));
    }
    @Test void upgradeWithV1HistoryThenReadV2AndRejectInvalidConstraints() throws Exception {
        var local=new Properties();try(var r=Files.newBufferedReader(Path.of("config/application-local.properties"))) {local.load(r);}
        String host=property(local,"DB_HOST","127.0.0.1"),port=property(local,"DB_PORT","3306"),
            username=property(local,"DB_USER","quiz_app"),password=property(local,"DB_PASSWORD","");
        String schema="quizz_task16_migration_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String rootUrl="jdbc:mysql://"+host+":"+port+"/?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        var admin=new JdbcTemplate(new DriverManagerDataSource(rootUrl,username,password));
        admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        String url=rootUrl.replace("/?","/"+schema+"?");
        var ds=new DriverManagerDataSource(url,username,password);jdbc=new JdbcTemplate(ds);
        try(var connection=ds.getConnection()) {assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");}
        Flyway.configure().dataSource(ds).target("3").load().migrate();
        seedV1();
        String configBefore=jdbc.queryForObject("select cast(config_snapshot as char) from game_session where id=?",String.class,legacy);
        String answerBefore=jdbc.queryForObject("select cast(result_snapshot as char) from answer where game_session_id=?",String.class,legacy);
        Flyway.configure().dataSource(ds).target("5").load().migrate();
        long oldSet=insert("insert into quiz(owner_user_id,title,visibility,created_at_ms,mode) values(?,'Old pieces','PUBLIC',1000,'VIETNAMESE_PUZZLE')",author);
        String oldPayload=json.writeValueAsString(Map.of("pieces",List.of(Map.of("id","a","text","a"),Map.of("id","b","text","b")),"correctOrder",List.of("a","b")));
        long oldQuestion=insert("insert into question(quiz_id,order_index,content,created_at_ms,schema_version,mode,payload) values(?,1,'Old arrangement',1000,2,'VIETNAMESE_PUZZLE',?)",oldSet,oldPayload);
        String arrangementBefore=jdbc.queryForObject("select cast(payload as char) from question where id=?",String.class,oldQuestion);
        Flyway.configure().dataSource(ds).load().migrate();
        assertThat(jdbc.queryForObject("select cast(payload as char) from question where id=?",String.class,oldQuestion)).isEqualTo(arrangementBefore);
        var withAliases=json.readTree(oldPayload).deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)withAliases).set("acceptedAnswers",json.valueToTree(List.of("ab")));
        reject("insert into question(quiz_id,order_index,content,created_at_ms,schema_version,mode,payload) values(?,2,'Unpaired alias policy',1000,2,'VIETNAMESE_PUZZLE',?)",oldSet,withAliases.toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode)withAliases).put("matchingPolicy","NFC_CASE_INSENSITIVE_WHITESPACE");
        long aliasQuestion=insert("insert into question(quiz_id,order_index,content,created_at_ms,schema_version,mode,payload) values(?,2,'Aliases',1000,2,'VIETNAMESE_PUZZLE',?)",oldSet,withAliases.toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode)withAliases).set("acceptedAnswers",json.valueToTree(List.of()));
        reject("update question set payload=? where id=?",withAliases.toString(),aliasQuestion);
        assertThat(jdbc.queryForObject("select cast(config_snapshot as char) from game_session where id=?",String.class,legacy)).isEqualTo(configBefore);
        assertThat(jdbc.queryForObject("select cast(result_snapshot as char) from answer where game_session_id=?",String.class,legacy)).isEqualTo(answerBefore);
        assertThat(jdbc.queryForObject("select schema_version from game_session where id=?",Integer.class,legacy)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select schema_version from game_question where game_session_id=?",Integer.class,legacy)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select mode from quiz where id=?",String.class,quiz)).isEqualTo("QUIZ");
        var v2=seedV2();
        reject("update player_session set score=-1 where game_session_id=?",v2[0]);
        reject("update player_session set player_state='ELIMINATED',score=-1,eliminated_at_ms=1200,eliminated_question_index=1 where game_session_id=?",v2[0]);
        reject("update question set payload='{}' where id=?",v2[3]);
        reject("update question set option_a='fake' where id=?",v2[3]);
        reject("update quiz set mode='QUIZ' where id=?",v2[4]);
        reject("update game_stage set title_snapshot='changed' where id=?",v2[1]);
        reject("update game_question set payload='{}' where id=?",v2[2]);
        reject("update game_session set v2_config_snapshot='{}' where id=?",v2[0]);
        reject("update player_session set schema_version=1 where game_session_id=?",v2[0]);
        reject("insert into game_stage(game_session_id,order_index,mode,source_quiz_id,author_user_id,title_snapshot,first_question_index,question_count,question_duration_ms,config_snapshot) values(?,2,'RIDDLE',?,?,'Duplicate',1,1,20000,'{}')",v2[0],v2[4],author);
        reject("insert into game_question(game_session_id,source_question_id,order_index,content,question_duration_ms,phase,schema_version,mode,stage_id,stage_question_index,payload) values(?,?,2,'Bad bounds',20000,'RESULT',2,'RIDDLE',?,2,?)",v2[0],v2[3],v2[1],riddlePayload());
        reject("insert into answer(game_session_id,player_session_id,game_question_id,answer_status,received_at_ms,answer_time_ms,schema_version,mode,answer_kind,answer_payload) values(?,?,?,'ACCEPTED_UNSCORED',1200,200,2,'RIDDLE','OPTION','{\"text\":\"bàn\"}')",v2[0],v2[5],v2[2]);
        reject("insert into answer(game_session_id,player_session_id,game_question_id,answer_status,received_at_ms,answer_time_ms,schema_version,mode,answer_kind,answer_payload) values(?,?,?,'ACCEPTED_UNSCORED',1200,200,2,'RIDDLE','TEXT','{\"text\":\"bàn\"}')",legacy,v2[5],v2[2]);
        reject("update answer set result_snapshot='{\"schemaVersion\":2,\"mode\":\"RIDDLE\",\"correctAnswerTimeMs\":200,\"hasMomentumBefore\":false}' where game_session_id=?",v2[0]);
        reject("update answer set answer_payload='{\"text\":\"khác\"}' where game_session_id=?",v2[0]);
        try(var ctx=new SpringApplicationBuilder(MultigameApplication.class).run("--spring.profiles.active=mysql",
                "--spring.datasource.url="+url,"--spring.datasource.username="+username,"--spring.datasource.password="+password,
                "--server.port=0","--server.address=127.0.0.1","--debug=false")) {
            var history=ctx.getBean(GameHistoryService.class);
            var old=history.detail(user,legacy);
            assertThat(old.finalSnapshot().player().score()).isEqualTo(-4);
            assertThat(old.finalSnapshot().player().state()).isEqualTo(PlayerState.ELIMINATED);
            assertThat(old.finalSnapshot().player().totalAnswerTimeMs()).isEqualTo(1312);
            assertThat(old.finalSnapshot().endReason()).isEqualTo(EndReason.ALL_ELIMINATED);
            assertThat(old.finalSnapshot().winners()).containsExactly(user);
            assertThat(old.questions().getFirst().answers().getFirst().selectedOption().name()).isEqualTo("A");
            var unscored=history.detail(user,cancelled).questions().getFirst().answers().getFirst();
            assertThat(unscored.answerStatus()).isEqualTo(AnswerStatus.ACCEPTED_UNSCORED);
            assertThat(unscored.scoredAtMs()).isNull();assertThat(unscored.result()).isNull();
            assertThat(unscored.scoreDelta()).isNull();
            assertThat(history.list(user,0,20).items()).hasSize(3);
            var modern=history.detail(user,v2[0]);
            assertThat(modern.finalSnapshot().schemaVersion()).isEqualTo(2);
            assertThat(modern.finalSnapshot().player().totalCorrectAnswerTimeMs()).isEqualTo(200);
            assertThat(modern.questions().getFirst().answers().getFirst().submittedAnswer().text()).isEqualTo("bàn");
            var game=ctx.getBean(GameSessionRepository.class).findById(v2[0]).orElseThrow();
            assertThat(game.getSchemaVersion()).isEqualTo(2);assertThat(game.getRulesVersion()).isEqualTo(2);
            assertThat(game.getConfigSnapshot()).isNull();
            assertThat(game.getV2ConfigSnapshot()).isEqualTo(MultimodeRulesSnapshot.forGame(1,0));
            assertThat(ctx.getBean(GameStageRepository.class).findByGameSessionIdOrderByOrderIndex(v2[0]).getFirst().getMode()).isEqualTo(GameMode.RIDDLE);
            var question=ctx.getBean(GameQuestionRepository.class).findById(v2[2]).orElseThrow();
            assertThat(question.getOptionA()).isNull();assertThat(question.getPayload()).containsKey("acceptedAnswers");
            var answer=ctx.getBean(AnswerRepository.class).findByGameSessionId(v2[0]).getFirst();
            assertThat(answer.getAnswerPayload().text()).isEqualTo("bàn");assertThat(answer.getSelectedOption()).isNull();
            long extraUser=insert("insert into app_user(username,password_hash,display_name,created_at_ms) values('extra','hash','Extra',1000)");
            jdbc.update("insert into room_member(room_id,user_id,participation,status,joined_at_ms) values(?,?,'PLAYER','JOINED',1000)",room,extraUser);
            jdbc.update("insert into game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) values(?,?,?,'MEMBER','PLAYER','Extra')",v2[0],room,extraUser);
            long extraPlayer=insert("insert into player_session(game_session_id,user_id,player_state,score,remaining_spins,remaining_spin_pool,star_available,schema_version,total_correct_answer_time_ms) values(?,?,'PLAYING',0,0,'[]',0,2,0)",v2[0],extraUser);
            var template=new org.springframework.transaction.support.TransactionTemplate(ctx.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            template.executeWithoutResult(status -> {
                var row=new vn.edu.multigame.game.entity.Answer();row.setGameSessionId(v2[0]);row.setPlayerSessionId(extraPlayer);row.setGameQuestionId(v2[2]);
                row.setSchemaVersion(2);row.setMode(GameMode.RIDDLE);row.setAnswerKind(AnswerKind.TEXT);
                row.setAnswerStatus(AnswerStatus.ACCEPTED_UNSCORED);row.setReceivedAtMs(1200L);row.setAnswerTimeMs(200L);row.setAnswerPayload(new TypedAnswer("bàn",null));
                var repository=ctx.getBean(AnswerRepository.class);repository.saveAndFlush(row);
                assertThat(repository.findById(row.getId()).orElseThrow().getAnswerPayload().text()).isEqualTo("bàn");
            });
            allModePayloadsAndTypedInputs(ctx,template);
        }
        System.out.println("TASK16_MIGRATION: MySQL v1->latest, retained legacy history, v2 readers/constraints; schema="+schema);
    }
    void allModePayloadsAndTypedInputs(org.springframework.context.ConfigurableApplicationContext ctx,
            org.springframework.transaction.support.TransactionTemplate template) throws Exception {
        long game=insert("insert into game_session(room_id,status,phase,end_reason,question_count,schema_version,rules_version,v2_config_snapshot,started_at_ms,finished_at_ms) values(?,'FINISHED','FINISHED','COMPLETED',7,2,2,?,1000,2000)",room,json.writeValueAsString(MultimodeRulesSnapshot.forGame(7,1)));
        members(game);
        long player=insert("insert into player_session(game_session_id,user_id,player_state,score,remaining_spins,remaining_spin_pool,star_available,schema_version,total_correct_answer_time_ms) values(?,?,'PLAYING',0,0,'[]',0,2,0)",game,user);
        int index=0;
        for(var mode:GameMode.values()) {
            int order=++index;
            long set=insert("insert into quiz(owner_user_id,title,visibility,created_at_ms,mode) values(?,'Data fixture','PUBLIC',1000,?)",author,mode.name());
            var pieces=List.of(Map.of("id","one","text","a"),Map.of("id","two","text","b"));
            var payload=new LinkedHashMap<String,Object>();
            if(mode==GameMode.VIETNAMESE_PUZZLE || mode==GameMode.ORDERING) {
                payload.put(mode==GameMode.ORDERING?"items":"pieces",pieces);payload.put("correctOrder",List.of("one","two"));
            } else if(mode!=GameMode.QUIZ) {
                payload.put("acceptedAnswers",List.of("bàn"));payload.put("matchingPolicy","NFC_CASE_INSENSITIVE_WHITESPACE");
                if(mode==GameMode.SONG || mode==GameMode.IMAGE_WORD) payload.put("mediaRef","sha256:"+"a".repeat(64));
                if(mode==GameMode.CLUES) payload.put("hints",List.of(Map.of("offsetMs",0,"text","Hint1"),Map.of("offsetMs",1000,"text","Hint2")));
            }
            boolean quizMode=mode==GameMode.QUIZ;
            String encoded=quizMode?null:json.writeValueAsString(payload);
            long src=insert("insert into question(quiz_id,order_index,content,created_at_ms,schema_version,mode,payload,option_a,option_b,option_c,option_d,correct_option) values(?,1,'Fixture',1000,2,?,?,?,?,?,?,?)",
                set,mode.name(),encoded,quizMode?"A":null,quizMode?"B":null,quizMode?"C":null,quizMode?"D":null,quizMode?"A":null);
            reject("insert into question(quiz_id,order_index,content,created_at_ms,schema_version,mode,payload) values(?,2,'Invalid payload',1000,2,?,'{}')",set,mode.name());
            long stage=insert("insert into game_stage(game_session_id,order_index,mode,source_quiz_id,author_user_id,title_snapshot,first_question_index,question_count,question_duration_ms,config_snapshot) values(?,?,?,?,?,'Fixture',?,1,20000,'{}')",game,order,mode.name(),set,author,order);
            long q=insert("insert into game_question(game_session_id,source_question_id,order_index,content,question_duration_ms,phase,opened_at_ms,deadline_at_ms,schema_version,mode,stage_id,stage_question_index,payload,option_a,option_b,option_c,option_d,correct_option) values(?,?,?,'Fixture',20000,'QUESTION_OPEN',1000,21000,2,?,?,1,?,?,?,?,?,?)",
                game,src,order,mode.name(),stage,encoded,quizMode?"A":null,quizMode?"B":null,quizMode?"C":null,quizMode?"D":null,quizMode?"A":null);
            template.executeWithoutResult(status -> {
                var a=new vn.edu.multigame.game.entity.Answer();a.setGameSessionId(game);a.setPlayerSessionId(player);a.setGameQuestionId(q);a.setSchemaVersion(2);a.setMode(mode);
                a.setAnswerStatus(AnswerStatus.ACCEPTED_UNSCORED);a.setReceivedAtMs(1100L);a.setAnswerTimeMs(100L);
                if(quizMode) {a.setAnswerKind(AnswerKind.OPTION);a.setSelectedOption(vn.edu.multigame.quiz.enums.Option.A);}
                else if(mode==GameMode.VIETNAMESE_PUZZLE || mode==GameMode.ORDERING) {a.setAnswerKind(AnswerKind.ARRANGEMENT);a.setAnswerPayload(new TypedAnswer(null,List.of("one","two")));}
                else {a.setAnswerKind(AnswerKind.TEXT);a.setAnswerPayload(new TypedAnswer("bàn",null));}
                ctx.getBean(AnswerRepository.class).saveAndFlush(a);
            });
        }
        var data=ctx.getBean(AnswerRepository.class).findByGameSessionId(game);
        assertThat(data).hasSize(7);assertThat(data.stream().map(a->a.getMode()).toList()).containsExactlyInAnyOrder(GameMode.values());
        assertThat(data.stream().filter(a->a.getAnswerKind()==AnswerKind.ARRANGEMENT).toList()).allSatisfy(a->assertThat(a.getAnswerPayload().itemIds()).containsExactly("one","two"));
    }
    void reject(String sql,Object...args) {
        var failure=catchThrowable(()->jdbc.update(sql,args));
        assertThat(failure).isInstanceOf(org.springframework.dao.DataAccessException.class);
        Throwable cause=failure;while(cause.getCause()!=null) cause=cause.getCause();
        assertThat(cause).isInstanceOf(java.sql.SQLException.class);
        // CHECK, FK, uniqueness or explicit immutable/bounds trigger; syntax errors are not evidence.
        assertThat(((java.sql.SQLException)cause).getErrorCode()).isIn(3819,1452,1062,1644);
    }
    String riddlePayload() throws Exception {return json.writeValueAsString(Map.of("acceptedAnswers",List.of("bàn"),"matchingPolicy","NFC_CASE_INSENSITIVE_WHITESPACE"));}
    void seedV1() throws Exception {
        author=insert("insert into app_user(username,password_hash,display_name,created_at_ms) values('author','hash','Author',1000)");
        user=insert("insert into app_user(username,password_hash,display_name,created_at_ms) values('player','hash','Player',1000)");
        quiz=insert("insert into quiz(owner_user_id,title,visibility,created_at_ms) values(?,'Legacy','PUBLIC',1000)",author);
        source=insert("insert into question(quiz_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,created_at_ms) values(?,1,'Legacy?','A','B','C','D','B',1000)",quiz);
        room=insert("insert into room(host_user_id,quiz_id,room_code,name,status,max_players,question_duration_ms,created_at_ms) values(?,?,'LEGACY','Legacy','WAITING',3,20000,1000)",author,quiz);
        jdbc.update("insert into room_member(room_id,user_id,participation,status,joined_at_ms) values(?,?,'SPECTATOR','JOINED',1000)",room,author);
        jdbc.update("insert into room_member(room_id,user_id,participation,status,joined_at_ms) values(?,?,'PLAYER','JOINED',1000)",room,user);
        String config=json.writeValueAsString(GameplayRulesSnapshot.forGame(10,20000,5000));
        legacy=insert("insert into game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,end_reason,question_count,current_question_index,config_snapshot,started_at_ms,finished_at_ms) values(?,?,?,'Legacy','FINISHED','FINISHED','ALL_ELIMINATED',10,1,?,1000,4000)",room,quiz,author,config);
        cancelled=insert("insert into game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,end_reason,question_count,current_question_index,config_snapshot,started_at_ms,finished_at_ms) values(?,?,?,'Legacy','FINISHED','FINISHED','CANCELLED',10,1,?,1000,4000)",room,quiz,author,config);
        for(long g:List.of(legacy,cancelled)) {
            members(g);
            boolean eliminated=g==legacy;
            long p=insert("insert into player_session(game_session_id,user_id,player_state,score,total_answer_time_ms,remaining_spins,remaining_spin_pool,eliminated_at_ms,eliminated_question_index,final_rank) values(?,?,?, ?,?,1,'[\"SAFE\"]',?,?,1)",
                g,user,eliminated?"ELIMINATED":"PLAYING",eliminated?-4:20,eliminated?1312:0,eliminated?2312:null,eliminated?1:null);
            long q=insert("insert into game_question(game_session_id,source_question_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,question_duration_ms,phase,opened_at_ms,deadline_at_ms,scored_at_ms) values(?,?,1,'Legacy?','A','B','C','D','B',20000,?,1000,21000,?)",g,source,eliminated?"RESULT":"QUESTION_OPEN",eliminated?2312:null);
            if(eliminated) jdbc.update("insert into answer(game_session_id,player_session_id,game_question_id,answer_status,selected_option,received_at_ms,answer_time_ms,scored_at_ms,base_delta,score_delta,score_after,result_snapshot) values(?,?,?,'WRONG','A',2312,1312,2312,-4,-4,-4,?)",
                g,p,q,json.writeValueAsString(Map.of("hasMomentumBefore",false,"hasRecoveryBefore",false,"hasMomentumAfter",false,"hasRecoveryAfter",false)));
            else jdbc.update("insert into answer(game_session_id,player_session_id,game_question_id,answer_status,selected_option,received_at_ms,answer_time_ms) values(?,?,?,'ACCEPTED_UNSCORED','A',1100,100)",g,p,q);
        }
    }
    void members(long game) {
        jdbc.update("insert into game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) values(?,?,?,'HOST','SPECTATOR','Author')",game,room,author);
        jdbc.update("insert into game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) values(?,?,?,'MEMBER','PLAYER','Player')",game,room,user);
    }
    long[] seedV2() throws Exception {
        long set=insert("insert into quiz(owner_user_id,title,visibility,created_at_ms,mode) values(?,'Riddle','PRIVATE',1000,'RIDDLE')",author);
        long src=insert("insert into question(quiz_id,order_index,content,created_at_ms,schema_version,mode,payload) values(?,1,'Có chân?',1000,2,'RIDDLE',?)",set,riddlePayload());
        long g=insert("insert into game_session(room_id,status,phase,end_reason,question_count,current_question_index,schema_version,rules_version,v2_config_snapshot,started_at_ms,finished_at_ms) values(?,'FINISHED','FINISHED','COMPLETED',1,1,2,2,?,1000,2000)",
            room,json.writeValueAsString(MultimodeRulesSnapshot.forGame(1,0)));
        members(g);
        long stage=insert("insert into game_stage(game_session_id,order_index,mode,source_quiz_id,author_user_id,title_snapshot,first_question_index,question_count,question_duration_ms,config_snapshot) values(?,1,'RIDDLE',?,?,'Riddle',1,1,20000,'{}')",g,set,author);
        long q=insert("insert into game_question(game_session_id,source_question_id,order_index,content,question_duration_ms,phase,opened_at_ms,deadline_at_ms,scored_at_ms,schema_version,mode,stage_id,stage_question_index,payload) values(?,?,1,'Có chân?',20000,'RESULT',1000,21000,1200,2,'RIDDLE',?,1,?)",g,src,stage,riddlePayload());
        long p=insert("insert into player_session(game_session_id,user_id,player_state,score,remaining_spins,remaining_spin_pool,star_available,schema_version,total_correct_answer_time_ms,final_rank) values(?,?,'PLAYING',10,0,'[]',0,2,200,1)",g,user);
        jdbc.update("insert into answer(game_session_id,player_session_id,game_question_id,answer_status,received_at_ms,answer_time_ms,scored_at_ms,base_delta,score_delta,score_after,schema_version,mode,answer_kind,answer_payload,result_snapshot) values(?,?,?,'CORRECT',1200,200,1200,10,10,10,2,'RIDDLE','TEXT','{\"text\":\"bàn\"}','{\"schemaVersion\":2,\"mode\":\"RIDDLE\",\"correctAnswerTimeMs\":200}')",g,p,q);
        return new long[]{g,stage,q,src,set,p};
    }
}
