-- Do not mutate immutable game_session config snapshots or active-room configuration.
ALTER TABLE room ALTER COLUMN decision_duration_ms SET DEFAULT 7000;
UPDATE room SET decision_duration_ms = 7000, revision = revision + 1
WHERE status IN ('DRAFT', 'WAITING') AND decision_duration_ms <> 7000;
