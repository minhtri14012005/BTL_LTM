-- Preserve bank/question identities and existing detached lesson snapshots.
ALTER TABLE quiz ADD COLUMN subject_id BIGINT NULL;
UPDATE quiz q JOIN (
 SELECT c.source_quiz_id,MIN(o.subject_id) subject_id
 FROM lesson_game_content c JOIN lesson_game g ON g.id=c.game_id
 JOIN lesson l ON l.id=g.lesson_id JOIN course_class cl ON cl.id=l.course_class_id
 JOIN subject_offering o ON o.id=cl.offering_id
 GROUP BY c.source_quiz_id HAVING COUNT(DISTINCT o.subject_id)=1
) used ON used.source_quiz_id=q.id SET q.subject_id=used.subject_id;
-- Approved legacy assignment: use the existing ADMIN-created LTM subject.
-- A single existing subject is also unambiguous on older installations.
UPDATE quiz SET subject_id=COALESCE(
 (SELECT id FROM academic_subject WHERE code='LTM'),
 (SELECT MIN(id) FROM academic_subject HAVING COUNT(*)=1)
) WHERE subject_id IS NULL;
-- Refuse ambiguous upgrades rather than invent a subject or discard content.
ALTER TABLE quiz MODIFY subject_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_bank_subject FOREIGN KEY(subject_id) REFERENCES academic_subject(id),
 ADD INDEX ix_bank_subject_mode(subject_id,mode,visibility,id);

ALTER TABLE lesson_game_content ADD COLUMN subject_id BIGINT NULL;
DROP TRIGGER trg_learning_content_update;
UPDATE lesson_game_content c JOIN lesson_game g ON g.id=c.game_id
 JOIN lesson l ON l.id=g.lesson_id JOIN course_class cl ON cl.id=l.course_class_id
 JOIN subject_offering o ON o.id=cl.offering_id SET c.subject_id=o.subject_id;
ALTER TABLE lesson_game_content MODIFY subject_id BIGINT NOT NULL,
 ADD CONSTRAINT fk_content_subject FOREIGN KEY(subject_id) REFERENCES academic_subject(id);
DELIMITER $$
CREATE TRIGGER trg_learning_content_update BEFORE UPDATE ON lesson_game_content FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 SELECT g.status INTO stage FROM lesson_game g JOIN lesson l ON l.id=g.lesson_id WHERE g.id=OLD.game_id FOR UPDATE;
 IF stage<>'READY' OR NEW.game_id<>OLD.game_id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTENT_FROZEN'; END IF;
END$$
CREATE TRIGGER trg_content_subject_insert BEFORE INSERT ON lesson_game_content FOR EACH ROW
BEGIN
 DECLARE class_subject BIGINT;
 SELECT o.subject_id INTO class_subject FROM lesson_game g JOIN lesson l ON l.id=g.lesson_id
 JOIN course_class c ON c.id=l.course_class_id JOIN subject_offering o ON o.id=c.offering_id WHERE g.id=NEW.game_id;
 IF NEW.subject_id<>class_subject THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTENT_SUBJECT_MISMATCH'; END IF;
END$$
CREATE TRIGGER trg_content_subject_update BEFORE UPDATE ON lesson_game_content FOR EACH ROW
BEGIN
 IF NEW.subject_id<>OLD.subject_id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTENT_SUBJECT_FROZEN'; END IF;
END$$
DELIMITER ;
