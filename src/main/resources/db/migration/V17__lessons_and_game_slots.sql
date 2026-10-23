CREATE TABLE lesson (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 course_class_id BIGINT NOT NULL,
 lesson_number INT NOT NULL,
 title VARCHAR(200) NOT NULL,
 visible BOOLEAN NOT NULL DEFAULT FALSE,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 revision BIGINT NOT NULL DEFAULT 0,
 allocated_points DECIMAL(5,2) NOT NULL DEFAULT 0,
 create_request CHAR(36) NOT NULL,
 create_fingerprint CHAR(64) NOT NULL,
 CONSTRAINT fk_lesson_class FOREIGN KEY(course_class_id) REFERENCES course_class(id),
 CONSTRAINT uq_lesson_number UNIQUE(course_class_id,lesson_number),
 CONSTRAINT uq_lesson_create UNIQUE(course_class_id,create_request),
 CONSTRAINT ck_lesson CHECK(lesson_number>0 AND revision>=0 AND CHAR_LENGTH(TRIM(title))>0
   AND status IN ('DRAFT','RUNNING','COMPLETED') AND allocated_points BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_game (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 position INT NOT NULL,
 mode VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 title VARCHAR(200) NOT NULL,
 budget DECIMAL(5,2) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'READY',
 CONSTRAINT fk_lesson_game FOREIGN KEY(lesson_id) REFERENCES lesson(id),
 CONSTRAINT uq_lesson_position UNIQUE(lesson_id,position),
 CONSTRAINT uq_lesson_mode UNIQUE(lesson_id,mode),
 CONSTRAINT ck_lesson_game CHECK(budget BETWEEN 0 AND 100 AND CHAR_LENGTH(TRIM(title))>0
   AND status IN ('READY','RUNNING','COMPLETED') AND
   ((position=1 AND mode='LUCKY_WHEEL') OR (position IN (2,3) AND mode IN ('QUIZ','ASSIGNMENT','FILL_BLANK'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_attendance (
 lesson_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 joined_at_ms BIGINT NOT NULL,
 PRIMARY KEY(lesson_id,student_id),
 CONSTRAINT fk_lesson_attend FOREIGN KEY(lesson_id) REFERENCES lesson(id),
 CONSTRAINT fk_lesson_student FOREIGN KEY(student_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_game_participant (
 game_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 joined_at_ms BIGINT NOT NULL,
 PRIMARY KEY(game_id,student_id),
 CONSTRAINT fk_game_participation FOREIGN KEY(game_id) REFERENCES lesson_game(id),
 CONSTRAINT fk_game_student FOREIGN KEY(student_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_game_score (
 game_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 score DECIMAL(5,2) NOT NULL,
 PRIMARY KEY(game_id,student_id),
 CONSTRAINT fk_game_score_participant FOREIGN KEY(game_id,student_id) REFERENCES lesson_game_participant(game_id,student_id),
 CONSTRAINT ck_game_score CHECK(score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE lesson_score (
 lesson_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 score DECIMAL(5,2) NOT NULL DEFAULT 0,
 PRIMARY KEY(lesson_id,student_id),
 CONSTRAINT fk_lesson_score_attendance FOREIGN KEY(lesson_id,student_id) REFERENCES lesson_attendance(lesson_id,student_id),
 CONSTRAINT ck_lesson_score CHECK(score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER trg_lesson_limit BEFORE INSERT ON lesson FOR EACH ROW
BEGIN
 DECLARE quota INT;
 DECLARE created_count INT;
 SELECT required_lessons INTO quota FROM course_class WHERE id=NEW.course_class_id FOR UPDATE;
 SELECT COUNT(*) INTO created_count FROM lesson WHERE course_class_id=NEW.course_class_id;
 IF created_count>=quota THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='LESSON_LIMIT'; END IF;
END$$
CREATE TRIGGER trg_lesson_lifecycle BEFORE UPDATE ON lesson FOR EACH ROW
BEGIN
 DECLARE finished INT;
 DECLARE slots INT;
 IF NEW.course_class_id<>OLD.course_class_id OR NEW.lesson_number<>OLD.lesson_number THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='LESSON_IDENTITY_FROZEN';
 END IF;
 IF OLD.status<>'DRAFT' AND (NEW.title<>OLD.title OR NEW.visible<>OLD.visible OR NEW.allocated_points<>OLD.allocated_points) THEN
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
CREATE TRIGGER trg_slot_insert BEFORE INSERT ON lesson_game FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 SELECT status INTO stage FROM lesson WHERE id=NEW.lesson_id FOR UPDATE;
 IF stage<>'DRAFT' OR NEW.status<>'READY' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SLOTS_FROZEN'; END IF;
 UPDATE lesson SET allocated_points=allocated_points+NEW.budget WHERE id=NEW.lesson_id;
END$$
CREATE TRIGGER trg_slot_update BEFORE UPDATE ON lesson_game FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 DECLARE previous_complete INT;
 SELECT status INTO stage FROM lesson WHERE id=OLD.lesson_id FOR UPDATE;
 IF NEW.lesson_id<>OLD.lesson_id OR NEW.position<>OLD.position THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SLOT_IDENTITY_FROZEN'; END IF;
 IF (NEW.mode<>OLD.mode OR NEW.title<>OLD.title OR NEW.budget<>OLD.budget) THEN
  IF stage<>'DRAFT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SLOTS_FROZEN'; END IF;
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
CREATE TRIGGER trg_slot_delete BEFORE DELETE ON lesson_game FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 SELECT status INTO stage FROM lesson WHERE id=OLD.lesson_id FOR UPDATE;
 IF stage<>'DRAFT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SLOTS_FROZEN'; END IF;
 UPDATE lesson SET allocated_points=allocated_points-OLD.budget WHERE id=OLD.lesson_id;
END$$
CREATE TRIGGER trg_game_score_budget BEFORE INSERT ON lesson_game_score FOR EACH ROW
BEGIN
 DECLARE max_points DECIMAL(5,2);
 DECLARE stage VARCHAR(16);
 SELECT budget,status INTO max_points,stage FROM lesson_game WHERE id=NEW.game_id;
 IF NEW.score>max_points OR stage<>'RUNNING' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INVALID_GAME_SCORE'; END IF;
END$$
CREATE TRIGGER trg_game_score_frozen BEFORE UPDATE ON lesson_game_score FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GAME_SCORE_FROZEN';
END$$
DELIMITER ;
