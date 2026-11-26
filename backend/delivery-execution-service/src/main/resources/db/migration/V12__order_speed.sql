-- V12__order_speed.sql
-- The order's speed decides the route: Same day within one district goes straight to the recipient; Standard and
-- Express go through the hub, within one district too. Older sessions have none and keep the straight route.

ALTER TABLE delivery_sessions ADD COLUMN IF NOT EXISTS order_speed VARCHAR(32);

COMMENT ON COLUMN delivery_sessions.order_speed IS 'STANDARD, EXPRESS or SAME_DAY, from courier.assigned; null on older sessions';
