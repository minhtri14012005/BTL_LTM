-- MySQL 8.0.16+ is required for enforced CHECK constraints.
-- No cascading deletes: snapshots/results retain their references.
CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT NULL,
    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT ck_user_values CHECK (CHAR_LENGTH(TRIM(username)) > 0 AND CHAR_LENGTH(TRIM(password_hash)) > 0
        AND CHAR_LENGTH(TRIM(display_name)) > 0 AND created_at_ms >= 0 AND (deleted_at_ms IS NULL OR deleted_at_ms >= created_at_ms))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    visibility VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_quiz_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
    CONSTRAINT ck_quiz_values CHECK (visibility IN ('PUBLIC','PRIVATE') AND CHAR_LENGTH(TRIM(title)) > 0
        AND revision >= 0 AND created_at_ms >= 0 AND (deleted_at_ms IS NULL OR deleted_at_ms >= created_at_ms))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE question (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    order_index INT NOT NULL,
    content TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_option VARCHAR(1) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    image_ref VARCHAR(71) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT NULL,
    CONSTRAINT uq_question_order UNIQUE (quiz_id, order_index),
    CONSTRAINT fk_question_quiz FOREIGN KEY (quiz_id) REFERENCES quiz(id),
    CONSTRAINT ck_question_values CHECK (order_index > 0 AND correct_option IN ('A','B','C','D')
        AND CHAR_LENGTH(TRIM(content)) > 0 AND CHAR_LENGTH(TRIM(option_a)) > 0 AND CHAR_LENGTH(TRIM(option_b)) > 0
        AND CHAR_LENGTH(TRIM(option_c)) > 0 AND CHAR_LENGTH(TRIM(option_d)) > 0 AND created_at_ms >= 0
        AND (deleted_at_ms IS NULL OR deleted_at_ms >= created_at_ms)),
    CONSTRAINT ck_question_image CHECK (image_ref IS NULL OR REGEXP_LIKE(image_ref, '^sha256:[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    host_user_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    room_code VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    max_players INT NOT NULL,
    question_duration_ms BIGINT NOT NULL,
    decision_duration_ms BIGINT NOT NULL DEFAULT 5000,
    created_at_ms BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_room_code UNIQUE (room_code),
    CONSTRAINT fk_room_host FOREIGN KEY (host_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_room_quiz FOREIGN KEY (quiz_id) REFERENCES quiz(id),
    CONSTRAINT ck_room_values CHECK (status IN ('DRAFT','WAITING','ACTIVE','CLOSED') AND max_players >= 3
        AND question_duration_ms > 0 AND decision_duration_ms > 0 AND revision >= 0 AND created_at_ms >= 0
        AND CHAR_LENGTH(TRIM(name)) > 0 AND CHAR_LENGTH(room_code) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE room_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    participation VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    joined_at_ms BIGINT NOT NULL,
    left_at_ms BIGINT NULL,
    CONSTRAINT uq_room_member UNIQUE (room_id, user_id),
    CONSTRAINT fk_room_member_room FOREIGN KEY (room_id) REFERENCES room(id),
    CONSTRAINT fk_room_member_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT ck_room_member_values CHECK (participation IN ('PLAYER','SPECTATOR') AND status IN ('JOINED','LEFT')
        AND joined_at_ms >= 0 AND ((status = 'JOINED' AND left_at_ms IS NULL)
        OR (status = 'LEFT' AND left_at_ms IS NOT NULL AND left_at_ms >= joined_at_ms)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    quiz_author_user_id BIGINT NOT NULL,
    quiz_title_snapshot VARCHAR(200) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    phase VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    end_reason VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    question_count INT NOT NULL,
    current_question_index INT NOT NULL DEFAULT 0,
    config_snapshot JSON NOT NULL,
    started_at_ms BIGINT NOT NULL,
    finished_at_ms BIGINT NULL,
    phase_opened_at_ms BIGINT NULL,
    phase_deadline_at_ms BIGINT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    active_room_key BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN room_id ELSE NULL END) STORED,
    CONSTRAINT uq_game_room_id UNIQUE (id, room_id),
    CONSTRAINT uq_game_status UNIQUE (id, status),
    CONSTRAINT uq_game_active_room UNIQUE (active_room_key),
    CONSTRAINT fk_game_room FOREIGN KEY (room_id) REFERENCES room(id),
    CONSTRAINT fk_game_quiz FOREIGN KEY (quiz_id) REFERENCES quiz(id),
    CONSTRAINT fk_game_author FOREIGN KEY (quiz_author_user_id) REFERENCES app_user(id),
    CONSTRAINT ck_game_values CHECK (status IN ('ACTIVE','FINISHED')
        AND phase IN ('DECISION','QUESTION_OPEN','QUESTION_CLOSED','SCORING','RESULT','FINISHED')
        AND question_count BETWEEN 10 AND 50 AND current_question_index BETWEEN 0 AND question_count
        AND revision >= 0 AND started_at_ms >= 0 AND CHAR_LENGTH(TRIM(quiz_title_snapshot)) > 0
        AND (phase_opened_at_ms IS NULL OR phase_opened_at_ms >= started_at_ms)
        AND (phase_deadline_at_ms IS NULL OR (phase_opened_at_ms IS NOT NULL AND phase_deadline_at_ms >= phase_opened_at_ms))),
    CONSTRAINT ck_game_end CHECK ((status = 'ACTIVE' AND phase <> 'FINISHED' AND end_reason IS NULL AND finished_at_ms IS NULL)
        OR (status = 'FINISHED' AND phase = 'FINISHED' AND finished_at_ms IS NOT NULL AND finished_at_ms >= started_at_ms
        AND end_reason IS NOT NULL AND end_reason IN ('COMPLETED','ONE_SURVIVOR','ALL_ELIMINATED','CANCELLED','SERVER_INTERRUPTED'))),
    CONSTRAINT ck_game_config CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schemaVersion","questionCount","questionDurationMs","decisionDurationMs","initialScore","spinCredits","starCredits","normal","starOnly","spins","streaks","spinWithoutReplacement","spinBeforeStarOnly","eliminationRule","rankingRule","endReasonPriority","allEliminatedRankOneAreWinners"],"properties":{"schemaVersion":{"enum":[1]},"questionCount":{"type":"integer","minimum":10,"maximum":50},"questionDurationMs":{"type":"integer","minimum":1},"decisionDurationMs":{"type":"integer","minimum":1},"initialScore":{"enum":[20]},"spinCredits":{"type":"integer","minimum":1,"maximum":5},"starCredits":{"enum":[1]},"normal":{"type":"object"},"starOnly":{"type":"object"},"spins":{"type":"object","required":["BONUS","SAFE","BREAKTHROUGH","SPEED","DECISIVE","HARDSHIP"]},"streaks":{"type":"object","required":["threshold","momentumBonus","recoveryReduction","recoveryPenaltyCap","recoveryUsesBasePenalty","recoveryConsumedWhenFinalPenaltyZero","newEffectAppliesOnTrigger","effectsStack","noAnswerResetsBothStreaks","eliminationBeforeStreakUpdate"],"properties":{"threshold":{"enum":[5]},"momentumBonus":{"enum":[3]},"recoveryReduction":{"enum":[3]},"recoveryPenaltyCap":{"enum":[0]},"recoveryUsesBasePenalty":{"enum":[true]},"recoveryConsumedWhenFinalPenaltyZero":{"enum":[true]},"newEffectAppliesOnTrigger":{"enum":[false]},"effectsStack":{"enum":[false]},"noAnswerResetsBothStreaks":{"enum":[true]},"eliminationBeforeStreakUpdate":{"enum":[true]}}},"spinWithoutReplacement":{"enum":[true]},"spinBeforeStarOnly":{"enum":[true]},"eliminationRule":{"enum":["SCORE_LT_ZERO"]},"rankingRule":{"enum":["SCORE_DESC_TIME_ASC_COMPETITION"]},"endReasonPriority":{"enum":["ALL_ELIMINATED_OR_ONE_SURVIVOR_BEFORE_COMPLETED"]},"allEliminatedRankOneAreWinners":{"enum":[true]}}}', config_snapshot)
        AND JSON_EXTRACT(config_snapshot, '$.questionCount') = question_count
        AND JSON_EXTRACT(config_snapshot, '$.spinCredits') = FLOOR(question_count / 10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game_session_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    participation VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name_snapshot VARCHAR(100) NOT NULL,
    CONSTRAINT uq_game_member UNIQUE (game_session_id, user_id),
    CONSTRAINT uq_game_member_participation UNIQUE (game_session_id, user_id, participation),
    CONSTRAINT fk_member_game_room FOREIGN KEY (game_session_id, room_id) REFERENCES game_session(id, room_id),
    CONSTRAINT fk_member_room_user FOREIGN KEY (room_id, user_id) REFERENCES room_member(room_id, user_id),
    CONSTRAINT ck_game_member_values CHECK (role IN ('HOST','MEMBER') AND participation IN ('PLAYER','SPECTATOR')
        AND CHAR_LENGTH(TRIM(display_name_snapshot)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE player_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game_session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    participation VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PLAYER',
    player_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    score INT NOT NULL DEFAULT 20,
    total_answer_time_ms BIGINT NOT NULL DEFAULT 0,
    win_streak INT NOT NULL DEFAULT 0,
    lose_streak INT NOT NULL DEFAULT 0,
    has_momentum BOOLEAN NOT NULL DEFAULT FALSE,
    has_recovery BOOLEAN NOT NULL DEFAULT FALSE,
    remaining_spins INT NOT NULL,
    star_available BOOLEAN NOT NULL DEFAULT TRUE,
    remaining_spin_pool JSON NOT NULL,
    current_spin VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    star_selected BOOLEAN NOT NULL DEFAULT FALSE,
    eliminated_at_ms BIGINT NULL,
    eliminated_question_index INT NULL,
    final_rank INT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_player_game_user UNIQUE (game_session_id, user_id),
    CONSTRAINT uq_player_game_id UNIQUE (game_session_id, id),
    CONSTRAINT fk_player_participant FOREIGN KEY (game_session_id, user_id, participation)
        REFERENCES game_member(game_session_id, user_id, participation),
    CONSTRAINT ck_player_values CHECK (participation = 'PLAYER' AND player_state IN ('PLAYING','ELIMINATED')
        AND total_answer_time_ms >= 0 AND win_streak BETWEEN 0 AND 4 AND lose_streak BETWEEN 0 AND 4
        AND remaining_spins BETWEEN 0 AND 5 AND revision >= 0 AND (final_rank IS NULL OR final_rank > 0)
        AND has_momentum IN (0,1) AND has_recovery IN (0,1) AND star_available IN (0,1) AND star_selected IN (0,1)
        AND JSON_SCHEMA_VALID('{"type":"array","uniqueItems":true,"maxItems":6,"items":{"enum":["BONUS","SAFE","BREAKTHROUGH","SPEED","DECISIVE","HARDSHIP"]}}', remaining_spin_pool)
        AND (current_spin IS NULL OR current_spin IN ('BONUS','SAFE','BREAKTHROUGH','SPEED','DECISIVE','HARDSHIP'))),
    CONSTRAINT ck_player_elimination CHECK ((player_state = 'PLAYING' AND score >= 0
        AND eliminated_at_ms IS NULL AND eliminated_question_index IS NULL)
        OR (player_state = 'ELIMINATED' AND score < 0 AND eliminated_at_ms IS NOT NULL AND eliminated_at_ms >= 0
        AND eliminated_question_index IS NOT NULL AND eliminated_question_index BETWEEN 1 AND 50)),
    CONSTRAINT ck_player_star CHECK (star_selected = 0 OR (star_available = 0 AND (current_spin IS NULL OR current_spin <> 'HARDSHIP')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_question (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game_session_id BIGINT NOT NULL,
    source_question_id BIGINT NOT NULL,
    order_index INT NOT NULL,
    content TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_option VARCHAR(1) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    image_ref VARCHAR(71) CHARACTER SET ascii COLLATE ascii_bin NULL,
    question_duration_ms BIGINT NOT NULL,
    phase VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    opened_at_ms BIGINT NULL,
    deadline_at_ms BIGINT NULL,
    scored_at_ms BIGINT NULL,
    CONSTRAINT uq_game_question_order UNIQUE (game_session_id, order_index),
    CONSTRAINT uq_game_question_game_id UNIQUE (game_session_id, id),
    CONSTRAINT fk_question_game FOREIGN KEY (game_session_id) REFERENCES game_session(id),
    CONSTRAINT fk_question_source FOREIGN KEY (source_question_id) REFERENCES question(id),
    CONSTRAINT ck_game_question_values CHECK (order_index BETWEEN 1 AND 50 AND question_duration_ms > 0
        AND correct_option IN ('A','B','C','D') AND phase IN ('DECISION','QUESTION_OPEN','QUESTION_CLOSED','SCORING','RESULT')
        AND CHAR_LENGTH(TRIM(content)) > 0 AND CHAR_LENGTH(TRIM(option_a)) > 0 AND CHAR_LENGTH(TRIM(option_b)) > 0
        AND CHAR_LENGTH(TRIM(option_c)) > 0 AND CHAR_LENGTH(TRIM(option_d)) > 0
        AND (opened_at_ms IS NULL OR opened_at_ms >= 0)
        AND (deadline_at_ms IS NULL OR (opened_at_ms IS NOT NULL AND deadline_at_ms > opened_at_ms))
        AND (scored_at_ms IS NULL OR (opened_at_ms IS NOT NULL AND scored_at_ms >= opened_at_ms))),
    CONSTRAINT ck_game_question_image CHECK (image_ref IS NULL OR REGEXP_LIKE(image_ref, '^sha256:[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE answer (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game_session_id BIGINT NOT NULL,
    player_session_id BIGINT NOT NULL,
    game_question_id BIGINT NOT NULL,
    answer_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    selected_option VARCHAR(1) CHARACTER SET ascii COLLATE ascii_bin NULL,
    received_at_ms BIGINT NULL,
    answer_time_ms BIGINT NOT NULL,
    scored_at_ms BIGINT NULL,
    base_delta INT NULL,
    score_delta INT NULL,
    score_after INT NULL,
    result_snapshot JSON NULL,
    CONSTRAINT uq_answer_player_question UNIQUE (player_session_id, game_question_id),
    CONSTRAINT fk_answer_player_game FOREIGN KEY (game_session_id, player_session_id) REFERENCES player_session(game_session_id, id),
    CONSTRAINT fk_answer_question_game FOREIGN KEY (game_session_id, game_question_id) REFERENCES game_question(game_session_id, id),
    CONSTRAINT ck_answer_values CHECK (answer_status IN ('ACCEPTED_UNSCORED','CORRECT','WRONG','NO_ANSWER') AND answer_time_ms >= 0
        AND (selected_option IS NULL OR selected_option IN ('A','B','C','D'))
        AND (received_at_ms IS NULL OR received_at_ms >= 0) AND (scored_at_ms IS NULL OR scored_at_ms >= 0)
        AND (result_snapshot IS NULL OR JSON_TYPE(result_snapshot) = 'OBJECT')),
    CONSTRAINT ck_answer_received CHECK ((answer_status = 'NO_ANSWER' AND selected_option IS NULL AND received_at_ms IS NULL)
        OR (answer_status <> 'NO_ANSWER' AND selected_option IS NOT NULL AND received_at_ms IS NOT NULL)),
    CONSTRAINT ck_answer_scored CHECK ((answer_status = 'ACCEPTED_UNSCORED' AND scored_at_ms IS NULL AND base_delta IS NULL
        AND score_delta IS NULL AND score_after IS NULL AND result_snapshot IS NULL)
        OR (answer_status <> 'ACCEPTED_UNSCORED' AND scored_at_ms IS NOT NULL AND base_delta IS NOT NULL
        AND score_delta IS NOT NULL AND score_after IS NOT NULL AND result_snapshot IS NOT NULL
        AND (received_at_ms IS NULL OR scored_at_ms >= received_at_ms)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_active_game (
    user_id BIGINT NOT NULL PRIMARY KEY,
    game_session_id BIGINT NOT NULL,
    game_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_active_member FOREIGN KEY (game_session_id, user_id) REFERENCES game_member(game_session_id, user_id),
    CONSTRAINT fk_active_status FOREIGN KEY (game_session_id, game_status) REFERENCES game_session(id, status),
    CONSTRAINT ck_active_status CHECK (game_status = 'ACTIVE')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Freeze content/config independently of subsequent edits to the source Quiz.
DELIMITER $$
CREATE TRIGGER trg_game_snapshot_immutable BEFORE UPDATE ON game_session FOR EACH ROW
BEGIN
    IF NOT (OLD.room_id <=> NEW.room_id) OR NOT (OLD.quiz_id <=> NEW.quiz_id)
        OR NOT (OLD.quiz_author_user_id <=> NEW.quiz_author_user_id)
        OR NOT (OLD.quiz_title_snapshot <=> NEW.quiz_title_snapshot)
        OR NOT (OLD.question_count <=> NEW.question_count) OR NOT (OLD.config_snapshot <=> NEW.config_snapshot) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Game content/config snapshot is immutable';
    END IF;
END$$
CREATE TRIGGER trg_game_question_immutable BEFORE UPDATE ON game_question FOR EACH ROW
BEGIN
    IF NOT (OLD.game_session_id <=> NEW.game_session_id) OR NOT (OLD.source_question_id <=> NEW.source_question_id)
        OR NOT (OLD.order_index <=> NEW.order_index) OR NOT (OLD.content <=> NEW.content)
        OR NOT (OLD.option_a <=> NEW.option_a) OR NOT (OLD.option_b <=> NEW.option_b)
        OR NOT (OLD.option_c <=> NEW.option_c) OR NOT (OLD.option_d <=> NEW.option_d)
        OR NOT (OLD.correct_option <=> NEW.correct_option) OR NOT (OLD.image_ref <=> NEW.image_ref)
        OR NOT (OLD.question_duration_ms <=> NEW.question_duration_ms) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Game question snapshot is immutable';
    END IF;
END$$
CREATE TRIGGER trg_member_snapshot_immutable BEFORE UPDATE ON game_member FOR EACH ROW
BEGIN
    IF NOT (OLD.game_session_id <=> NEW.game_session_id) OR NOT (OLD.room_id <=> NEW.room_id)
        OR NOT (OLD.user_id <=> NEW.user_id) OR NOT (OLD.role <=> NEW.role)
        OR NOT (OLD.participation <=> NEW.participation)
        OR NOT (OLD.display_name_snapshot <=> NEW.display_name_snapshot) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Game membership snapshot is immutable';
    END IF;
END$$
CREATE TRIGGER trg_player_elimination_retained BEFORE UPDATE ON player_session FOR EACH ROW
BEGIN
    IF NOT (OLD.game_session_id <=> NEW.game_session_id) OR NOT (OLD.user_id <=> NEW.user_id)
        OR NOT (OLD.participation <=> NEW.participation)
        OR (OLD.player_state = 'ELIMINATED' AND (NEW.player_state <> 'ELIMINATED'
            OR NOT (OLD.eliminated_at_ms <=> NEW.eliminated_at_ms)
            OR NOT (OLD.eliminated_question_index <=> NEW.eliminated_question_index)
            OR OLD.score <> NEW.score)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Player identity/elimination history cannot change';
    END IF;
END$$
DELIMITER ;
