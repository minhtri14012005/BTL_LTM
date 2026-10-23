-- Preserve all legacy predicates and rows; add the academic content variants.
ALTER TABLE quiz DROP CHECK ck_quiz_mode,
 ADD CONSTRAINT ck_quiz_mode CHECK(mode IN ('QUIZ','SONG','VIETNAMESE_PUZZLE','RIDDLE','CLUES','IMAGE_WORD','ORDERING','FILL_BLANK','ASSIGNMENT'));
ALTER TABLE question DROP CHECK ck_question_values,
 ADD CONSTRAINT ck_question_values CHECK ((schema_version IN (1,2) AND (schema_version=2 OR mode='QUIZ') AND order_index>0 AND CHAR_LENGTH(TRIM(content))>0 AND created_at_ms>=0 AND (deleted_at_ms IS NULL OR deleted_at_ms>=created_at_ms) AND ((mode='QUIZ' AND payload IS NULL AND correct_option IS NOT NULL AND correct_option IN ('A','B','C','D') AND option_a IS NOT NULL AND CHAR_LENGTH(TRIM(option_a))>0 AND option_b IS NOT NULL AND CHAR_LENGTH(TRIM(option_b))>0 AND option_c IS NOT NULL AND CHAR_LENGTH(TRIM(option_c))>0 AND option_d IS NOT NULL AND CHAR_LENGTH(TRIM(option_d))>0) OR (schema_version=2 AND option_a IS NULL AND option_b IS NULL AND option_c IS NULL AND option_d IS NULL AND correct_option IS NULL AND payload IS NOT NULL AND ((mode='RIDDLE' AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["acceptedAnswers","matchingPolicy"],"properties":{"acceptedAnswers":{"type":"array","minItems":1,"maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":300,"pattern":"\\\\S"}},"matchingPolicy":{"enum":["NFC_CASE_INSENSITIVE_WHITESPACE"]}}}', payload)) OR (mode='SONG' AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["acceptedAnswers","matchingPolicy","mediaRef"],"properties":{"acceptedAnswers":{"type":"array","minItems":1,"maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":300,"pattern":"\\\\S"}},"matchingPolicy":{"enum":["NFC_CASE_INSENSITIVE_WHITESPACE"]},"mediaRef":{"type":"string","pattern":"^sha256:[0-9a-f]{64}$"}}}', payload)) OR (mode='IMAGE_WORD' AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["acceptedAnswers","matchingPolicy","mediaRef"],"properties":{"acceptedAnswers":{"type":"array","minItems":1,"maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":300,"pattern":"\\\\S"}},"matchingPolicy":{"enum":["NFC_CASE_INSENSITIVE_WHITESPACE"]},"mediaRef":{"type":"string","pattern":"^sha256:[0-9a-f]{64}$"}}}', payload)) OR (mode='CLUES' AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["acceptedAnswers","matchingPolicy","hints"],"properties":{"acceptedAnswers":{"type":"array","minItems":1,"maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":300,"pattern":"\\\\S"}},"matchingPolicy":{"enum":["NFC_CASE_INSENSITIVE_WHITESPACE"]},"hints":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["offsetMs","text"],"properties":{"offsetMs":{"type":"integer","minimum":0},"text":{"type":"string","minLength":1,"maxLength":2000}}}}}}', payload)) OR (mode='VIETNAMESE_PUZZLE' AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["pieces","correctOrder"],"properties":{"pieces":{"type":"array","minItems":2,"maxItems":100,"items":{"type":"object","additionalProperties":false,"required":["id","text"],"properties":{"id":{"type":"string","minLength":1,"maxLength":64},"text":{"type":"string","minLength":1,"maxLength":300}}}},"correctOrder":{"type":"array","minItems":2,"maxItems":100,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":64}},"acceptedAnswers":{"type":"array","minItems":1,"maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":300}},"matchingPolicy":{"type":"string","enum":["NFC_CASE_INSENSITIVE_WHITESPACE"]}},"dependencies":{"acceptedAnswers":["matchingPolicy"],"matchingPolicy":["acceptedAnswers"]}}', payload)) OR (mode='ORDERING' AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["items","correctOrder"],"properties":{"items":{"type":"array","minItems":2,"maxItems":100,"items":{"type":"object","additionalProperties":false,"required":["id","text"],"properties":{"id":{"type":"string","minLength":1,"maxLength":64},"text":{"type":"string","minLength":1,"maxLength":300}}}},"correctOrder":{"type":"array","minItems":2,"maxItems":100,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":64}}}}', payload)))))) OR (schema_version=3 AND mode IN ('FILL_BLANK','ASSIGNMENT') AND order_index>0 AND CHAR_LENGTH(TRIM(content))>0 AND created_at_ms>=0 AND (deleted_at_ms IS NULL OR deleted_at_ms>=created_at_ms) AND option_a IS NULL AND option_b IS NULL AND option_c IS NULL AND option_d IS NULL AND correct_option IS NULL AND image_ref IS NULL AND payload IS NOT NULL AND
 ((mode='ASSIGNMENT' AND JSON_TYPE(payload)='OBJECT' AND JSON_LENGTH(payload)=0) OR
  (mode='FILL_BLANK' AND CHAR_LENGTH(content)-CHAR_LENGTH(REPLACE(content,'{{blank}}',''))=9 AND JSON_SCHEMA_VALID('{"type":"object","additionalProperties":false,"required":["acceptedAnswers","matchingPolicy"],"properties":{"acceptedAnswers":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"string","minLength":1,"maxLength":300}},"matchingPolicy":{"enum":["EXACT","CASE_INSENSITIVE"]}}}',payload)))));

