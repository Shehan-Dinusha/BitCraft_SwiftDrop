-- A failed delivery goes back to the hub, where it is tried again or returned to the sender.
ALTER TABLE delivery_sessions
    ADD COLUMN proof_since TIMESTAMP WITH TIME ZONE,
    ADD COLUMN returned_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN delivery_sessions.proof_since IS
    'Proof from before this moment belongs to an earlier attempt and does not count';
