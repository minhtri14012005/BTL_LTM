-- Account authority and session revocation survive expiry, UNBAN and restart.
ALTER TABLE app_user
    ADD COLUMN ban_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NONE',
    ADD COLUMN banned_until_ms BIGINT NULL,
    ADD COLUMN ban_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN auth_epoch BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_account_sanction CHECK (ban_version>=0 AND auth_epoch>=0 AND
        ((ban_kind='TEMP' AND banned_until_ms IS NOT NULL AND banned_until_ms>=0) OR
         (ban_kind IN ('NONE','INDEFINITE') AND banned_until_ms IS NULL)));

CREATE TABLE admin_account_action (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_version BIGINT NOT NULL,
    duration_minutes INT NULL,
    reason VARCHAR(240) NOT NULL,
    result VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    before_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    before_until_ms BIGINT NULL,
    after_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    after_until_ms BIGINT NULL,
    committed_version BIGINT NOT NULL,
    auth_cutoff_epoch BIGINT NOT NULL,
    effective_at_ms BIGINT NOT NULL,
    CONSTRAINT uq_account_action_request UNIQUE(admin_user_id,request_id),
    CONSTRAINT fk_account_action_admin FOREIGN KEY(admin_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_account_action_target FOREIGN KEY(target_user_id) REFERENCES app_user(id),
    CONSTRAINT ck_account_action CHECK(action IN ('TEMP','INDEFINITE','UNBAN') AND expected_version>=0
        AND committed_version>=0 AND auth_cutoff_epoch>=0 AND effective_at_ms>=0
        AND result IN ('APPLIED','NO_CHANGE') AND (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 525600))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Immutable target identity; only completion/retry fields may change. DONE Game
-- targets remain authoritative restrictions, including after UNBAN/expiry.
CREATE TABLE account_effect_intent (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    action_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    ban_version BIGINT NOT NULL,
    effect_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    game_session_id BIGINT NULL,
    room_id BIGINT NULL,
    membership_row_id BIGINT NULL,
    membership_generation BIGINT NULL,
    auth_cutoff_epoch BIGINT NULL,
    reservation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reservation_version BIGINT NULL,
    effective_at_ms BIGINT NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    retry_after_ms BIGINT NOT NULL DEFAULT 0,
    completed_at_ms BIGINT NULL,
    CONSTRAINT uq_account_effect_target UNIQUE(action_id,effect_type,target_key),
    CONSTRAINT fk_account_effect_action FOREIGN KEY(action_id) REFERENCES admin_account_action(id),
    CONSTRAINT fk_account_effect_user FOREIGN KEY(user_id) REFERENCES app_user(id),
    CONSTRAINT fk_account_effect_game FOREIGN KEY(game_session_id) REFERENCES game_session(id),
    CONSTRAINT fk_account_effect_room FOREIGN KEY(room_id) REFERENCES room(id),
    CONSTRAINT fk_account_effect_membership FOREIGN KEY(membership_row_id) REFERENCES room_member(id),
    CONSTRAINT ck_account_effect CHECK(ban_version>0 AND effective_at_ms>=0 AND attempts>=0
        AND state IN ('PENDING','DONE') AND effect_type IN ('GAME','MEMBERSHIP','SESSIONS','RESERVATION')
        AND (membership_generation IS NULL OR membership_generation>0)
        AND ((effect_type='GAME' AND game_session_id IS NOT NULL AND room_id IS NOT NULL) OR
             (effect_type='MEMBERSHIP' AND room_id IS NOT NULL AND membership_row_id IS NOT NULL AND membership_generation IS NOT NULL) OR
             (effect_type='SESSIONS' AND auth_cutoff_epoch IS NOT NULL) OR
             (effect_type='RESERVATION' AND reservation_id IS NOT NULL AND reservation_version IS NOT NULL)))
) ENGINE=InnoDB;
CREATE INDEX ix_account_effect_pending ON account_effect_intent(state,retry_after_ms,id);
CREATE INDEX ix_account_effect_game_user ON account_effect_intent(game_session_id,user_id,effect_type);

CREATE TABLE admin_orphan_close_action (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    room_id BIGINT NOT NULL,
    host_user_id BIGINT NOT NULL,
    host_ban_version BIGINT NOT NULL,
    reason VARCHAR(240) NOT NULL,
    effective_at_ms BIGINT NOT NULL,
    room_revision BIGINT NOT NULL,
    CONSTRAINT uq_orphan_close_request UNIQUE(admin_user_id,request_id),
    CONSTRAINT fk_orphan_close_admin FOREIGN KEY(admin_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_orphan_close_room FOREIGN KEY(room_id) REFERENCES room(id),
    CONSTRAINT fk_orphan_close_host FOREIGN KEY(host_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