ALTER TABLE lesson_game ADD UNIQUE KEY uq_learning_game_mode(id,mode);
CREATE TABLE lesson_game_content (
 game_id BIGINT NOT NULL PRIMARY KEY,
 source_quiz_id BIGINT NOT NULL,
 source_revision BIGINT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 mode VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 title VARCHAR(200) NOT NULL,
 questions JSON NOT NULL,
 CONSTRAINT fk_content_source FOREIGN KEY(source_quiz_id) REFERENCES quiz(id),
 CONSTRAINT fk_content_game FOREIGN KEY(game_id,mode) REFERENCES lesson_game(id,mode),
 CONSTRAINT ck_learning_content CHECK(mode IN ('QUIZ','FILL_BLANK','ASSIGNMENT') AND revision>=0 AND source_revision>=0
   AND CHAR_LENGTH(TRIM(title))>0 AND JSON_TYPE(questions)='ARRAY' AND JSON_LENGTH(questions) BETWEEN 1 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER trg_learning_content_insert BEFORE INSERT ON lesson_game_content FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 SELECT g.status INTO stage FROM lesson_game g JOIN lesson l ON l.id=g.lesson_id WHERE g.id=NEW.game_id FOR UPDATE;
 IF stage<>'READY' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTENT_FROZEN'; END IF;
END$$
CREATE TRIGGER trg_learning_content_update BEFORE UPDATE ON lesson_game_content FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 SELECT g.status INTO stage FROM lesson_game g JOIN lesson l ON l.id=g.lesson_id WHERE g.id=OLD.game_id FOR UPDATE;
 IF stage<>'READY' OR NEW.game_id<>OLD.game_id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTENT_FROZEN'; END IF;
END$$
CREATE TRIGGER trg_learning_content_delete BEFORE DELETE ON lesson_game_content FOR EACH ROW
BEGIN
 DECLARE stage VARCHAR(16);
 SELECT g.status INTO stage FROM lesson_game g JOIN lesson l ON l.id=g.lesson_id WHERE g.id=OLD.game_id FOR UPDATE;
 IF stage<>'READY' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTENT_FROZEN'; END IF;
END$$
DELIMITER ;
