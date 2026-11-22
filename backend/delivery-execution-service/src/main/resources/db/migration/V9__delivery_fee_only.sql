-- SwiftDrop collects only the delivery fee, from the sender: the recipient never pays anything. V8 was never
-- released with cash on delivery, so the only such rows are test data.
DELETE FROM cash_entries WHERE kind = 'COD';

ALTER TABLE cash_entries DROP CONSTRAINT uk_cash_entries_once;
ALTER TABLE cash_entries DROP COLUMN kind;
ALTER TABLE cash_entries ADD CONSTRAINT uk_cash_entries_once UNIQUE (delivery_id, entry_type);
