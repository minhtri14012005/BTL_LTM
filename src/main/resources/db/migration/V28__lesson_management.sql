ALTER TABLE lesson
 ADD COLUMN deleted_at_ms BIGINT NULL,
 ADD COLUMN active_number INT GENERATED ALWAYS AS (IF(deleted_at_ms IS NULL,lesson_number,NULL)) STORED,
 DROP INDEX uq_lesson_number,
 ADD UNIQUE KEY uq_lesson_number(course_class_id,active_number);
DROP TRIGGER trg_lesson_lifecycle;
DROP TRIGGER trg_slot_delete;
DROP TRIGGER trg_learning_content_delete;
DELIMITER $$
CREATE TRIGGER trg_lesson_lifecycle BEFORE UPDATE ON lesson FOR EACH ROW
BEGIN
 DECLARE finished INT;
 DECLARE slots INT;
 IF OLD.deleted_at_ms IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='LESSON_DELETED'; END IF;
 IF NEW.deleted_at_ms IS NOT NULL AND OLD.status='COMPLETED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='COMPLETED_LESSON_CANNOT_DELETE'; END IF;
 IF NEW.course_class_id<>OLD.course_class_id THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='LESSON_IDENTITY_FROZEN';
 END IF;
 IF OLD.status<>'DRAFT' AND (NEW.title<>OLD.title) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='LESSON_CONFIG_FROZEN';
 END IF;
 IF NEW.status<>OLD.status THEN
  SELECT COUNT(*),COALESCE(SUM(status='COMPLETED'),0) INTO slots,finished FROM lesson_game WHERE lesson_id=OLD.id;
  IF NOT ((OLD.status='DRAFT' AND NEW.status='RUNNING' AND slots=3 AND NEW.visible=TRUE)
      OR (OLD.status='RUNNING' AND NEW.status='COMPLETED' AND slots=3 AND finished=3)) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INVALID_LESSON_TRANSITION';
  END IF;
 END IF;
END$$
CREATE TRIGGER trg_slot_delete BEFORE DELETE ON lesson_game FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16); DECLARE removed BIGINT;
 SELECT status,deleted_at_ms INTO stage,removed FROM lesson WHERE id=OLD.lesson_id FOR UPDATE;
 IF removed IS NULL THEN
  IF stage<>'DRAFT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SLOTS_FROZEN'; END IF;
  UPDATE lesson SET allocated_points=allocated_points-OLD.budget WHERE id=OLD.lesson_id;
 END IF;
END$$
CREATE TRIGGER trg_learning_content_delete BEFORE DELETE ON lesson_game_content FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16); DECLARE removed BIGINT;
 SELECT g.status,l.deleted_at_ms INTO stage,removed FROM lesson_game g JOIN lesson l ON l.id=g.lesson_id WHERE g.id=OLD.game_id FOR UPDATE;
 IF stage<>'READY' AND removed IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTENT_FROZEN'; END IF;
END$$
DELIMITER ;

ALTER TABLE lesson_wheel_receipt ADD COLUMN lesson_id BIGINT NULL, ADD CONSTRAINT fk_wheel_receipt_lesson FOREIGN KEY(lesson_id) REFERENCES lesson(id);

DROP TRIGGER trg_lesson_limit;
DELIMITER $$
CREATE TRIGGER trg_lesson_limit BEFORE INSERT ON lesson FOR EACH ROW
BEGIN
 DECLARE quota INT; DECLARE created_count INT;
 SELECT required_lessons INTO quota FROM course_class WHERE id=NEW.course_class_id FOR UPDATE;
 SELECT COUNT(*) INTO created_count FROM lesson WHERE course_class_id=NEW.course_class_id AND deleted_at_ms IS NULL;
 IF created_count>=quota THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='LESSON_LIMIT'; END IF;
END$$
DELIMITER ;
