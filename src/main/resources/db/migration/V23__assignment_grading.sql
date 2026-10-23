CREATE TABLE lesson_assignment_grade (
 game_id BIGINT NOT NULL, student_id BIGINT NOT NULL, submission_id BIGINT NOT NULL,
 score DECIMAL(5,2) NOT NULL, graded_at_ms BIGINT NOT NULL,
 PRIMARY KEY(game_id,student_id),
 FOREIGN KEY(game_id,student_id) REFERENCES lesson_game_participant(game_id,student_id),
 FOREIGN KEY(submission_id) REFERENCES lesson_action_input(id),
 CHECK(score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE lesson_assignment_deadline (
 game_id BIGINT PRIMARY KEY,deadline_at_ms BIGINT NOT NULL,
 FOREIGN KEY(game_id) REFERENCES lesson_game(id), CHECK(deadline_at_ms>0)
) ENGINE=InnoDB;
