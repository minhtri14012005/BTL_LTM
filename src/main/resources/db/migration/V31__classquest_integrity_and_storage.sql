-- Remove obsolete storage, preserving accounts, outcomes and authoritative runtime state.
DELIMITER $$
CREATE PROCEDURE classquest_integrity_preflight()
BEGIN
 IF EXISTS(SELECT 1 FROM lesson_game WHERE points_per_correct IS NOT NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Review historical per-question point settings before migration';
 END IF;
 IF EXISTS(SELECT action_id FROM account_effect_intent GROUP BY action_id HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Multiple account effects require an explicit migration mapping';
 END IF;
 IF EXISTS(SELECT 1 FROM account_effect_intent e JOIN admin_account_action a ON a.id=e.action_id
  WHERE e.effect_type<>'SESSIONS' OR e.user_id<>a.target_user_id OR e.auth_cutoff_epoch<>a.auth_cutoff_epoch) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Account effect does not match its audited revocation';
 END IF;
 IF EXISTS(SELECT 1 FROM lesson_game_content c JOIN JSON_TABLE(c.questions,'$[*]' COLUMNS(item JSON PATH '$')) j
  WHERE JSON_TYPE(JSON_EXTRACT(j.item,'$.mediaRef'))<>'NULL' OR JSON_TYPE(JSON_EXTRACT(j.item,'$.hints'))<>'NULL'
     OR JSON_TYPE(JSON_EXTRACT(j.item,'$.pieces'))<>'NULL' OR JSON_TYPE(JSON_EXTRACT(j.item,'$.items'))<>'NULL'
     OR JSON_TYPE(JSON_EXTRACT(j.item,'$.correctOrder'))<>'NULL') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Nonempty legacy snapshot fields require review';
 END IF;
 IF EXISTS(SELECT 1 FROM lesson_action_window w JOIN lesson_game g ON g.id=w.game_id WHERE w.lesson_id<>g.lesson_id OR w.position<>g.position)
 OR EXISTS(SELECT 1 FROM lesson_action_input i JOIN lesson_game g ON g.id=i.game_id WHERE i.lesson_id<>g.lesson_id)
 OR EXISTS(SELECT 1 FROM lesson_wheel_round w JOIN lesson_game g ON g.id=w.game_id WHERE w.lesson_id<>g.lesson_id OR g.mode<>'LUCKY_WHEEL')
 OR EXISTS(SELECT 1 FROM lesson_assignment_grade a JOIN lesson_action_input i ON i.id=a.submission_id WHERE a.game_id<>i.game_id OR a.student_id<>i.student_id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inconsistent game, lesson or submission ownership';
 END IF;
END$$
DELIMITER ;
CALL classquest_integrity_preflight();
DROP PROCEDURE classquest_integrity_preflight;

DROP TRIGGER trg_game_settings_frozen;
ALTER TABLE lesson_game DROP CHECK ck_game_settings, DROP COLUMN points_per_correct,
 ADD CONSTRAINT ck_game_settings CHECK(duration_ms BETWEEN 1000 AND 2147483647 AND selected_count BETWEEN 1 AND 10000),
 ADD UNIQUE KEY uq_game_lesson(id,lesson_id);
DELIMITER $$
CREATE TRIGGER trg_game_settings_frozen BEFORE UPDATE ON lesson_game FOR EACH ROW
BEGIN
 IF OLD.status<>'READY' AND (NEW.duration_ms<>OLD.duration_ms OR NOT (NEW.question_count <=> OLD.question_count)
   OR NOT (NEW.wheel_cells <=> OLD.wheel_cells) OR NEW.selected_count<>OLD.selected_count) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GAME_CONFIG_FROZEN';
 END IF;
END$$
DELIMITER ;

-- One self-contained session revocation per audited account action.
ALTER TABLE account_effect_intent DROP CHECK ck_account_effect, DROP INDEX uq_account_effect_target,
 DROP COLUMN effect_type, DROP COLUMN target_key, DROP COLUMN ban_version, DROP COLUMN effective_at_ms,
 ADD UNIQUE KEY uq_account_effect_action(action_id),
 ADD CONSTRAINT ck_account_effect CHECK(auth_cutoff_epoch>=0 AND attempts>=0 AND retry_after_ms>=0 AND
  ((state='PENDING' AND completed_at_ms IS NULL) OR (state='DONE' AND completed_at_ms IS NOT NULL AND completed_at_ms>=0)));

-- A window's display position is derived from its game, never a second independent authority.
ALTER TABLE lesson_action_window DROP CHECK ck_lesson_window,
 DROP FOREIGN KEY fk_window_lesson, DROP FOREIGN KEY fk_window_game, DROP INDEX fk_window_game,
 DROP COLUMN position, ADD KEY ix_window_game_lesson(game_id,lesson_id),
 ADD CONSTRAINT fk_window_game_lesson FOREIGN KEY(game_id,lesson_id) REFERENCES lesson_game(id,lesson_id),
 ADD CONSTRAINT ck_lesson_window CHECK(token>0 AND question_index>0 AND status IN ('OPEN','CLOSED','CANCELLED') AND deadline_at_ms>opened_at_ms);
ALTER TABLE lesson_action_input DROP FOREIGN KEY fk_lesson_input_lesson,
 RENAME INDEX fk_lesson_input_lesson TO ix_input_lesson,
 ADD CONSTRAINT fk_input_game_lesson FOREIGN KEY(game_id,lesson_id) REFERENCES lesson_game(id,lesson_id),
 ADD UNIQUE KEY uq_input_owner(id,game_id,student_id);
ALTER TABLE lesson_wheel_round DROP FOREIGN KEY lesson_wheel_round_ibfk_1, DROP FOREIGN KEY lesson_wheel_round_ibfk_2,
 ADD CONSTRAINT fk_wheel_game_lesson FOREIGN KEY(game_id,lesson_id) REFERENCES lesson_game(id,lesson_id);
ALTER TABLE lesson_assignment_grade DROP FOREIGN KEY lesson_assignment_grade_ibfk_2, DROP INDEX submission_id,
 ADD CONSTRAINT fk_grade_submission_owner FOREIGN KEY(submission_id,game_id,student_id) REFERENCES lesson_action_input(id,game_id,student_id);

-- Mode already determines the valid question shape. Keep the composite bank/mode FK.
ALTER TABLE question DROP FOREIGN KEY fk_question_quiz, DROP INDEX uq_source_mode,
 DROP CHECK ck_question_values, DROP COLUMN schema_version;

ALTER TABLE question ADD CONSTRAINT ck_question_values CHECK(order_index>0 AND CHAR_LENGTH(TRIM(content))>0 AND created_at_ms>=0 AND (deleted_at_ms IS NULL OR deleted_at_ms>=created_at_ms) AND
 ((mode='QUIZ' AND payload IS NULL AND correct_option IN ('A','B','C','D') AND correct_option IS NOT NULL AND option_a IS NOT NULL AND CHAR_LENGTH(TRIM(option_a))>0 AND option_b IS NOT NULL AND CHAR_LENGTH(TRIM(option_b))>0 AND option_c IS NOT NULL AND CHAR_LENGTH(TRIM(option_c))>0 AND option_d IS NOT NULL AND CHAR_LENGTH(TRIM(option_d))>0) OR
  (mode IN ('FILL_BLANK','ASSIGNMENT') AND option_a IS NULL AND option_b IS NULL AND option_c IS NULL AND option_d IS NULL AND correct_option IS NULL AND image_ref IS NULL AND payload IS NOT NULL AND
   ((mode='ASSIGNMENT' AND JSON_TYPE(payload)='OBJECT' AND JSON_LENGTH(payload)=0) OR
    (mode='FILL_BLANK' AND CHAR_LENGTH(content)-CHAR_LENGTH(REPLACE(content,'{{blank}}',''))=9 AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["acceptedAnswers","matchingPolicy"],"properties":{"acceptedAnswers":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"string","minLength":1,"maxLength":300}},"matchingPolicy":{"enum":["EXACT","CASE_INSENSITIVE"]}}}',payload))))));

-- Normalize only null keys from retired modes; keep every question, answer and array position.
DROP TRIGGER trg_learning_content_update;
DELIMITER $$
CREATE PROCEDURE classquest_normalize_snapshots()
BEGIN
 DECLARE finished BOOLEAN DEFAULT FALSE;
 DECLARE content_id BIGINT;
 DECLARE data JSON;
 DECLARE idx INT;
 DECLARE item JSON;
 DECLARE changed BOOLEAN;
 DECLARE contents CURSOR FOR SELECT game_id,questions FROM lesson_game_content ORDER BY game_id;
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=TRUE;
 OPEN contents;
 content_loop: LOOP
  FETCH contents INTO content_id,data;
  IF finished THEN LEAVE content_loop; END IF;
  SET idx=0,changed=FALSE;
  WHILE idx<JSON_LENGTH(data) DO
   SET item=JSON_EXTRACT(data,CONCAT('$[',idx,']'));
   IF JSON_CONTAINS_PATH(item,'one','$.mediaRef','$.hints','$.pieces','$.items','$.correctOrder') THEN
    SET item=JSON_REMOVE(item,'$.mediaRef','$.hints','$.pieces','$.items','$.correctOrder');
    SET data=JSON_SET(data,CONCAT('$[',idx,']'),item),changed=TRUE;
   END IF;
   SET idx=idx+1;
  END WHILE;
  IF changed THEN UPDATE lesson_game_content SET questions=data WHERE game_id=content_id; END IF;
 END LOOP;
 CLOSE contents;
END$$
DELIMITER ;
CALL classquest_normalize_snapshots();
DROP PROCEDURE classquest_normalize_snapshots;
DELIMITER $$
CREATE TRIGGER trg_learning_content_update BEFORE UPDATE ON lesson_game_content FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 SELECT g.status INTO stage FROM lesson_game g JOIN lesson l ON l.id=g.lesson_id WHERE g.id=OLD.game_id FOR UPDATE;
 IF stage<>'READY' OR NEW.game_id<>OLD.game_id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTENT_FROZEN'; END IF;
END$$
DELIMITER ;
