CREATE TABLE lesson_quiz_question (
 game_id BIGINT NOT NULL,question_index INT NOT NULL,
 PRIMARY KEY(game_id,question_index),FOREIGN KEY(game_id) REFERENCES lesson_game(id)
) ENGINE=InnoDB;
CREATE TABLE lesson_quiz_answer (
 game_id BIGINT NOT NULL,question_index INT NOT NULL,student_id BIGINT NOT NULL,
 answer_value CHAR(1) NOT NULL,is_correct BOOLEAN NOT NULL,received_at_ms BIGINT NOT NULL,
 PRIMARY KEY(game_id,question_index,student_id),
 FOREIGN KEY(game_id,question_index) REFERENCES lesson_quiz_question(game_id,question_index),
 FOREIGN KEY(game_id,student_id) REFERENCES lesson_game_participant(game_id,student_id)
) ENGINE=InnoDB;
DROP TRIGGER trg_game_score_frozen;
DELIMITER $$
CREATE TRIGGER trg_game_score_frozen BEFORE UPDATE ON lesson_game_score FOR EACH ROW
BEGIN
 DECLARE game_mode VARCHAR(32); DECLARE game_status VARCHAR(16); DECLARE max_points DECIMAL(5,2);
 SELECT mode,status,budget INTO game_mode,game_status,max_points FROM lesson_game WHERE id=OLD.game_id;
 IF NEW.game_id<>OLD.game_id OR NEW.student_id<>OLD.student_id OR game_mode NOT IN ('LUCKY_WHEEL','QUIZ') OR game_status<>'RUNNING' OR NEW.score<0 OR NEW.score>max_points THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GAME_SCORE_FROZEN';
 END IF;
END$$
DELIMITER ;
