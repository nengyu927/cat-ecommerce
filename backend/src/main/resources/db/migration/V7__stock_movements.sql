-- Historical identifiers and labels are snapshots, intentionally not cascading FKs.
-- No backfill: pre-existing stock is not a reconstructed movement history.
CREATE TABLE stock_movement (
 id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
 sku_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 product_name NVARCHAR(200) NOT NULL,
 sku_code NVARCHAR(64) NOT NULL,
 reason NVARCHAR(30) NOT NULL,
 actor_member_id BIGINT NULL,
 before_stock INT NOT NULL,
 after_stock INT NOT NULL,
 quantity_delta INT NOT NULL,
 order_id BIGINT NULL,
 order_no NVARCHAR(32) NULL,
 created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT ck_stock_movement_quantities CHECK (before_stock>=0 AND after_stock>=0 AND quantity_delta=after_stock-before_stock AND quantity_delta<>0),
 CONSTRAINT ck_stock_movement_reason CHECK (reason IN ('SKU_CREATED','ADMIN_ADJUSTMENT','ORDER_PLACED','ORDER_CANCELLED'))
);
CREATE INDEX ix_stock_movement_sku_id ON stock_movement(sku_id,id DESC);
CREATE INDEX ix_stock_movement_order_id ON stock_movement(order_id,id DESC);
