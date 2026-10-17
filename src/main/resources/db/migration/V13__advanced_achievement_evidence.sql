-- T13 signed AWARD_V2 only. No update/backfill of Games, decisions or existing awards.
ALTER TABLE achievement_award
 ADD COLUMN evidence_snapshot JSON NULL,
 DROP CHECK ck_achievement_shape,
 ADD CONSTRAINT ck_achievement_shape CHECK(award_value=1 AND earned_at_ms>=0 AND
   ((award_type IN ('CUP','PERFECT') AND mode_key='ALL') OR
    (award_type='FAST' AND mode_key IN ('QUIZ','SONG','VIETNAMESE_PUZZLE','RIDDLE','CLUES','IMAGE_WORD','ORDERING')) OR
    (award_type IN ('MULTITALENT','COMEBACK') AND mode_key='ALL' AND evidence_snapshot IS NOT NULL AND JSON_TYPE(evidence_snapshot)='OBJECT')));

DROP TRIGGER trg_award_ledger_insert;
DELIMITER $$
CREATE TRIGGER trg_award_ledger_insert BEFORE INSERT ON achievement_award FOR EACH ROW
BEGIN
 DECLARE decision_state VARCHAR(24);
 DECLARE decision_policy VARCHAR(64);
 SELECT state,policy_version INTO decision_state,decision_policy FROM game_award_decision WHERE game_session_id=NEW.game_session_id FOR SHARE;
 IF decision_state<>'PENDING' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Award decision already terminal'; END IF;
 IF NEW.award_type IN ('MULTITALENT','COMEBACK') AND decision_policy<>'AWARD_V2' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Advanced award policy required';
 END IF;
END$$
DELIMITER ;
