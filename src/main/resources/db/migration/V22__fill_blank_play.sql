ALTER TABLE lesson_quiz_answer MODIFY answer_value TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
DROP TRIGGER trg_game_score_frozen;
DELIMITER $$
CREATE TRIGGER trg_game_score_frozen BEFORE UPDATE ON lesson_game_score FOR EACH ROW
BEGIN
 DECLARE game_mode VARCHAR(32); DECLARE game_status VARCHAR(16); DECLARE max_points DECIMAL(5,2);
 SELECT mode,status,budget INTO game_mode,game_status,max_points FROM lesson_game WHERE id=OLD.game_id;
 IF NEW.game_id<>OLD.game_id OR NEW.student_id<>OLD.student_id OR game_mode NOT IN ('LUCKY_WHEEL','QUIZ','FILL_BLANK') OR game_status<>'RUNNING' OR NEW.score<0 OR NEW.score>max_points THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GAME_SCORE_FROZEN';
 END IF;
END$$
DELIMITER ;
