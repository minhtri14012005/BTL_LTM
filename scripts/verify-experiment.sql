-- Read-only verification of the opt-in experiment schema; no password or fixture mutation.
USE quizz_task15_experiment;
SELECT VERSION() AS mysql_version;
SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) AS active_games FROM game_session WHERE status='ACTIVE';
SELECT COUNT(*) AS active_users FROM user_active_game;
SELECT COUNT(*) AS duplicate_answer_groups FROM
 (SELECT player_session_id,game_question_id FROM answer GROUP BY player_session_id,game_question_id HAVING COUNT(*)>1) duplicates;
SELECT COUNT(*) AS cross_game_answers FROM answer a
 JOIN player_session p ON p.id=a.player_session_id JOIN game_question q ON q.id=a.game_question_id
 WHERE a.game_session_id<>p.game_session_id OR a.game_session_id<>q.game_session_id;
SELECT COUNT(*) AS spin_pool_inconsistency FROM player_session p JOIN game_session g ON g.id=p.game_session_id
 WHERE JSON_LENGTH(p.remaining_spin_pool) <> 6-(FLOOR(g.question_count/10)-p.remaining_spins);
SELECT COUNT(*) AS invalid_unscored_results FROM answer
 WHERE answer_status='ACCEPTED_UNSCORED' AND (scored_at_ms IS NOT NULL OR base_delta IS NOT NULL OR score_delta IS NOT NULL OR score_after IS NOT NULL OR result_snapshot IS NOT NULL);
SELECT end_reason,COUNT(*) AS games FROM game_session GROUP BY end_reason ORDER BY end_reason;
