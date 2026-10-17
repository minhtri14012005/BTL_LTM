-- An outbox target is an immutable cross-aggregate identity, not a live FK.
-- FK validation itself takes shared parent locks. With User already locked,
-- those checks would introduce User -> Room/Game against Room -> Game -> User.
-- Retained source rows are never deleted by sanctions; target cleanup is scoped
-- CAS under the owning aggregate, and missing targets are handled idempotently.
ALTER TABLE account_effect_intent
    DROP FOREIGN KEY fk_account_effect_game,
    DROP FOREIGN KEY fk_account_effect_room,
    DROP FOREIGN KEY fk_account_effect_membership;
-- ADMIN identity is audited, never locked by a target-only ban transaction.
-- An ADMIN may hold another User admission fence concurrently; do not acquire
-- its row implicitly while writing the target's audit receipt.
ALTER TABLE admin_account_action DROP FOREIGN KEY fk_account_action_admin;
ALTER TABLE admin_orphan_close_action DROP FOREIGN KEY fk_orphan_close_admin;
