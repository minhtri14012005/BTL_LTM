-- AWARD_V1: approved T10 signoff. Existing Games remain without a policy snapshot.
ALTER TABLE game_session
 ADD COLUMN game_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NORMAL',
 ADD COLUMN award_policy_snapshot JSON NULL,
 ADD CONSTRAINT ck_game_award_metadata CHECK(game_type IN ('NORMAL','FRIENDLY_1V1') AND
   (award_policy_snapshot IS NULL OR (schema_version=2 AND rules_version=2 AND game_type='NORMAL' AND JSON_TYPE(award_policy_snapshot)='OBJECT')));

CREATE TABLE game_award_decision (
 game_session_id BIGINT NOT NULL PRIMARY KEY,
 state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
 revision BIGINT NOT NULL DEFAULT 0,
 policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 policy_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 evidence_version INT NOT NULL DEFAULT 1,
 result_revision BIGINT NOT NULL,
 created_at_ms BIGINT NOT NULL,
 eligibility_evaluated_at_ms BIGINT NULL,
 decided_at_ms BIGINT NULL,
 reason_codes JSON NULL,
 initial_player_count INT NULL, eligible_player_count INT NULL,
 configured_count INT NULL, opened_count INT NULL, scored_count INT NULL, eligible_correct_count INT NULL,
 attempts INT NOT NULL DEFAULT 0, retry_after_ms BIGINT NOT NULL DEFAULT 0,
 last_error_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
 CONSTRAINT fk_award_decision_game FOREIGN KEY(game_session_id) REFERENCES game_session(id),
 CONSTRAINT ck_award_decision CHECK(evidence_version=1 AND result_revision>=0 AND created_at_ms>=0 AND attempts>=0 AND retry_after_ms>=0 AND
  ((state='PENDING' AND revision=0 AND decided_at_ms IS NULL AND eligibility_evaluated_at_ms IS NULL) OR
   (state IN ('DECIDED','NOT_QUALIFIED') AND revision=1 AND decided_at_ms IS NOT NULL AND eligibility_evaluated_at_ms IS NOT NULL AND
    initial_player_count>=0 AND eligible_player_count>=0 AND eligible_player_count<=initial_player_count AND
    configured_count>=0 AND opened_count>=0 AND scored_count>=0 AND eligible_correct_count>=0 AND reason_codes IS NOT NULL)))
) ENGINE=InnoDB;
CREATE INDEX ix_award_pending ON game_award_decision(state,retry_after_ms,game_session_id);

CREATE TABLE game_award_participant (
 game_session_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 eligible BOOLEAN NOT NULL, private_evidence JSON NOT NULL,
 history_rank INT NOT NULL, award_rank INT NULL,
 official_game_delta TINYINT NOT NULL, official_win_delta TINYINT NOT NULL,
 participated_count INT NOT NULL, correct_count INT NOT NULL, correct_time_ms BIGINT NOT NULL,
 mode_metrics JSON NOT NULL,
 PRIMARY KEY(game_session_id,user_id),
 CONSTRAINT fk_award_participant_decision FOREIGN KEY(game_session_id) REFERENCES game_award_decision(game_session_id),
 CONSTRAINT fk_award_participant_player FOREIGN KEY(game_session_id,user_id) REFERENCES player_session(game_session_id,user_id),
 CONSTRAINT ck_award_participant CHECK(eligible IN (0,1) AND history_rank>0 AND (award_rank IS NULL OR award_rank>0) AND
  official_game_delta IN (0,1) AND official_win_delta BETWEEN 0 AND official_game_delta AND
  (eligible=1 OR (official_game_delta=0 AND official_win_delta=0 AND award_rank IS NULL)) AND
  participated_count>=0 AND correct_count BETWEEN 0 AND participated_count AND correct_time_ms>=0)
) ENGINE=InnoDB;

CREATE TABLE achievement_award (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 game_session_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 award_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 mode_key VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 award_value INT NOT NULL DEFAULT 1, earned_at_ms BIGINT NOT NULL,
 CONSTRAINT uq_achievement_award UNIQUE(game_session_id,user_id,award_type,mode_key),
 CONSTRAINT fk_achievement_participant FOREIGN KEY(game_session_id,user_id) REFERENCES game_award_participant(game_session_id,user_id),
 CONSTRAINT ck_achievement_shape CHECK(award_value=1 AND earned_at_ms>=0 AND
  ((award_type IN ('CUP','PERFECT') AND mode_key='ALL') OR
   (award_type='FAST' AND mode_key IN ('QUIZ','SONG','VIETNAMESE_PUZZLE','RIDDLE','CLUES','IMAGE_WORD','ORDERING'))))
) ENGINE=InnoDB;
CREATE INDEX ix_achievement_user ON achievement_award(user_id,award_type,earned_at_ms,id);

DROP TRIGGER trg_game_snapshot_immutable;
DELIMITER $$
CREATE TRIGGER trg_game_snapshot_immutable BEFORE UPDATE ON game_session FOR EACH ROW
BEGIN
 IF NOT (OLD.room_id <=> NEW.room_id) OR NOT (OLD.quiz_id <=> NEW.quiz_id)
  OR NOT (OLD.quiz_author_user_id <=> NEW.quiz_author_user_id) OR NOT (OLD.quiz_title_snapshot <=> NEW.quiz_title_snapshot)
  OR NOT (OLD.schema_version <=> NEW.schema_version) OR NOT (OLD.rules_version <=> NEW.rules_version)
  OR NOT (OLD.v2_config_snapshot <=> NEW.v2_config_snapshot) OR NOT (OLD.question_count <=> NEW.question_count)
  OR NOT (OLD.config_snapshot <=> NEW.config_snapshot) OR NOT (OLD.game_type <=> NEW.game_type)
  OR NOT (OLD.award_policy_snapshot <=> NEW.award_policy_snapshot) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Game content/config/award policy snapshot is immutable';
 END IF;
END$$
CREATE TRIGGER trg_award_terminal_immutable BEFORE UPDATE ON game_award_decision FOR EACH ROW
BEGIN
 IF OLD.state<>'PENDING' OR OLD.game_session_id<>NEW.game_session_id OR OLD.policy_version<>NEW.policy_version
  OR OLD.policy_hash<>NEW.policy_hash OR OLD.created_at_ms<>NEW.created_at_ms OR OLD.result_revision<>NEW.result_revision
  OR OLD.evidence_version<>NEW.evidence_version THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Award decision is immutable';
 END IF;
END$$
CREATE TRIGGER trg_award_participant_insert BEFORE INSERT ON game_award_participant FOR EACH ROW
BEGIN
 DECLARE decision_state VARCHAR(24);
 SELECT state INTO decision_state FROM game_award_decision WHERE game_session_id=NEW.game_session_id FOR SHARE;
 IF decision_state<>'PENDING' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Award decision already terminal'; END IF;
END$$
CREATE TRIGGER trg_award_ledger_insert BEFORE INSERT ON achievement_award FOR EACH ROW
BEGIN
 DECLARE decision_state VARCHAR(24);
 SELECT state INTO decision_state FROM game_award_decision WHERE game_session_id=NEW.game_session_id FOR SHARE;
 IF decision_state<>'PENDING' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Award decision already terminal'; END IF;
END$$
CREATE TRIGGER trg_award_participant_immutable BEFORE UPDATE ON game_award_participant FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Award participant evidence is immutable'; END$$
CREATE TRIGGER trg_award_ledger_immutable BEFORE UPDATE ON achievement_award FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Award ledger is immutable'; END$$
DELIMITER ;
