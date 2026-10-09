CREATE TABLE wishlist_item (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_wishlist_member_product UNIQUE (member_id, product_id),
    CONSTRAINT fk_wishlist_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES product(id)
);
CREATE INDEX ix_wishlist_member_id ON wishlist_item(member_id, id DESC);
