ALTER TABLE lesson_game
 ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE,
 ADD COLUMN duration_ms INT NOT NULL DEFAULT 30000,
 ADD COLUMN points_per_correct DECIMAL(5,2) NULL,
 ADD COLUMN wheel_cells JSON NULL,
 ADD COLUMN selected_count INT NOT NULL DEFAULT 1,
 ADD CONSTRAINT ck_game_settings CHECK(duration_ms BETWEEN 1000 AND 2147483647 AND (points_per_correct IS NULL OR points_per_correct BETWEEN 0 AND 100) AND selected_count BETWEEN 1 AND 10000);

DROP TRIGGER trg_lesson_lifecycle;
DROP TRIGGER trg_slot_update;
DELIMITER $$
CREATE TRIGGER trg_lesson_lifecycle BEFORE UPDATE ON lesson FOR EACH ROW
BEGIN
 DECLARE finished INT;
 DECLARE slots INT;
 IF NEW.course_class_id<>OLD.course_class_id OR NEW.lesson_number<>OLD.lesson_number THEN
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
CREATE TRIGGER trg_slot_update BEFORE UPDATE ON lesson_game FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 DECLARE previous_complete INT;
 SELECT status INTO stage FROM lesson WHERE id=OLD.lesson_id FOR UPDATE;
 IF NEW.lesson_id<>OLD.lesson_id OR NEW.position<>OLD.position THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SLOT_IDENTITY_FROZEN'; END IF;
 IF (NEW.mode<>OLD.mode OR NEW.title<>OLD.title OR NEW.budget<>OLD.budget) THEN
  IF OLD.status<>'READY' OR (stage<>'DRAFT' AND (NEW.mode<>OLD.mode OR NEW.title<>OLD.title)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SLOTS_FROZEN'; END IF;
  UPDATE lesson SET allocated_points=allocated_points-OLD.budget+NEW.budget WHERE id=OLD.lesson_id;
 END IF;
 IF NEW.status<>OLD.status THEN
  SELECT COUNT(*) INTO previous_complete FROM lesson_game WHERE lesson_id=OLD.lesson_id AND position<OLD.position AND status='COMPLETED';
  IF stage<>'RUNNING' OR NOT ((OLD.status='READY' AND NEW.status='RUNNING' AND previous_complete=OLD.position-1)
       OR (OLD.status='RUNNING' AND NEW.status='COMPLETED')) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INVALID_SLOT_TRANSITION';
  END IF;
 END IF;
END$$
CREATE TRIGGER trg_game_settings_frozen BEFORE UPDATE ON lesson_game FOR EACH ROW
BEGIN
 IF OLD.status<>'READY' AND (NEW.duration_ms<>OLD.duration_ms OR NOT (NEW.points_per_correct <=> OLD.points_per_correct) OR NOT (NEW.wheel_cells <=> OLD.wheel_cells) OR NEW.selected_count<>OLD.selected_count) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GAME_CONFIG_FROZEN';
 END IF;
END$$
DELIMITER ;
