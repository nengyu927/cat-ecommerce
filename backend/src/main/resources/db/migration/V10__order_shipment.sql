CREATE TABLE order_shipment (
 id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE,
 carrier NVARCHAR(80) NOT NULL,
 tracking_number NVARCHAR(100) NOT NULL,
 actor_member_id BIGINT NOT NULL,
 shipped_at DATETIME2(3) NOT NULL,
 CONSTRAINT fk_shipment_order FOREIGN KEY(order_id) REFERENCES orders(id)
);
