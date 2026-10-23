CREATE TABLE class_wheel_config (
 course_class_id BIGINT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0, cells JSON NOT NULL,
 FOREIGN KEY(course_class_id) REFERENCES course_class(id)
) ENGINE=InnoDB;
CREATE TABLE lesson_wheel_round (
 lesson_id BIGINT PRIMARY KEY, game_id BIGINT NOT NULL UNIQUE, state JSON NOT NULL,
 FOREIGN KEY(lesson_id) REFERENCES lesson(id), FOREIGN KEY(game_id) REFERENCES lesson_game(id)
) ENGINE=InnoDB;
CREATE TABLE lesson_wheel_receipt (
 actor_id BIGINT NOT NULL, request_id CHAR(36) NOT NULL, fingerprint CHAR(64) NOT NULL,
 PRIMARY KEY(actor_id,request_id), FOREIGN KEY(actor_id) REFERENCES app_user(id)
) ENGINE=InnoDB;

DROP TRIGGER trg_game_score_frozen;
DELIMITER $$
CREATE TRIGGER trg_game_score_frozen BEFORE UPDATE ON lesson_game_score FOR EACH ROW
BEGIN
 DECLARE game_mode VARCHAR(32);
 DECLARE game_status VARCHAR(16);
 DECLARE max_points DECIMAL(5,2);
 SELECT mode,status,budget INTO game_mode,game_status,max_points FROM lesson_game WHERE id=OLD.game_id;
 IF NEW.game_id<>OLD.game_id OR NEW.student_id<>OLD.student_id OR game_mode<>'LUCKY_WHEEL'
    OR game_status<>'RUNNING' OR NEW.score<0 OR NEW.score>max_points THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GAME_SCORE_FROZEN';
 END IF;
END$$
DELIMITER ;
