-- Explicit before/after effects in every scored result, including NO_ANSWER.
-- ACCEPTED_UNSCORED keeps NULL; no invented outcome on cancellation.
ALTER TABLE answer ADD CONSTRAINT ck_answer_effect_snapshot CHECK (
    result_snapshot IS NULL OR JSON_SCHEMA_VALID(
        '{"type":"object","required":["hasMomentumBefore","hasRecoveryBefore","hasMomentumAfter","hasRecoveryAfter"],"properties":{"hasMomentumBefore":{"type":"boolean"},"hasRecoveryBefore":{"type":"boolean"},"hasMomentumAfter":{"type":"boolean"},"hasRecoveryAfter":{"type":"boolean"}}}',
        result_snapshot));
