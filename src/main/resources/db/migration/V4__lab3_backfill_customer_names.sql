UPDATE customers
SET first_name = CASE WHEN strpos(full_name, ' ') IN (0, length(full_name))
                THEN full_name ELSE split_part(full_name, ' ', 1) END,
    last_name = CASE WHEN strpos(full_name, ' ') IN (0, length(full_name)) THEN ''
        ELSE substr(full_name, strpos(full_name, ' ') + 1) END
WHERE first_name IS NULL OR last_name IS NULL;

ALTER TABLE customers
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL,
    ADD CONSTRAINT customers_name_contact_key
        UNIQUE NULLS NOT DISTINCT (first_name, last_name, address, phone);
