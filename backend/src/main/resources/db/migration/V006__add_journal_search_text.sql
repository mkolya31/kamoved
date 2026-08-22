ALTER TABLE journal_entry ADD COLUMN search_text TEXT NOT NULL DEFAULT '';

UPDATE entry_contact
SET normalized_phone = CONCAT('7', SUBSTRING(normalized_phone, 2))
WHERE LENGTH(normalized_phone) = 11 AND normalized_phone LIKE '8%';

UPDATE journal_entry entry
SET search_text = LOWER(REPLACE(CONCAT(
    COALESCE(entry.delivery_address, ''), ' ',
    COALESCE((
        SELECT STRING_AGG(COALESCE(contact.name, ''), ' ')
        FROM entry_contact contact
        WHERE contact.journal_entry_id = entry.id
    ), ''), ' ',
    COALESCE((
        SELECT STRING_AGG(COALESCE(contact.normalized_phone, ''), ' ')
        FROM entry_contact contact
        WHERE contact.journal_entry_id = entry.id
    ), ''), ' ',
    COALESCE((
        SELECT STRING_AGG(COALESCE(item.name, ''), ' ')
        FROM journal_entry_item item
        WHERE item.journal_entry_id = entry.id
    ), '')
), 'ё', 'е'));
