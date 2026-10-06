package vn.edu.quiz.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.quiz.auth.security.AuthPrincipal;
import vn.edu.quiz.auth.security.PermissionPolicy.*;
import vn.edu.quiz.game.dto.GameplayRulesSnapshot;
import vn.edu.quiz.user.repository.UserRepository;
import static org.assertj.core.api.Assertions.*;

/** MySQL repository/guard adapter evidence; fixtures and Authentication are synthetic, no lifecycle engine. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000"
})
@ActiveProfiles("mysql")
@Transactional
class AuthorizationRepositoryIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired AuthorizationService permissions;
    @Autowired UserRepository users;
    long author, host, outsider, quiz, room, game;
    @BeforeEach void fixture() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("quizz_task2_test");
        author = user(); host = user(); outsider = user();
        quiz = insert("INSERT INTO quiz(owner_user_id,title,visibility,created_at_ms) VALUES(?,'Q','PUBLIC',1000)", author);
        room = insert("INSERT INTO room(host_user_id,quiz_id,room_code,name,status,max_players,question_duration_ms,created_at_ms) VALUES(?,?,?,'Room','WAITING',10,30000,1000)", host, quiz, UUID.randomUUID().toString().substring(0, 8));
        insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'PLAYER','JOINED',1000)", room, host);
        insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'SPECTATOR','JOINED',1000)", room, author);
        game = insert("INSERT INTO game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,question_count,config_snapshot,started_at_ms) VALUES(?,?,?,'Q','ACTIVE','QUESTION_OPEN',10,?,1000)", room, quiz, author, json.writeValueAsString(GameplayRulesSnapshot.forGame(10, 30000, 5000)));
        insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'HOST','PLAYER','Host')", game, room, host);
        insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'MEMBER','SPECTATOR','Author')", game, room, author);
        insert("INSERT INTO player_session(game_session_id,user_id,player_state,remaining_spins,remaining_spin_pool) VALUES(?,?,'PLAYING',1,'[\"BONUS\",\"SAFE\",\"BREAKTHROUGH\",\"SPEED\",\"DECISIVE\",\"HARDSHIP\"]')", game, host);
    }
    @Test void authenticatedOutsiderAndNonHostAreDeniedByStoredMembership() {
        assertThatThrownBy(() -> permissions.requireRoom(auth(outsider), room, RoomAction.VIEW)).isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> permissions.requireGame(auth(outsider), game, GameAction.VIEW)).isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> permissions.requireRoom(auth(author), room, RoomAction.START)).isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> permissions.requireGame(auth(author), game, GameAction.CANCEL)).isInstanceOf(AuthFailure.class);
        permissions.requireRoom(auth(host), room, RoomAction.START);
        permissions.requireGame(auth(host), game, GameAction.ANSWER);
    }
    @Test void storedEliminationRemovesPlayPermissionsAndRetainsHostCancel() {
        jdbc.update("UPDATE player_session SET player_state='ELIMINATED',score=-1,eliminated_at_ms=2000,eliminated_question_index=1 WHERE game_session_id=? AND user_id=?", game, host);
        em.clear();
        permissions.requireGame(auth(host), game, GameAction.CANCEL);
        permissions.requireGame(auth(host), game, GameAction.VIEW);
        for (GameAction action : new GameAction[]{GameAction.ANSWER, GameAction.SPIN, GameAction.STAR}) {
            assertThatThrownBy(() -> permissions.requireGame(auth(host), game, action)).isInstanceOfSatisfying(AuthFailure.class,
                    failure -> assertThat(failure.code()).isEqualTo("FORBIDDEN"));
        }
    }
    @Test void leftMembershipDeletedOrForgedPrincipalCannotPassGuard() {
        jdbc.update("UPDATE room_member SET status='LEFT',left_at_ms=2000 WHERE room_id=? AND user_id=?", room, host);
        em.clear();
        assertThatThrownBy(() -> permissions.requireRoom(auth(host), room, RoomAction.VIEW)).isInstanceOf(AuthFailure.class);
        Authentication previouslyValid = auth(host);
        jdbc.update("UPDATE app_user SET deleted_at_ms=2000 WHERE id=?", host); em.clear();
        assertThatThrownBy(() -> permissions.requireGame(previouslyValid, game, GameAction.CANCEL)).isInstanceOfSatisfying(AuthFailure.class,
                failure -> assertThat(failure.code()).isEqualTo("UNAUTHENTICATED"));
        var forged = UsernamePasswordAuthenticationToken.authenticated("client-user-id", null, java.util.List.of());
        assertThatThrownBy(() -> permissions.requireGame(forged, game, GameAction.VIEW)).isInstanceOf(AuthFailure.class);
        assertThatThrownBy(() -> permissions.requireRoom(null, room, RoomAction.VIEW)).isInstanceOf(AuthFailure.class);
    }
    Authentication auth(long id) {
        var principal = new AuthPrincipal(users.findById(id).orElseThrow()); principal.eraseCredentials();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
    long user() { return insert("INSERT INTO app_user(username,password_hash,display_name,created_at_ms) VALUES(?,'fixture-not-login','User',1000)", "guard_" + UUID.randomUUID()); }
    long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
