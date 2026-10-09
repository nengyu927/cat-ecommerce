-- Legacy orders remain NULL; only requests carrying an identity are deduplicated.
ALTER TABLE orders ADD request_id NVARCHAR(36) NULL, request_hash NVARCHAR(64) NULL;
GO
CREATE UNIQUE INDEX ux_orders_member_request
    ON orders(member_id, request_id) WHERE request_id IS NOT NULL;
GO
ALTER TABLE orders ADD CONSTRAINT ck_orders_request_identity
    CHECK ((request_id IS NULL AND request_hash IS NULL)
        OR (request_id IS NOT NULL AND request_hash IS NOT NULL));
