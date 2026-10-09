-- =====================================================================
-- V1__init.sql  |  Cat Shop — initial schema (Microsoft SQL Server 2022)
-- NOTE: Flyway does NOT understand the T-SQL "GO" batch separator.
--       Each statement is terminated with a semicolon instead.
-- =====================================================================

-- ---------- member ----------
CREATE TABLE member (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    email         NVARCHAR(255)  NOT NULL,
    password_hash NVARCHAR(255)  NOT NULL,
    name          NVARCHAR(100)  NOT NULL,
    phone         NVARCHAR(30)   NULL,
    role          NVARCHAR(20)   NOT NULL CONSTRAINT df_member_role   DEFAULT N'CUSTOMER',
    status        NVARCHAR(20)   NOT NULL CONSTRAINT df_member_status DEFAULT N'ACTIVE',
    created_at    DATETIME2(3)   NOT NULL CONSTRAINT df_member_created DEFAULT SYSUTCDATETIME(),
    updated_at    DATETIME2(3)   NOT NULL CONSTRAINT df_member_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_member PRIMARY KEY (id),
    CONSTRAINT uq_member_email UNIQUE (email),
    CONSTRAINT ck_member_role   CHECK (role IN (N'CUSTOMER', N'ADMIN')),
    CONSTRAINT ck_member_status CHECK (status IN (N'ACTIVE', N'DISABLED'))
);

-- ---------- address ----------
CREATE TABLE address (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    member_id     BIGINT         NOT NULL,
    receiver_name NVARCHAR(100)  NOT NULL,
    phone         NVARCHAR(30)   NOT NULL,
    city          NVARCHAR(50)   NOT NULL,
    district      NVARCHAR(50)   NULL,
    line1         NVARCHAR(200)  NOT NULL,
    line2         NVARCHAR(200)  NULL,
    postal_code   NVARCHAR(20)   NULL,
    is_default    BIT            NOT NULL CONSTRAINT df_address_default DEFAULT 0,
    created_at    DATETIME2(3)   NOT NULL CONSTRAINT df_address_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_address PRIMARY KEY (id),
    CONSTRAINT fk_address_member FOREIGN KEY (member_id) REFERENCES member (id)
);
CREATE INDEX ix_address_member ON address (member_id);

-- ---------- category ----------
CREATE TABLE category (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    name       NVARCHAR(100) NOT NULL,
    slug       NVARCHAR(120) NOT NULL,
    parent_id  BIGINT        NULL,
    sort_order INT           NOT NULL CONSTRAINT df_category_sort   DEFAULT 0,
    is_active  BIT           NOT NULL CONSTRAINT df_category_active DEFAULT 1,
    created_at DATETIME2(3)  NOT NULL CONSTRAINT df_category_created DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uq_category_slug UNIQUE (slug),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
);
CREATE INDEX ix_category_parent ON category (parent_id);

-- ---------- product ----------
CREATE TABLE product (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    category_id BIGINT         NOT NULL,
    name        NVARCHAR(200)  NOT NULL,
    slug        NVARCHAR(220)  NOT NULL,
    description NVARCHAR(MAX)  NULL,
    brand       NVARCHAR(100)  NULL,
    base_price  DECIMAL(10,2)  NOT NULL,
    status      NVARCHAR(20)   NOT NULL CONSTRAINT df_product_status DEFAULT N'ON_SALE',
    is_active   BIT            NOT NULL CONSTRAINT df_product_active DEFAULT 1,
    created_at  DATETIME2(3)   NOT NULL CONSTRAINT df_product_created DEFAULT SYSUTCDATETIME(),
    updated_at  DATETIME2(3)   NOT NULL CONSTRAINT df_product_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT uq_product_slug UNIQUE (slug),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_product_price CHECK (base_price >= 0),
    CONSTRAINT ck_product_status CHECK (status IN (N'ON_SALE', N'OFF_SHELF', N'SOLD_OUT'))
);
CREATE INDEX ix_product_category ON product (category_id);
CREATE INDEX ix_product_status   ON product (status, is_active);

-- ---------- product_image ----------
CREATE TABLE product_image (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    product_id BIGINT        NOT NULL,
    url        NVARCHAR(500) NOT NULL,
    alt_text   NVARCHAR(200) NULL,
    sort_order INT           NOT NULL CONSTRAINT df_pimg_sort    DEFAULT 0,
    is_primary BIT           NOT NULL CONSTRAINT df_pimg_primary DEFAULT 0,
    CONSTRAINT pk_product_image PRIMARY KEY (id),
    CONSTRAINT fk_pimg_product FOREIGN KEY (product_id) REFERENCES product (id)
);
CREATE INDEX ix_pimg_product ON product_image (product_id);

