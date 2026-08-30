ALTER TABLE journal_entry ADD COLUMN factory_ready_date DATE;
ALTER TABLE journal_entry ADD COLUMN factory_ready_attention BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE journal_entry ADD COLUMN factory_ready_confirmed_date DATE;
ALTER TABLE journal_entry ADD COLUMN factory_ready_reminder_start_date DATE;

CREATE INDEX idx_journal_entry_factory_ready_attention
    ON journal_entry (factory_ready_attention, factory_ready_date);

ALTER TABLE email_notification DROP CONSTRAINT chk_email_notification_status;
ALTER TABLE email_notification ADD CONSTRAINT chk_email_notification_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'CANCELLED'));
