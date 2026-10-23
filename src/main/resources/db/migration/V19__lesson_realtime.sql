CREATE TABLE lesson_command_receipt (
 actor_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 lesson_id BIGINT NOT NULL,
 fingerprint CHAR(64) NOT NULL,
 command VARCHAR(40) NOT NULL,
 ingress_sequence BIGINT NOT NULL,
 received_at_ms BIGINT NOT NULL,
 PRIMARY KEY(actor_id,request_id),
 CONSTRAINT fk_lesson_receipt_actor FOREIGN KEY(actor_id) REFERENCES app_user(id),
 CONSTRAINT fk_lesson_receipt_lesson FOREIGN KEY(lesson_id) REFERENCES lesson(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_action_window (
 lesson_id BIGINT NOT NULL PRIMARY KEY,
 game_id BIGINT NOT NULL,
 position INT NOT NULL,
 token BIGINT NOT NULL,
 question_index INT NOT NULL,
 status VARCHAR(16) NOT NULL,
 opened_at_ms BIGINT NOT NULL,
 deadline_at_ms BIGINT NOT NULL,
 CONSTRAINT fk_window_lesson FOREIGN KEY(lesson_id) REFERENCES lesson(id),
 CONSTRAINT fk_window_game FOREIGN KEY(game_id) REFERENCES lesson_game(id),
 CONSTRAINT ck_lesson_window CHECK(position BETWEEN 1 AND 3 AND token>0 AND question_index>0
   AND status IN ('OPEN','CLOSED','CANCELLED') AND deadline_at_ms>opened_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_action_input (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 game_id BIGINT NOT NULL,
 window_token BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 submission_key VARCHAR(64) NOT NULL,
 input_value TEXT NOT NULL,
 received_at_ms BIGINT NOT NULL,
 ingress_sequence BIGINT NOT NULL,
 CONSTRAINT uq_lesson_action_request UNIQUE(student_id,request_id),
 CONSTRAINT uq_lesson_action_slot UNIQUE(game_id,window_token,submission_key),
 CONSTRAINT fk_lesson_input_game FOREIGN KEY(game_id,student_id) REFERENCES lesson_game_participant(game_id,student_id),
 CONSTRAINT fk_lesson_input_lesson FOREIGN KEY(lesson_id) REFERENCES lesson(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
