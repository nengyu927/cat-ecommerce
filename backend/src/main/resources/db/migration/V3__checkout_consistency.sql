ALTER TABLE product_sku ADD version INT NOT NULL CONSTRAINT df_sku_version DEFAULT 0;
ALTER TABLE orders ADD version INT NOT NULL CONSTRAINT df_order_version DEFAULT 0;
ALTER TABLE orders ADD payment_method NVARCHAR(20) NOT NULL CONSTRAINT df_order_payment DEFAULT N'COD';
-- Execute separately so SQL Server resolves the newly added column after ALTER TABLE.
EXEC(N'ALTER TABLE orders ADD CONSTRAINT ck_order_payment CHECK (payment_method IN (N''COD'', N''MOCK''));');
CREATE INDEX ix_orders_member_created ON orders(member_id, created_at);
CREATE INDEX ix_product_category_status ON product(category_id, status, is_active);
