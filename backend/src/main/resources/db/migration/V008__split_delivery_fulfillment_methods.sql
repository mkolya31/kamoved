ALTER TABLE journal_entry
    DROP CONSTRAINT chk_journal_entry_fulfillment_method;

UPDATE journal_entry
SET fulfillment_method = 'DELIVERY_MARKET'
WHERE fulfillment_method = 'DELIVERY';

ALTER TABLE journal_entry
    ADD CONSTRAINT chk_journal_entry_fulfillment_method
        CHECK (fulfillment_method IS NULL OR fulfillment_method IN (
            'PICKUP_WAREHOUSE',
            'PICKUP_FACTORY',
            'DELIVERY_FACTORY',
            'DELIVERY_MARKET'
        ));
