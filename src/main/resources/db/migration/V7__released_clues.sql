-- Additive release cursor; immutable hint timeline/answers remain in the existing snapshot.
ALTER TABLE game_question
 ADD COLUMN released_hint_count INT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_game_question_released_hints CHECK (
   released_hint_count >= 0 AND
   ((mode <> 'CLUES' AND released_hint_count = 0) OR
    (mode = 'CLUES' AND released_hint_count <= JSON_LENGTH(payload, '$.hints')
     AND (released_hint_count = 0 OR opened_at_ms IS NOT NULL)))
 );
