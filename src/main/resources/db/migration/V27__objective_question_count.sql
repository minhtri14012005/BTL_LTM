ALTER TABLE lesson_game
 ADD COLUMN question_count INT NULL,
 ADD CONSTRAINT ck_game_question_count CHECK(question_count IS NULL OR question_count BETWEEN 1 AND 50);

DROP TRIGGER trg_game_settings_frozen;
DELIMITER $$
CREATE TRIGGER trg_game_settings_frozen BEFORE UPDATE ON lesson_game FOR EACH ROW
BEGIN
 IF OLD.status<>'READY' AND (NEW.duration_ms<>OLD.duration_ms OR NOT (NEW.points_per_correct <=> OLD.points_per_correct) OR NOT (NEW.question_count <=> OLD.question_count) OR NOT (NEW.wheel_cells <=> OLD.wheel_cells) OR NEW.selected_count<>OLD.selected_count) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GAME_CONFIG_FROZEN';
 END IF;
END$$
DELIMITER ;
