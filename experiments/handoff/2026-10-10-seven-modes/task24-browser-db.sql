SELECT @@version AS mysql_version, DATABASE() AS test_database;
SELECT 'test' AS db_name,MAX(CAST(version AS UNSIGNED)) AS schema_version FROM flyway_schema_history WHERE success=1;
SELECT 'application' AS db_name,MAX(CAST(version AS UNSIGNED)) AS schema_version FROM quizz.flyway_schema_history WHERE success=1;
SELECT id,room_id,schema_version,rules_version,status,phase,end_reason,question_count FROM game_session WHERE id IN (2070,2071,2072,2073) ORDER BY id;
SELECT p.game_session_id,p.user_id,p.player_state,p.score,p.total_correct_answer_time_ms,p.final_rank,p.remaining_spins,p.star_available,p.has_momentum,p.has_recovery,
 COALESCE(SUM(CASE WHEN a.answer_status='CORRECT' THEN a.answer_time_ms ELSE 0 END),0) AS sql_correct_ms
 FROM player_session p LEFT JOIN answer a ON a.player_session_id=p.id WHERE p.game_session_id IN (2070,2071,2072,2073) GROUP BY p.id ORDER BY p.game_session_id,p.user_id;
SELECT game_session_id,order_index,mode,stage_question_index,question_duration_ms,released_hint_count,JSON_LENGTH(payload,'$.hints') AS snapshot_hint_count,opened_at_ms,deadline_at_ms,scored_at_ms
 FROM game_question WHERE game_session_id IN (2070,2071,2072,2073) ORDER BY game_session_id,order_index;
SELECT game_session_id,answer_status,COUNT(*) AS answer_count FROM answer WHERE game_session_id IN (2070,2071,2072,2073) GROUP BY game_session_id,answer_status ORDER BY game_session_id,answer_status;
SELECT q.order_index,q.mode,p.user_id,a.answer_status,a.base_delta,a.score_delta,a.score_after,JSON_EXTRACT(a.result_snapshot,'$.ruleDelta') AS rule_delta
 FROM answer a JOIN game_question q ON q.id=a.game_question_id JOIN player_session p ON p.id=a.player_session_id WHERE a.game_session_id=2070 AND q.order_index IN (1,6,7,10,11,12,13,14,15,16) ORDER BY q.order_index,p.user_id;
SELECT id,status,config_version FROM room WHERE id IN (2293,2294,2295,2296) ORDER BY id;
SELECT COUNT(*) AS outstanding_active_users FROM user_active_game WHERE game_session_id IN (2070,2071,2072,2073);
SELECT game_session_id,user_id,score,total_correct_answer_time_ms FROM player_session WHERE game_session_id IN (1940,1941) ORDER BY game_session_id,user_id;
SELECT COUNT(*) AS invalid_non_clues_cursors FROM game_question WHERE mode<>'CLUES' AND released_hint_count<>0;
