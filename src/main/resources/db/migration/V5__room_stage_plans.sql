-- Preserve legacy Rooms and all completed Game snapshots; plans are append-only generations.
ALTER TABLE room
 MODIFY quiz_id BIGINT NULL,
 MODIFY question_duration_ms BIGINT NULL,
 ADD config_version INT NOT NULL DEFAULT 1,
 ADD plan_revision BIGINT NOT NULL DEFAULT 0,
 DROP CHECK ck_room_values,
 ADD CONSTRAINT ck_room_values CHECK (
   status IN ('DRAFT','WAITING','ACTIVE','CLOSED') AND max_players>=3
   AND decision_duration_ms>0 AND revision>=0 AND created_at_ms>=0
   AND CHAR_LENGTH(TRIM(name))>0 AND CHAR_LENGTH(room_code)>0
   AND config_version IN (1,2) AND plan_revision>=0
   AND ((config_version=1 AND quiz_id IS NOT NULL AND question_duration_ms IS NOT NULL AND question_duration_ms>0)
     OR (config_version=2 AND quiz_id IS NULL AND question_duration_ms IS NULL AND plan_revision>0)));
CREATE TABLE room_stage (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 room_id BIGINT NOT NULL, plan_revision BIGINT NOT NULL, order_index INT NOT NULL,
 mode VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quiz_id BIGINT NOT NULL, question_count INT NOT NULL, question_duration_ms BIGINT NOT NULL,
 CONSTRAINT uq_room_stage_order UNIQUE(room_id,plan_revision,order_index),
 CONSTRAINT uq_room_stage_mode UNIQUE(room_id,plan_revision,mode),
 CONSTRAINT fk_room_stage_room FOREIGN KEY(room_id) REFERENCES room(id),
 CONSTRAINT fk_room_stage_set_mode FOREIGN KEY(quiz_id,mode) REFERENCES quiz(id,mode),
 CONSTRAINT ck_room_stage_values CHECK(plan_revision>0 AND order_index BETWEEN 1 AND 7
   AND question_count BETWEEN 1 AND 50 AND question_duration_ms>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DELIMITER $$
CREATE TRIGGER trg_room_stage_immutable BEFORE UPDATE ON room_stage FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Room stage plan generations are immutable';
END$$
CREATE TRIGGER trg_room_stage_insert BEFORE INSERT ON room_stage FOR EACH ROW
BEGIN
 DECLARE room_state VARCHAR(16); DECLARE room_version INT; DECLARE current_plan BIGINT;
 SELECT status,config_version,plan_revision INTO room_state,room_version,current_plan FROM room WHERE id=NEW.room_id;
 IF room_state IS NULL OR room_state NOT IN ('DRAFT','WAITING') OR room_version<>2 OR current_plan<>NEW.plan_revision THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stage must belong to the current mutable Room plan';
 END IF;
END$$
DELIMITER ;
