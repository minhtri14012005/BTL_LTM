package vn.edu.quiz.game.repository;

import vn.edu.quiz.game.entity.PlayerSession;
import vn.edu.quiz.game.entity.GameSession;
import vn.edu.quiz.game.entity.GameQuestion;
import vn.edu.quiz.game.dto.GameplayRulesSnapshot;
import vn.edu.quiz.game.entity.Answer;
import vn.edu.quiz.room.repository.RoomMemberRepository;
import vn.edu.quiz.room.entity.RoomMember;
import vn.edu.quiz.user.repository.UserRepository;
import vn.edu.quiz.user.entity.UserAccount;
import vn.edu.quiz.room.enums.Participation;
import vn.edu.quiz.room.enums.MembershipStatus;
import vn.edu.quiz.game.enums.Phase;
import vn.edu.quiz.game.enums.PlayerState;
import vn.edu.quiz.game.enums.AnswerStatus;
import vn.edu.quiz.quiz.enums.Option;
import vn.edu.quiz.game.enums.SpinEffect;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Real MySQL only. Each fixture rolls back; the dedicated schema keeps Flyway history. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000"
})
@ActiveProfiles("mysql")
@Transactional
class MySqlSchemaIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired ApplicationContext context;
    @Autowired RoomMemberRepository memberships;
    @Autowired GameSessionRepository games;
    @Autowired PlayerSessionRepository players;
    @Autowired GameQuestionRepository questions;
    @Autowired AnswerRepository answers;
    long author, user, otherUser, quiz, source, room, otherRoom, game, otherGame, player, otherPlayer, question, otherQuestion;
    String config;
    static final String IMAGE = "sha256:" + "a".repeat(64);

    @BeforeEach
    void fixture() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("quizz_task2_test");
        config = json.writeValueAsString(GameplayRulesSnapshot.forGame(10, 30000, 5000));
        author = user("author"); user = user("player"); otherUser = user("other");
        quiz = insert("INSERT INTO quiz(owner_user_id,title,visibility,created_at_ms) VALUES(?,'Original','PUBLIC',1000)", author);
        source = insert("INSERT INTO question(quiz_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,image_ref,created_at_ms) VALUES(?,1,'Original question','A','B','C','D','B',?,1000)", quiz, IMAGE);
        room = room("a"); otherRoom = room("b");
        for (long r : List.of(room, otherRoom)) {
            insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'SPECTATOR','JOINED',1000)", r, author);
            insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'PLAYER','JOINED',1000)", r, user);
            insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'PLAYER','JOINED',1000)", r, otherUser);
        }
        game = game(room); otherGame = game(otherRoom);
        for (long g : List.of(game, otherGame)) {
            long r = g == game ? room : otherRoom;
            insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'HOST','SPECTATOR','Author')", g, r, author);
            insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'MEMBER','PLAYER','Player')", g, r, user);
            insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'MEMBER','PLAYER','Other')", g, r, otherUser);
        }
        player = player(game, user); otherPlayer = player(otherGame, user);
        question = question(game, 1); otherQuestion = question(otherGame, 1);
    }

    long user(String prefix) {
        return insert("INSERT INTO app_user(username,password_hash,display_name,created_at_ms) VALUES(?,'test-hash','Test',1000)", prefix + UUID.randomUUID());
    }
    long room(String prefix) {
        return insert("INSERT INTO room(host_user_id,quiz_id,room_code,name,status,max_players,question_duration_ms,created_at_ms) VALUES(?,?,?,'Room','ACTIVE',10,30000,1000)", author, quiz, prefix + UUID.randomUUID().toString().substring(0, 8));
    }
    long game(long r) {
        return insert("INSERT INTO game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,question_count,config_snapshot,started_at_ms) VALUES(?,?,?,'Original','ACTIVE','DECISION',10,?,1000)", r, quiz, author, config);
    }
    long player(long g, long u) {
        return insert("INSERT INTO player_session(game_session_id,user_id,player_state,remaining_spins,remaining_spin_pool) VALUES(?,?,'PLAYING',1,'[\"BONUS\",\"SAFE\",\"BREAKTHROUGH\",\"SPEED\",\"DECISIVE\",\"HARDSHIP\"]')", g, u);
    }
    long question(long g, int index) {
        return insert("INSERT INTO game_question(game_session_id,source_question_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,image_ref,question_duration_ms,phase,opened_at_ms,deadline_at_ms) VALUES(?, ?,?,'Original question','A','B','C','D','B',?,30000,'QUESTION_OPEN',1000,31000)", g, source, index, IMAGE);
    }
    long accepted(long g, long p, long q) {
        return insert("INSERT INTO answer(game_session_id,player_session_id,game_question_id,answer_status,selected_option,received_at_ms,answer_time_ms) VALUES(?,?,?,'ACCEPTED_UNSCORED','B',2000,1000)", g, p, q);
    }
    long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        return key.getKey().longValue();
    }
    void rejected(int errorCode, Runnable command) {
        Throwable failure = catchThrowable(command::run);
        assertThat(failure).as("SQL must be rejected by MySQL").isNotNull();
        while (failure.getCause() != null) failure = failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class);
        assertThat(((SQLException) failure).getErrorCode()).isEqualTo(errorCode);
    }

    @Test void answerCannotCrossEitherGameBoundaryOrDuplicate() {
        rejected(1452, () -> accepted(game, player, otherQuestion));
        rejected(1452, () -> accepted(game, otherPlayer, question));
        accepted(game, player, question);
        rejected(1062, () -> accepted(game, player, question));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer WHERE game_session_id=?", Integer.class, game)).isEqualTo(1);
    }

    @Test void playerSessionMustMatchActualPlayerMembership() {
        rejected(1452, () -> player(game, author)); // Spectator has no matching PLAYER composite key.
        rejected(1644, () -> jdbc.update("UPDATE player_session SET participation='SPECTATOR' WHERE id=?", player));
        rejected(3819, () -> insert("INSERT INTO player_session(game_session_id,user_id,participation,player_state,remaining_spins,remaining_spin_pool) VALUES(?,?,'SPECTATOR','PLAYING',1,'[]')", game, author));
        rejected(1062, () -> player(game, user));
        long crossRoomUser = user("cross-room");
        insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'PLAYER','JOINED',1000)", otherRoom, crossRoomUser);
        rejected(1452, () -> insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'MEMBER','PLAYER','X')", game, otherRoom, crossRoomUser));
    }

    @Test void oneActiveGamePerUserIncludesSpectatorsAndRequiresReleaseBeforeFinish() {
        jdbc.update("INSERT INTO user_active_game(user_id,game_session_id) VALUES(?,?)", author, game);
        rejected(1062, () -> jdbc.update("INSERT INTO user_active_game(user_id,game_session_id) VALUES(?,?)", author, otherGame));
        rejected(1451, () -> jdbc.update("UPDATE game_session SET status='FINISHED',phase='FINISHED',end_reason='CANCELLED',finished_at_ms=4000 WHERE id=?", game));
        jdbc.update("DELETE FROM user_active_game WHERE game_session_id=?", game);
        jdbc.update("UPDATE game_session SET status='FINISHED',phase='FINISHED',end_reason='CANCELLED',finished_at_ms=4000 WHERE id=?", game);
        rejected(1452, () -> jdbc.update("INSERT INTO user_active_game(user_id,game_session_id) VALUES(?,?)", author, game));
        jdbc.update("INSERT INTO user_active_game(user_id,game_session_id) VALUES(?,?)", author, otherGame);
        rejected(1452, () -> jdbc.update("INSERT INTO user_active_game(user_id,game_session_id) VALUES(?,?)", user("outsider"), otherGame));
    }

    @Test void oneActiveSessionPerRoomButHistoricalSessionsCanCoexist() {
        rejected(1062, () -> game(room));
        jdbc.update("UPDATE game_session SET status='FINISHED',phase='FINISHED',end_reason='COMPLETED',finished_at_ms=4000 WHERE id=?", game);
        long next = game(room);
        assertThat(next).isNotEqualTo(game);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_session WHERE room_id=?", Integer.class, room)).isEqualTo(2);
    }

    @Test void questionOrderAndMembershipAreUnique() {
        rejected(1062, () -> question(game, 1));
        rejected(1062, () -> insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'PLAYER','JOINED',1000)", room, user));
        rejected(1062, () -> insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'MEMBER','PLAYER','Duplicate')", game, room, user));
        question(game, 2);
    }

    @Test void leaveThenRejoinReusesRowAndRetainsHistoricalParticipation() {
        RoomMember member = memberships.findByRoomIdAndUserId(room, user).orElseThrow();
        long id = member.getId();
        member.leave(2000);
        memberships.saveAndFlush(member);
        em.clear();
        member = memberships.findById(id).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MembershipStatus.LEFT);
        member.rejoin(3000);
        member.setParticipation(Participation.SPECTATOR); // current room choice; historical game remains PLAYER
        memberships.saveAndFlush(member);
        em.clear();
        assertThat(memberships.findByRoomIdAndUserId(room, user).orElseThrow().getId()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT participation FROM game_member WHERE game_session_id=? AND user_id=?", String.class, game, user)).isEqualTo("PLAYER");
        assertThat(jdbc.queryForObject("SELECT left_at_ms FROM room_member WHERE id=?", Long.class, id)).isNull();
        rejected(3819, () -> jdbc.update("UPDATE room_member SET status='LEFT',left_at_ms=NULL WHERE id=?", id));
    }

    @Test void cancelKeepsAcceptedAnswerUnscoredWithoutFalseCorrectOrWrong() {
        long id = accepted(game, player, question);
        jdbc.update("UPDATE game_session SET status='FINISHED',phase='FINISHED',end_reason='CANCELLED',finished_at_ms=4000 WHERE id=?", game);
        Answer a = answers.findById(id).orElseThrow();
        assertThat(a.getAnswerStatus()).isEqualTo(AnswerStatus.ACCEPTED_UNSCORED);
        assertThat(a.getReceivedAtMs()).isEqualTo(2000L);
        assertThat(a.getScoreDelta()).isNull();
        assertThat(a.getScoredAtMs()).isNull();
        assertThat(a.getResultSnapshot()).isNull();
        rejected(3819, () -> jdbc.update("UPDATE answer SET answer_status='CORRECT' WHERE id=?", id));
        rejected(3819, () -> jdbc.update("UPDATE answer SET score_delta=0 WHERE id=?", id));
    }

    @Test void noAnswerHasNullReceiptAndFullDurationWhileScoredAnswersHaveOutcomes() {
        long id = insert("INSERT INTO answer(game_session_id,player_session_id,game_question_id,answer_status,answer_time_ms,scored_at_ms,base_delta,score_delta,score_after,result_snapshot) VALUES(?,?,?,'NO_ANSWER',30000,31000,-1,-1,19,'{\"hasMomentumBefore\":false,\"hasRecoveryBefore\":false,\"hasMomentumAfter\":false,\"hasRecoveryAfter\":false}')", game, player, question);
        Answer a = answers.findById(id).orElseThrow();
        assertThat(a.getReceivedAtMs()).isNull();
        assertThat(a.getSelectedOption()).isNull();
        assertThat(a.getAnswerTimeMs()).isEqualTo(30000L);
        assertThat(a.getResultSnapshot()).containsEntry("hasRecoveryBefore", false);
        rejected(3819, () -> jdbc.update("UPDATE answer SET received_at_ms=30000 WHERE id=?", id));
        rejected(3819, () -> jdbc.update("UPDATE answer SET answer_time_ms=-1 WHERE id=?", id));
        long q2 = question(game, 2);
        long accepted = accepted(game, player, q2);
        rejected(3819, () -> jdbc.update("UPDATE answer SET result_snapshot='{}' WHERE id=?", id));
        jdbc.update("UPDATE answer SET answer_status='CORRECT',scored_at_ms=31000,base_delta=10,score_delta=13,score_after=33,result_snapshot='{\"hasMomentumBefore\":true,\"hasRecoveryBefore\":false,\"hasMomentumAfter\":false,\"hasRecoveryAfter\":false}' WHERE id=?", accepted);
        assertThat(answers.findById(accepted).orElseThrow().getScoreDelta()).isEqualTo(13);
    }

    @Test void sourceEditsAndSoftDeletionCannotRewriteOrCascadeHistory() {
        accepted(game, player, question);
        jdbc.update("UPDATE quiz SET title='Edited',deleted_at_ms=4000 WHERE id=?", quiz);
        jdbc.update("UPDATE question SET content='Edited question',correct_option='D',image_ref=?,deleted_at_ms=4000 WHERE id=?", "sha256:" + "b".repeat(64), source);
        assertThat(games.findById(game).orElseThrow().getQuizTitleSnapshot()).isEqualTo("Original");
        GameQuestion snapshot = questions.findById(question).orElseThrow();
        assertThat(snapshot.getContent()).isEqualTo("Original question");
        assertThat(snapshot.getCorrectOption()).isEqualTo(Option.B);
        assertThat(snapshot.getImageRef()).isEqualTo(IMAGE);
        rejected(1451, () -> jdbc.update("DELETE FROM quiz WHERE id=?", quiz));
        rejected(1451, () -> jdbc.update("DELETE FROM question WHERE id=?", source));
        rejected(1451, () -> jdbc.update("DELETE FROM game_session WHERE id=?", game));
        rejected(1451, () -> jdbc.update("DELETE FROM game_question WHERE id=?", question));
        rejected(1644, () -> jdbc.update("UPDATE game_question SET content='Rewrite' WHERE id=?", question));
        rejected(1644, () -> jdbc.update("UPDATE game_session SET config_snapshot=JSON_SET(config_snapshot,'$.questionDurationMs',40000) WHERE id=?", game));
        rejected(1644, () -> jdbc.update("UPDATE game_member SET role='HOST' WHERE game_session_id=? AND user_id=?", game, user));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND delete_rule='CASCADE'", Integer.class)).isZero();
    }

    @Test void zeroScoreLivesAndEliminationIsRecordedAndFrozen() {
        jdbc.update("UPDATE player_session SET score=0,has_momentum=1,has_recovery=1 WHERE id=?", player);
        PlayerSession p = players.findById(player).orElseThrow();
        assertThat(p.getPlayerState()).isEqualTo(PlayerState.PLAYING);
        assertThat(p.getHasMomentum()).isTrue();
        assertThat(p.getHasRecovery()).isTrue();
        rejected(3819, () -> jdbc.update("UPDATE player_session SET score=-1 WHERE id=?", player));
        jdbc.update("UPDATE player_session SET score=-1,player_state='ELIMINATED',eliminated_at_ms=4000,eliminated_question_index=1 WHERE id=?", player);
        em.clear();
        assertThat(players.findById(player).orElseThrow().getEliminatedAtMs()).isEqualTo(4000L);
        rejected(1644, () -> jdbc.update("UPDATE player_session SET score=-2 WHERE id=?", player));
        rejected(1644, () -> jdbc.update("UPDATE player_session SET eliminated_at_ms=5000 WHERE id=?", player));
        rejected(1644, () -> jdbc.update("UPDATE player_session SET score=0,player_state='PLAYING',eliminated_at_ms=NULL,eliminated_question_index=NULL WHERE id=?", player));
        jdbc.update("UPDATE player_session SET final_rank=1 WHERE id=?", player);
    }

    @Test void enumsNotNullRangesJsonAndImageReferencesAreEnforcedByMysql() {
        rejected(3819, () -> jdbc.update("UPDATE quiz SET visibility='public' WHERE id=?", quiz));
        rejected(1048, () -> jdbc.update("UPDATE question SET option_a=NULL WHERE id=?", source));
        rejected(3819, () -> jdbc.update("UPDATE question SET correct_option='E' WHERE id=?", source));
        rejected(3819, () -> jdbc.update("UPDATE question SET image_ref='mutable-url' WHERE id=?", source));
        rejected(3819, () -> jdbc.update("UPDATE room SET question_duration_ms=0 WHERE id=?", room));
        rejected(3819, () -> jdbc.update("UPDATE player_session SET remaining_spins=6 WHERE id=?", player));
        rejected(3819, () -> jdbc.update("UPDATE player_session SET remaining_spin_pool='[\"UNKNOWN\"]' WHERE id=?", player));
        rejected(3819, () -> jdbc.update("UPDATE player_session SET remaining_spin_pool='[\"SAFE\",\"SAFE\"]' WHERE id=?", player));
        rejected(3819, () -> jdbc.update("UPDATE player_session SET current_spin='HARDSHIP',star_selected=1,star_available=0 WHERE id=?", player));
        String missingStreaks = config.replace("\"streaks\":", "\"omittedStreaks\":");
        rejected(3819, () -> insert("INSERT INTO game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,end_reason,question_count,config_snapshot,started_at_ms,finished_at_ms) VALUES(?,?,?,'X','FINISHED','FINISHED','COMPLETED',10,?,1000,4000)", room, quiz, author, missingStreaks));
        rejected(1452, () -> insert("INSERT INTO quiz(owner_user_id,title,visibility,created_at_ms) VALUES(-1,'X','PUBLIC',1000)"));
    }

    @Test void allElevenRepositoriesReadAndJpaWritesRoundTripJsonAndRevision() {
        assertThat(em.getMetamodel().getEntities().stream().map(e -> e.getJavaType().getName())).containsExactlyInAnyOrder(
                "vn.edu.quiz.user.entity.UserAccount", "vn.edu.quiz.quiz.entity.Quiz", "vn.edu.quiz.quiz.entity.Question",
                "vn.edu.quiz.room.entity.Room", "vn.edu.quiz.room.entity.RoomMember",
                "vn.edu.quiz.game.entity.GameSession", "vn.edu.quiz.game.entity.GameMember", "vn.edu.quiz.game.entity.PlayerSession",
                "vn.edu.quiz.game.entity.GameQuestion", "vn.edu.quiz.game.entity.Answer", "vn.edu.quiz.game.entity.UserActiveGame");
        assertThat(context.getBeansOfType(JpaRepository.class)).hasSize(11);
        for (JpaRepository<?, ?> repo : context.getBeansOfType(JpaRepository.class).values()) repo.count();
        GameSession g = games.findById(game).orElseThrow();
        assertThat(g.getConfigSnapshot()).isEqualTo(GameplayRulesSnapshot.forGame(10, 30000, 5000));
        g.setPhase(Phase.QUESTION_OPEN);
        g.setCurrentQuestionIndex(1);
        games.saveAndFlush(g);
        PlayerSession p = players.findById(player).orElseThrow();
        p.setRemainingSpinPool(List.of(SpinEffect.SAFE, SpinEffect.SPEED));
        p.setHasRecovery(true);
        players.saveAndFlush(p);
        UserAccount account = new UserAccount();
        account.setUsername("jpa-" + UUID.randomUUID());
        account.setPasswordHash("test-hash");
        account.setDisplayName("Jpa");
        account.setCreatedAtMs(1000L);
        UserRepository users = context.getBean(UserRepository.class);
        users.saveAndFlush(account);
        em.clear();
        assertThat(users.findByUsername(account.getUsername())).isPresent();
        assertThat(games.findById(game).orElseThrow().getRevision()).isEqualTo(1L);
        assertThat(players.findById(player).orElseThrow().getRemainingSpinPool()).containsExactly(SpinEffect.SAFE, SpinEffect.SPEED);
        assertThat(players.findById(player).orElseThrow().getHasRecovery()).isTrue();
    }
}
