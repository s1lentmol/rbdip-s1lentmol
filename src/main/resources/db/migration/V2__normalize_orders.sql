CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(50),
    UNIQUE NULLS NOT DISTINCT (full_name, address, phone)
);

ALTER TABLE orders ADD COLUMN customer_id BIGINT REFERENCES customers(id);
ALTER TABLE order_items ADD COLUMN product_id BIGINT REFERENCES products(id);

INSERT INTO customers (full_name, address, phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone
FROM orders;

UPDATE orders o
SET customer_id = c.id
FROM customers c
WHERE c.full_name = o.customer_full_name
  AND c.address IS NOT DISTINCT FROM o.customer_address
  AND c.phone IS NOT DISTINCT FROM o.customer_phone;

UPDATE order_items i
SET product_id = p.id
FROM (
    SELECT name, price, MIN(id) AS id
    FROM products
    GROUP BY name, price
    HAVING COUNT(*) = 1
) p
WHERE p.name = i.product_name AND p.price = i.product_price;

DO $$
DECLARE
    unmatched_id BIGINT;
BEGIN
    SELECT id INTO unmatched_id FROM order_items WHERE product_id IS NULL LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Cannot identify a unique catalog product for order_items.id=%', unmatched_id;
    END IF;
END;
$$;

ALTER TABLE orders
    ALTER COLUMN customer_id SET NOT NULL,
    DROP COLUMN customer_full_name,
    DROP COLUMN customer_address,
    DROP COLUMN customer_phone;

ALTER TABLE order_items
    ALTER COLUMN product_id SET NOT NULL,
    DROP COLUMN product_name,
    DROP COLUMN product_price;

CREATE INDEX orders_customer_id_idx ON orders(customer_id);
CREATE INDEX order_items_product_id_idx ON order_items(product_id);
