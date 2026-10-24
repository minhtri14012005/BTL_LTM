-- ClassQuest owns lesson/class data. Legacy matches are removed only after dependency guards.
DELIMITER $$
CREATE PROCEDURE classquest_legacy_cleanup_guard()
BEGIN
 IF EXISTS(SELECT 1 FROM lesson_game_content c JOIN quiz q ON q.id=c.source_quiz_id WHERE q.mode NOT IN ('QUIZ','FILL_BLANK','ASSIGNMENT')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ClassQuest snapshot still references a legacy bank';
 END IF;
 IF EXISTS(SELECT 1 FROM lesson_game_content c JOIN JSON_TABLE(c.questions,'$[*]' COLUMNS(question_id BIGINT PATH '$.id' NULL ON EMPTY)) ids JOIN question q ON q.id=ids.question_id WHERE q.mode NOT IN ('QUIZ','FILL_BLANK','ASSIGNMENT')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ClassQuest snapshot still references a legacy question';
 END IF;
 IF EXISTS(SELECT 1 FROM achievement_award WHERE lesson_game_id IS NOT NULL AND game_session_id IS NOT NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ambiguous award source must be reviewed';
 END IF;
END$$
DELIMITER ;
CALL classquest_legacy_cleanup_guard();
DROP PROCEDURE classquest_legacy_cleanup_guard;

DROP TRIGGER trg_award_ledger_insert;
DELETE FROM achievement_award WHERE lesson_game_id IS NULL AND game_session_id IS NOT NULL;
ALTER TABLE achievement_award DROP FOREIGN KEY fk_achievement_lesson_player;
ALTER TABLE achievement_award
 DROP FOREIGN KEY fk_achievement_participant,
 DROP CHECK ck_achievement_source,
 DROP CHECK ck_achievement_shape,
 DROP INDEX uq_achievement_award,
 DROP COLUMN game_session_id,
 DROP COLUMN mode_key,
 DROP COLUMN award_value,
 MODIFY lesson_game_id BIGINT NOT NULL,
 ADD CONSTRAINT ck_achievement_shape CHECK(award_type='PERFECT' AND earned_at_ms>=0),
 ADD CONSTRAINT fk_achievement_lesson_player FOREIGN KEY(lesson_game_id,user_id) REFERENCES lesson_game_participant(game_id,student_id);
DELIMITER $$
CREATE TRIGGER trg_award_ledger_insert BEFORE INSERT ON achievement_award FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16); DECLARE game_mode VARCHAR(32);
 SELECT status,mode INTO stage,game_mode FROM lesson_game WHERE id=NEW.lesson_game_id;
 IF stage<>'COMPLETED' OR game_mode<>'QUIZ' OR NEW.award_type<>'PERFECT' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INVALID_LESSON_AWARD';
 END IF;
END$$
DELIMITER ;

-- Account actions and session revocation remain authoritative; old match/room effects do not.
DELETE FROM account_effect_intent WHERE effect_type IN ('GAME','MEMBERSHIP','RESERVATION');
ALTER TABLE account_effect_intent
 DROP CHECK ck_account_effect,
 DROP INDEX ix_account_effect_game_user,
 DROP COLUMN game_session_id,
 DROP COLUMN room_id,
 DROP COLUMN membership_row_id,
 DROP COLUMN membership_generation,
 DROP COLUMN reservation_id,
 DROP COLUMN reservation_version,
 MODIFY auth_cutoff_epoch BIGINT NOT NULL,
 ADD CONSTRAINT ck_account_effect CHECK(ban_version>0 AND effective_at_ms>=0 AND attempts>=0 AND state IN ('PENDING','DONE') AND effect_type='SESSIONS' AND auth_cutoff_epoch>=0);

-- Children precede their parents. Foreign-key enforcement stays enabled throughout cleanup.
DROP TABLE game_participation_restriction;
DROP TABLE admin_kick_action;
DROP TABLE admin_orphan_close_action;
DROP TABLE game_decision_action;
DROP TABLE answer;
DROP TABLE game_question_participation;
DROP TABLE game_award_participant;
DROP TABLE game_award_decision;
DROP TABLE user_active_game;
DROP TABLE player_session;
DROP TABLE game_question;
DROP TABLE game_stage;
DROP TABLE game_member;
DROP TABLE game_session;
DROP TABLE room_stage;
DROP TABLE room_member;
DROP TABLE room;

-- Keep every current bank/question, including old but valid QUIZ banks and their identities.
DELETE FROM question WHERE mode IN ('SONG','RIDDLE','ORDERING','CLUES','IMAGE_WORD','VIETNAMESE_PUZZLE');
DELETE FROM quiz WHERE mode IN ('SONG','RIDDLE','ORDERING','CLUES','IMAGE_WORD','VIETNAMESE_PUZZLE');
ALTER TABLE quiz DROP CHECK ck_quiz_mode,
 ADD CONSTRAINT ck_quiz_mode CHECK(mode IN ('QUIZ','FILL_BLANK','ASSIGNMENT'));
ALTER TABLE question DROP CHECK ck_question_values,
 ADD CONSTRAINT ck_question_values CHECK(order_index>0 AND CHAR_LENGTH(TRIM(content))>0 AND created_at_ms>=0 AND (deleted_at_ms IS NULL OR deleted_at_ms>=created_at_ms) AND
 ((mode='QUIZ' AND schema_version IN (1,2) AND payload IS NULL AND correct_option IN ('A','B','C','D') AND correct_option IS NOT NULL AND option_a IS NOT NULL AND CHAR_LENGTH(TRIM(option_a))>0 AND option_b IS NOT NULL AND CHAR_LENGTH(TRIM(option_b))>0 AND option_c IS NOT NULL AND CHAR_LENGTH(TRIM(option_c))>0 AND option_d IS NOT NULL AND CHAR_LENGTH(TRIM(option_d))>0) OR
  (schema_version=3 AND mode IN ('FILL_BLANK','ASSIGNMENT') AND option_a IS NULL AND option_b IS NULL AND option_c IS NULL AND option_d IS NULL AND correct_option IS NULL AND image_ref IS NULL AND payload IS NOT NULL AND
   ((mode='ASSIGNMENT' AND JSON_TYPE(payload)='OBJECT' AND JSON_LENGTH(payload)=0) OR
    (mode='FILL_BLANK' AND CHAR_LENGTH(content)-CHAR_LENGTH(REPLACE(content,'{{blank}}',''))=9 AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["acceptedAnswers","matchingPolicy"],"properties":{"acceptedAnswers":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"string","minLength":1,"maxLength":300}},"matchingPolicy":{"enum":["EXACT","CASE_INSENSITIVE"]}}}',payload))))));
