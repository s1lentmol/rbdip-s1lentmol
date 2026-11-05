ALTER TABLE customers ADD COLUMN first_name VARCHAR(255);
ALTER TABLE customers ADD COLUMN last_name VARCHAR(255);

CREATE FUNCTION sync_customer_names() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.full_name IS DISTINCT FROM OLD.full_name THEN
            NEW.first_name := CASE WHEN strpos(NEW.full_name, ' ') IN (0, length(NEW.full_name))
                THEN NEW.full_name ELSE split_part(NEW.full_name, ' ', 1) END;
            NEW.last_name := CASE WHEN strpos(NEW.full_name, ' ') IN (0, length(NEW.full_name)) THEN ''
                ELSE substr(NEW.full_name, strpos(NEW.full_name, ' ') + 1) END;
            RETURN NEW;
        END IF;
    END IF;

    IF NEW.first_name IS NOT NULL AND NEW.last_name IS NOT NULL THEN
        NEW.full_name := NEW.first_name
            || CASE WHEN NEW.last_name = '' THEN '' ELSE ' ' || NEW.last_name END;
    ELSE
        NEW.first_name := CASE WHEN strpos(NEW.full_name, ' ') IN (0, length(NEW.full_name))
                THEN NEW.full_name ELSE split_part(NEW.full_name, ' ', 1) END;
        NEW.last_name := CASE WHEN strpos(NEW.full_name, ' ') IN (0, length(NEW.full_name)) THEN ''
            ELSE substr(NEW.full_name, strpos(NEW.full_name, ' ') + 1) END;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER customers_sync_names
BEFORE INSERT OR UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION sync_customer_names();
