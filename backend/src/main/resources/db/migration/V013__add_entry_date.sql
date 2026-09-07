ALTER TABLE journal_entry ADD COLUMN entry_date DATE;
UPDATE journal_entry SET entry_date = CAST(created_at AT TIME ZONE 'Europe/Moscow' AS DATE);
ALTER TABLE journal_entry ALTER COLUMN entry_date SET NOT NULL;
CREATE INDEX idx_journal_entry_date ON journal_entry (entry_date DESC, created_at DESC, id DESC);

ALTER TABLE journal_payment ADD COLUMN received_date_only BOOLEAN NOT NULL DEFAULT FALSE;
