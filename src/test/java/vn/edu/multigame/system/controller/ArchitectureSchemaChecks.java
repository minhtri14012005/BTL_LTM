package vn.edu.multigame.system.controller;

import java.sql.SQLException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

final class ArchitectureSchemaChecks {
    static void structure(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()",Integer.class)).isEqualTo(232);
        assertThat(jdbc.queryForList("SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()",String.class))
            .doesNotContain("question.schema_version","lesson_game.points_per_correct","lesson_action_window.position",
                "account_effect_intent.effect_type","account_effect_intent.target_key","account_effect_intent.ban_version","account_effect_intent.effective_at_ms");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='question' AND INDEX_NAME='uq_source_mode'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lesson_assignment_grade' AND INDEX_NAME='submission_id'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME IN ('fk_window_game_lesson','fk_input_game_lesson','fk_wheel_game_lesson','fk_grade_submission_owner')",Integer.class)).isEqualTo(4);
    }

    static void ownership(ConfigurableApplicationContext context,JdbcTemplate jdbc) {
        // Real domain fixtures already exist. Every rejected statement must leave them unchanged.
        long window=jdbc.queryForObject("SELECT lesson_id FROM lesson_action_window ORDER BY lesson_id LIMIT 1",Long.class);
        long otherGame=jdbc.queryForObject("SELECT id FROM lesson_game WHERE lesson_id<>? ORDER BY id LIMIT 1",Long.class,window);
        var before=jdbc.queryForMap("SELECT * FROM lesson_action_window WHERE lesson_id=?",window);
        foreignKeyFailure(()->jdbc.update("UPDATE lesson_action_window SET game_id=? WHERE lesson_id=?",otherGame,window));
        assertThat(jdbc.queryForMap("SELECT * FROM lesson_action_window WHERE lesson_id=?",window)).isEqualTo(before);
        var persisted=context.getBean(vn.edu.multigame.lesson.repository.LessonRealtimeStore.class).window(window).orElseThrow();
        assertThat(persisted.position()).isEqualTo(jdbc.queryForObject("SELECT position FROM lesson_game WHERE id=?",Integer.class,persisted.gameId()));

        var input=jdbc.queryForMap("SELECT id,lesson_id FROM lesson_action_input ORDER BY id LIMIT 1");
        long otherLesson=jdbc.queryForObject("SELECT id FROM lesson WHERE id<>? AND deleted_at_ms IS NULL ORDER BY id LIMIT 1",Long.class,input.get("lesson_id"));
        foreignKeyFailure(()->jdbc.update("UPDATE lesson_action_input SET lesson_id=? WHERE id=?",otherLesson,input.get("id")));

        var grade=jdbc.queryForMap("SELECT game_id,student_id,submission_id FROM lesson_assignment_grade ORDER BY game_id,student_id LIMIT 1");
        long foreignSubmission=jdbc.queryForObject("SELECT id FROM lesson_action_input WHERE game_id<>? OR student_id<>? ORDER BY id LIMIT 1",Long.class,grade.get("game_id"),grade.get("student_id"));
        foreignKeyFailure(()->jdbc.update("UPDATE lesson_assignment_grade SET submission_id=? WHERE game_id=? AND student_id=?",foreignSubmission,grade.get("game_id"),grade.get("student_id")));

        long wheelLesson=jdbc.queryForObject("SELECT lesson_id FROM lesson_wheel_round ORDER BY lesson_id LIMIT 1",Long.class);
        long foreignWheelGame=jdbc.queryForObject("SELECT id FROM lesson_game WHERE lesson_id<>? AND mode='QUIZ' ORDER BY id LIMIT 1",Long.class,wheelLesson);
        foreignKeyFailure(()->jdbc.update("UPDATE lesson_wheel_round SET game_id=? WHERE lesson_id=?",foreignWheelGame,wheelLesson));
        System.out.println("Architecture ownership constraints and derived window position PASS");
    }

    private static void foreignKeyFailure(Runnable mutation) {
        var failure=catchThrowable(mutation::run);
        assertThat(failure).isNotNull();
        Throwable root=failure;while(root.getCause()!=null)root=root.getCause();
        assertThat(root).isInstanceOf(SQLException.class);
        assertThat(((SQLException)root).getErrorCode()).isEqualTo(1452);
    }
}
