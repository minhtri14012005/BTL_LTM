-- Preserve reusable membership rows; every real rejoin gets a new generation.
ALTER TABLE room_member ADD COLUMN membership_generation BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_room_membership_generation CHECK (membership_generation > 0);
ALTER TABLE game_member ADD COLUMN membership_generation BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_game_membership_generation CHECK (membership_generation > 0);
UPDATE game_member gm JOIN room_member rm ON rm.room_id=gm.room_id AND rm.user_id=gm.user_id
    SET gm.membership_generation=rm.membership_generation;

ALTER TABLE game_question ADD COLUMN participation_captured BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE game_question_participation (
    game_session_id BIGINT NOT NULL,
    game_question_id BIGINT NOT NULL,
    player_session_id BIGINT NOT NULL,
    opened_at_ms BIGINT NOT NULL,
    PRIMARY KEY (game_question_id,player_session_id),
    CONSTRAINT fk_participation_question FOREIGN KEY (game_session_id,game_question_id) REFERENCES game_question(game_session_id,id),
    CONSTRAINT fk_participation_player FOREIGN KEY (game_session_id,player_session_id) REFERENCES player_session(game_session_id,id),
    CONSTRAINT ck_participation_open CHECK (opened_at_ms >= 0)
) ENGINE=InnoDB;
-- Before V9 no restrictions existed: retained outcomes or the elimination boundary
-- recover legacy participation; ACTIVE PLAYING accounts were eligible even offline.
INSERT INTO game_question_participation(game_session_id,game_question_id,player_session_id,opened_at_ms)
SELECT q.game_session_id,q.id,p.id,q.opened_at_ms FROM game_question q
JOIN player_session p ON p.game_session_id=q.game_session_id
WHERE q.opened_at_ms IS NOT NULL AND (p.player_state='PLAYING' OR p.eliminated_question_index>=q.order_index
    OR EXISTS(SELECT 1 FROM answer a WHERE a.game_question_id=q.id AND a.player_session_id=p.id));
UPDATE game_question SET participation_captured=TRUE WHERE opened_at_ms IS NOT NULL;

CREATE TABLE game_decision_action (
    game_session_id BIGINT NOT NULL,
    player_session_id BIGINT NOT NULL,
    question_index INT NOT NULL,
    action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    spin_effect VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    remaining_spins INT NOT NULL,
    star_available BOOLEAN NOT NULL,
    star_selected BOOLEAN NOT NULL,
    committed_at_ms BIGINT NOT NULL,
    PRIMARY KEY (game_session_id,player_session_id,question_index,action),
    CONSTRAINT fk_decision_player FOREIGN KEY (game_session_id,player_session_id) REFERENCES player_session(game_session_id,id),
    CONSTRAINT ck_decision_action CHECK (action IN ('USE_SPIN','USE_STAR') AND question_index BETWEEN 1 AND 50
        AND remaining_spins>=0 AND committed_at_ms>=0 AND star_available IN (0,1) AND star_selected IN (0,1)
        AND (spin_effect IS NULL OR spin_effect IN ('BONUS','SAFE','BREAKTHROUGH','SPEED','DECISIVE','HARDSHIP')))
) ENGINE=InnoDB;

CREATE TABLE admin_kick_action (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    game_session_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    membership_row_id BIGINT NOT NULL,
    membership_generation BIGINT NOT NULL,
    reason VARCHAR(240) NOT NULL,
    result VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_at_ms BIGINT NOT NULL,
    game_revision BIGINT NOT NULL,
    occupancy_released BOOLEAN NOT NULL,
    membership_detached BOOLEAN NOT NULL,
    CONSTRAINT uq_admin_kick_request UNIQUE (admin_user_id,request_id),
    CONSTRAINT fk_kick_admin FOREIGN KEY (admin_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_kick_member FOREIGN KEY (game_session_id,target_user_id) REFERENCES game_member(game_session_id,user_id),
    CONSTRAINT fk_kick_room FOREIGN KEY (room_id) REFERENCES room(id),
    CONSTRAINT fk_kick_membership FOREIGN KEY (membership_row_id) REFERENCES room_member(id),
    CONSTRAINT ck_kick_action CHECK (result IN ('KICK_APPLIED','ALREADY_APPLIED') AND effective_at_ms>=0
        AND game_revision>=0 AND membership_generation>0 AND occupancy_released IN (0,1) AND membership_detached IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE game_participation_restriction (
    game_session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action_id BIGINT NOT NULL,
    kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_at_ms BIGINT NOT NULL,
    PRIMARY KEY (game_session_id,user_id),
    CONSTRAINT uq_restriction_action UNIQUE (action_id),
    CONSTRAINT fk_restriction_member FOREIGN KEY (game_session_id,user_id) REFERENCES game_member(game_session_id,user_id),
    CONSTRAINT fk_restriction_action FOREIGN KEY (action_id) REFERENCES admin_kick_action(id),
    CONSTRAINT ck_restriction CHECK (kind='KICKED_BY_ADMIN' AND effective_at_ms>=0)
) ENGINE=InnoDB;
