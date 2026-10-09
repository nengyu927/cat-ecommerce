-- =====================================================================
-- V2__seed.sql  |  Cat Shop — seed data (categories, products, SKUs, demo users)
-- =====================================================================

-- ---------- categories ----------
INSERT INTO category (name, slug, parent_id, sort_order, is_active) VALUES
 (N'飼料',   N'food',        NULL, 1, 1),
 (N'保健品', N'health',      NULL, 2, 1),
 (N'玩具',   N'toys',        NULL, 3, 1),
 (N'零食',   N'treats',      NULL, 4, 1),
 (N'貓砂',   N'litter',      NULL, 5, 1),
 (N'主食罐', N'wet-food',    1,    1, 1),
 (N'乾糧',   N'dry-food',    1,    2, 1);

-- ---------- products ----------
INSERT INTO product (category_id, name, slug, description, brand, base_price, status, is_active) VALUES
 (7, N'皇家 幼貓專用乾糧', N'royal-canin-kitten-dry', N'專為 4-12 個月幼貓設計，高蛋白配方，添加 DHA 幫助腦部發育。', N'Royal Canin', 890.00, N'ON_SALE', 1),
 (7, N'渴望 成貓無穀乾糧', N'orijen-cat-dry',        N'85% 肉類含量，低碳水無穀配方，貼近貓咪天然飲食。',       N'Orijen',      1280.00, N'ON_SALE', 1),
 (6, N'惜時 鮮肉主食罐',   N'seeds-wet-food',        N'95% 鮮肉含量，無膠無穀，補水首選。',                     N'惜時',        65.00,  N'ON_SALE', 1),
 (2, N'離胺酸 貓用保健粉', N'lysine-supplement',     N'幫助抑制皰疹病毒，維護呼吸道與眼睛健康。',               N'VetriScience', 520.00, N'ON_SALE', 1),
 (2, N'化毛膏 鮪魚口味',   N'hairball-gel',          N'幫助排出毛球，添加膳食纖維與魚油。',                     N'Beaphar',     380.00, N'ON_SALE', 1),
 (3, N'逗貓棒 羽毛款',     N'feather-wand-toy',      N'天然羽毛搭配鈴鐺，激發狩獵本能。',                       N'CattyMan',    199.00, N'ON_SALE', 1),
 (3, N'電動雷射逗貓機',    N'laser-cat-toy',         N'自動變換軌跡，USB 充電，可定時。',                       N'PetSafe',     690.00, N'ON_SALE', 1),
 (4, N'凍乾雞肉丁',        N'freeze-dried-chicken',  N'100% 純雞胸肉凍乾，無添加，高適口性。',                   N'汪喵星球',    320.00, N'ON_SALE', 1),
 (4, N'化毛肉泥條',        N'creamy-treat-tube',     N'鮪魚口味肉泥，添加化毛成分，好餵食。',                   N'Ciao',        45.00,  N'ON_SALE', 1),
 (5, N'豆腐砂 原味',       N'tofu-litter',           N'可沖馬桶，凝結力強，低粉塵。',                           N'CatLitter',   260.00, N'ON_SALE', 1);

-- ---------- product_image ----------
INSERT INTO product_image (product_id, url, alt_text, sort_order, is_primary) VALUES
 (1, N'https://placehold.co/600x600?text=Royal+Canin+Kitten', N'皇家幼貓乾糧', 0, 1),
 (2, N'https://placehold.co/600x600?text=Orijen+Cat',         N'渴望成貓乾糧', 0, 1),
 (3, N'https://placehold.co/600x600?text=Wet+Food',           N'惜時主食罐',   0, 1),
 (4, N'https://placehold.co/600x600?text=Lysine',             N'離胺酸保健粉', 0, 1),
 (5, N'https://placehold.co/600x600?text=Hairball+Gel',       N'化毛膏',       0, 1),
 (6, N'https://placehold.co/600x600?text=Feather+Toy',        N'逗貓棒',       0, 1),
 (7, N'https://placehold.co/600x600?text=Laser+Toy',          N'雷射逗貓機',   0, 1),
 (8, N'https://placehold.co/600x600?text=Freeze+Dried',       N'凍乾雞肉丁',   0, 1),
 (9, N'https://placehold.co/600x600?text=Creamy+Treat',       N'化毛肉泥條',   0, 1),
 (10,N'https://placehold.co/600x600?text=Tofu+Litter',        N'豆腐砂',       0, 1);

-- ---------- product_sku ----------
INSERT INTO product_sku (product_id, sku_code, spec_name, price, stock, safety_stock, is_active) VALUES
 (1, N'RC-KIT-2KG',   N'2kg 裝',        890.00,  120, 20, 1),
 (1, N'RC-KIT-4KG',   N'4kg 裝',        1580.00, 60,  10, 1),
 (2, N'ORI-CAT-1.8KG',N'1.8kg 裝',      1280.00, 45,  10, 1),
 (2, N'ORI-CAT-5.4KG',N'5.4kg 裝',      3280.00, 15,  5,  1),
 (3, N'SEED-WET-80G', N'80g 單罐',      65.00,   500, 50, 1),
 (3, N'SEED-WET-24',  N'80g x 24 罐',   1480.00, 40,  10, 1),
 (4, N'LYS-100G',     N'100g 粉劑',     520.00,  80,  15, 1),
 (5, N'HB-GEL-100G',  N'100g 軟管',     380.00,  90,  15, 1),
 (6, N'TOY-FEATHER',  N'羽毛款',        199.00,  200, 30, 1),
 (7, N'TOY-LASER-USB',N'USB 充電款',    690.00,  70,  10, 1),
 (8, N'FD-CHK-30G',   N'30g 包',        320.00,  150, 20, 1),
 (9, N'CIAO-TUBE-4',  N'4 入組',        45.00,   800, 100,1),
 (10,N'LIT-TOFU-6L',  N'6L 包',         260.00,  110, 20, 1);

-- ---------- demo members ----------
-- password for both accounts is: password123
-- BCrypt hash generated with strength 10.
INSERT INTO member (email, password_hash, name, phone, role, status) VALUES
 (N'admin@catshop.local',  N'$2a$10$eI6jFHafIMm/2em8dRFlJ.TxjBywIP2J0jOs4Pl5dTluCrsUlGYd.', N'系統管理員', N'0900000000', N'ADMIN',    N'ACTIVE'),
 (N'customer@catshop.local',N'$2a$10$eI6jFHafIMm/2em8dRFlJ.TxjBywIP2J0jOs4Pl5dTluCrsUlGYd.', N'測試會員',   N'0911111111', N'CUSTOMER', N'ACTIVE');

-- ---------- demo address ----------
INSERT INTO address (member_id, receiver_name, phone, city, district, line1, line2, postal_code, is_default) VALUES
 (2, N'測試會員', N'0911111111', N'台北市', N'大安區', N'復興南路一段 100 號', N'5 樓', N'106', 1);
