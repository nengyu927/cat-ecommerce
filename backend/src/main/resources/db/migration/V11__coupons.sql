CREATE TABLE coupon (
 id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
 code NVARCHAR(32) NOT NULL UNIQUE,
 discount_amount DECIMAL(10,2) NOT NULL,
 minimum_amount DECIMAL(10,2) NOT NULL,
 starts_at DATETIME2(3) NOT NULL,
 ends_at DATETIME2(3) NOT NULL,
 max_uses INT NOT NULL,
 active BIT NOT NULL,
 version INT NOT NULL DEFAULT 0,
 CONSTRAINT ck_coupon_amount CHECK(discount_amount>0 AND minimum_amount>=0),
 CONSTRAINT ck_coupon_limit CHECK(max_uses>0),
 CONSTRAINT ck_coupon_period CHECK(ends_at>starts_at)
);
ALTER TABLE orders ADD coupon_id BIGINT NULL, coupon_code NVARCHAR(32) NULL,
 discount_amount DECIMAL(10,2) NOT NULL CONSTRAINT df_order_discount DEFAULT 0;
CREATE INDEX ix_order_coupon_usage ON orders(coupon_id, status, member_id);