-- ---------- product_sku ----------
CREATE TABLE product_sku (
    id           BIGINT IDENTITY(1,1) NOT NULL,
    product_id   BIGINT        NOT NULL,
    sku_code     NVARCHAR(64)  NOT NULL,
    spec_name    NVARCHAR(200) NOT NULL,
    price        DECIMAL(10,2) NOT NULL,
    stock        INT           NOT NULL CONSTRAINT df_sku_stock   DEFAULT 0,
    safety_stock INT           NOT NULL CONSTRAINT df_sku_safety  DEFAULT 0,
    is_active    BIT           NOT NULL CONSTRAINT df_sku_active  DEFAULT 1,
    created_at   DATETIME2(3)  NOT NULL CONSTRAINT df_sku_created DEFAULT SYSUTCDATETIME(),
    updated_at   DATETIME2(3)  NOT NULL CONSTRAINT df_sku_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_product_sku PRIMARY KEY (id),
    CONSTRAINT uq_sku_code UNIQUE (sku_code),
    CONSTRAINT fk_sku_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_sku_price CHECK (price >= 0),
    CONSTRAINT ck_sku_stock CHECK (stock >= 0)
);
CREATE INDEX ix_sku_product ON product_sku (product_id);

-- ---------- cart ----------
CREATE TABLE cart (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    member_id  BIGINT       NOT NULL,
    created_at DATETIME2(3) NOT NULL CONSTRAINT df_cart_created DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL CONSTRAINT df_cart_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_cart PRIMARY KEY (id),
    CONSTRAINT uq_cart_member UNIQUE (member_id),
    CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES member (id)
);

-- ---------- cart_item ----------
CREATE TABLE cart_item (
    id         BIGINT IDENTITY(1,1) NOT NULL,
    cart_id    BIGINT        NOT NULL,
    sku_id     BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    created_at DATETIME2(3)  NOT NULL CONSTRAINT df_citem_created DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3)  NOT NULL CONSTRAINT df_citem_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_cart_item PRIMARY KEY (id),
    CONSTRAINT uq_cart_item UNIQUE (cart_id, sku_id),
    CONSTRAINT fk_citem_cart FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE CASCADE,
    CONSTRAINT fk_citem_sku  FOREIGN KEY (sku_id)  REFERENCES product_sku (id),
    CONSTRAINT ck_citem_qty CHECK (quantity > 0)
);
CREATE INDEX ix_citem_cart ON cart_item (cart_id);
CREATE INDEX ix_citem_sku  ON cart_item (sku_id);

-- ---------- orders ----------
CREATE TABLE orders (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    order_no         NVARCHAR(32)  NOT NULL,
    member_id        BIGINT        NOT NULL,
    status           NVARCHAR(20)  NOT NULL CONSTRAINT df_orders_status DEFAULT N'PENDING',
    total_amount     DECIMAL(10,2) NOT NULL,
    shipping_fee     DECIMAL(10,2) NOT NULL CONSTRAINT df_orders_ship DEFAULT 0,
    receiver_name    NVARCHAR(100) NOT NULL,
    receiver_phone   NVARCHAR(30)  NOT NULL,
    shipping_address NVARCHAR(500) NOT NULL,
    remark           NVARCHAR(500) NULL,
    created_at       DATETIME2(3)  NOT NULL CONSTRAINT df_orders_created DEFAULT SYSUTCDATETIME(),
    updated_at       DATETIME2(3)  NOT NULL CONSTRAINT df_orders_updated DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_no UNIQUE (order_no),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_orders_status CHECK (status IN (N'PENDING', N'PAID', N'SHIPPED', N'COMPLETED', N'CANCELLED')),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0)
);
CREATE INDEX ix_orders_member ON orders (member_id);
CREATE INDEX ix_orders_status ON orders (status, created_at);

-- ---------- order_item ----------
CREATE TABLE order_item (
    id           BIGINT IDENTITY(1,1) NOT NULL,
    order_id     BIGINT        NOT NULL,
    sku_id       BIGINT        NULL,
    product_name NVARCHAR(200) NOT NULL,
    sku_spec     NVARCHAR(200) NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    quantity     INT           NOT NULL,
    subtotal     DECIMAL(10,2) NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (id),
    CONSTRAINT fk_oitem_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_oitem_sku   FOREIGN KEY (sku_id)   REFERENCES product_sku (id),
    CONSTRAINT ck_oitem_qty CHECK (quantity > 0)
);
CREATE INDEX ix_oitem_order ON order_item (order_id);
CREATE INDEX ix_oitem_sku   ON order_item (sku_id);
