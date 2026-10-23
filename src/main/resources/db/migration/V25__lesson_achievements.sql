CREATE TABLE lesson_quiz_award_roster (
 game_id BIGINT NOT NULL,student_id BIGINT NOT NULL,
 PRIMARY KEY(game_id,student_id),
 FOREIGN KEY(game_id,student_id) REFERENCES lesson_game_participant(game_id,student_id)
) ENGINE=InnoDB;
ALTER TABLE achievement_award
 MODIFY game_session_id BIGINT NULL,
 ADD COLUMN lesson_game_id BIGINT NULL,
 ADD CONSTRAINT fk_achievement_lesson_player FOREIGN KEY(lesson_game_id,user_id) REFERENCES lesson_game_participant(game_id,student_id),
 ADD CONSTRAINT uq_lesson_achievement UNIQUE(lesson_game_id,user_id,award_type),
 ADD CONSTRAINT ck_achievement_source CHECK(
  (game_session_id IS NOT NULL AND lesson_game_id IS NULL) OR
  (game_session_id IS NULL AND lesson_game_id IS NOT NULL AND award_type='PERFECT' AND mode_key='ALL'));
DROP TRIGGER trg_award_ledger_insert;
DELIMITER $$
CREATE TRIGGER trg_award_ledger_insert BEFORE INSERT ON achievement_award FOR EACH ROW
BEGIN
 DECLARE decision_state VARCHAR(24); DECLARE decision_policy VARCHAR(64); DECLARE stage VARCHAR(16); DECLARE game_mode VARCHAR(32);
 IF NEW.lesson_game_id IS NOT NULL THEN
  SELECT status,mode INTO stage,game_mode FROM lesson_game WHERE id=NEW.lesson_game_id;
  IF stage<>'COMPLETED' OR game_mode<>'QUIZ' OR NEW.award_type<>'PERFECT' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INVALID_LESSON_AWARD';
  END IF;
 ELSE
  SELECT state,policy_version INTO decision_state,decision_policy FROM game_award_decision WHERE game_session_id=NEW.game_session_id FOR SHARE;
  IF decision_state<>'PENDING' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Award decision already terminal'; END IF;
  IF NEW.award_type IN ('MULTITALENT','COMEBACK') AND decision_policy<>'AWARD_V2' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Advanced award policy required'; END IF;
 END IF;
END$$
DELIMITER ;
