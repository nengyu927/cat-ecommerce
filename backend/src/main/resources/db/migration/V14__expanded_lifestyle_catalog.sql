-- Additive presentation catalog. Existing products, images and customer data are preserved.
-- Fictional Meow Daily products and original illustrative images; no real brand claims.

INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT N'餐具與飲水',N'dining',NULL,6,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=N'dining');
INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT N'寢具與居家',N'beds',NULL,7,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=N'beds');
INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT N'抓板與跳台',N'scratchers',NULL,8,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=N'scratchers');
INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT N'清潔與美容',N'grooming',NULL,9,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=N'grooming');
INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT N'外出與旅行',N'travel',NULL,10,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=N'travel');
INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT N'陶瓷食碗',N'ceramic-bowls',(SELECT id FROM category WHERE slug=N'dining'),1,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=N'ceramic-bowls');
INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT N'舒眠貓窩',N'cat-beds',(SELECT id FROM category WHERE slug=N'beds'),1,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=N'cat-beds');
INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT N'外出提籠',N'carriers',(SELECT id FROM category WHERE slug=N'travel'),1,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=N'carriers');

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 鮭魚成貓主食',N'daily-salmon-dry',N'鮭魚搭配雞肉製作的日常乾糧，顆粒大小適中。可依主子的體重與活動量調整餵食份量。',N'喵日常',1180,N'ON_SALE',1,N'鮭魚、雞肉、豌豆、雞脂、魚油、牛磺酸、維生素與礦物質',N'1 歲以上成貓',N'臺灣',N'請置於陰涼乾燥處，開封後依包裝說明保存。' FROM category c WHERE c.slug=N'dry-food' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-salmon-dry');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/orijen-cat-dry.png',N'喵日常 鮭魚成貓主食（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-salmon-dry' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-001',N'鮭魚配方 / 1.8 公斤',1180,40,10,1 FROM product p WHERE p.slug=N'daily-salmon-dry' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 鮮雞南瓜主食罐',N'daily-chicken-wet',N'細緻雞肉搭配南瓜，方便分餐與日常補水。開罐後請冷藏，餵食前回溫。',N'喵日常',72,N'ON_SALE',1,N'雞肉、南瓜、雞湯、牛磺酸、維生素與礦物質',N'1 歲以上成貓',N'臺灣',N'請置於陰涼乾燥處，開封後依包裝說明保存。' FROM category c WHERE c.slug=N'wet-food' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-chicken-wet');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/seeds-wet-food.png',N'喵日常 鮮雞南瓜主食罐（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-chicken-wet' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-002',N'雞肉南瓜 / 80 公克',72,43,10,1 FROM product p WHERE p.slug=N'daily-chicken-wet' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 鮭魚凍乾小方塊',N'daily-salmon-freeze',N'一口大小的凍乾點心，可作為互動獎勵或主食拌料。建議少量餵食並提供飲水。',N'喵日常',380,N'ON_SALE',1,N'鮭魚',N'6 個月以上貓咪',N'臺灣',N'請置於陰涼乾燥處，開封後依包裝說明保存。' FROM category c WHERE c.slug=N'treats' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-salmon-freeze');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/freeze-dried-chicken.png',N'喵日常 鮭魚凍乾小方塊（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-salmon-freeze' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-003',N'鮭魚 / 40 公克',380,46,10,1 FROM product p WHERE p.slug=N'daily-salmon-freeze' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 鴨肉凍乾點心',N'daily-duck-freeze',N'輕巧酥鬆的鴨肉點心，適合陪玩後少量獎勵。夾鏈袋設計方便密封保存。',N'喵日常',340,N'ON_SALE',1,N'鴨肉',N'6 個月以上貓咪',N'臺灣',N'請置於陰涼乾燥處，開封後依包裝說明保存。' FROM category c WHERE c.slug=N'treats' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-duck-freeze');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/freeze-dried-chicken.png',N'喵日常 鴨肉凍乾點心（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-duck-freeze' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-004',N'鴨肉 / 35 公克',340,49,10,1 FROM product p WHERE p.slug=N'daily-duck-freeze' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 鮪魚滑順肉泥',N'daily-tuna-puree',N'單條包裝的鮪魚肉泥，適合一起度過親密互動時光。零食應搭配完整主食餵食。',N'喵日常',68,N'ON_SALE',1,N'鮪魚、水、雞肉、木薯澱粉',N'6 個月以上貓咪',N'臺灣',N'請置於陰涼乾燥處，開封後依包裝說明保存。' FROM category c WHERE c.slug=N'treats' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-tuna-puree');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/creamy-treat-tube.png',N'喵日常 鮪魚滑順肉泥（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-tuna-puree' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-005',N'鮪魚 / 14 公克 × 4 入',68,52,10,1 FROM product p WHERE p.slug=N'daily-tuna-puree' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 日常營養拌粉',N'daily-probiotic',N'獨立小包裝，方便依包裝建議份量拌入食物。特殊飲食需求請先諮詢獸醫。',N'喵日常',620,N'ON_SALE',1,N'酵母、膳食纖維、乳酸菌粉',N'1 歲以上成貓',N'臺灣',N'請置於陰涼乾燥處，開封後依包裝說明保存。' FROM category c WHERE c.slug=N'health' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-probiotic');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/lysine-supplement.png',N'喵日常 日常營養拌粉（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-probiotic' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-006',N'獨立包裝 / 30 包',620,55,10,1 FROM product p WHERE p.slug=N'daily-probiotic' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 低塵礦砂',N'daily-mineral-litter',N'無添加香氛，細顆粒觸感，適合日常清理。結塊請以一般垃圾處理，勿投入馬桶。',N'喵日常',360,N'ON_SALE',1,N'膨潤土',N'各年齡貓咪',N'臺灣',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'litter' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-mineral-litter');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/tofu-litter.png',N'喵日常 低塵礦砂（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-mineral-litter' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-007',N'無香款 / 8 公斤',360,58,10,1 FROM product p WHERE p.slug=N'daily-mineral-litter' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 植物纖維貓砂',N'daily-plant-litter',N'植物纖維顆粒搭配無香配方，日常換砂更輕鬆。使用後請密封袋裝丟棄。',N'喵日常',280,N'ON_SALE',1,N'豌豆纖維、玉米澱粉',N'各年齡貓咪',N'臺灣',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'litter' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-plant-litter');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/tofu-litter.png',N'喵日常 植物纖維貓砂（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-plant-litter' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-008',N'原味 / 6 公升',280,61,10,1 FROM product p WHERE p.slug=N'daily-plant-litter' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 羊毛互動球組',N'daily-wool-ball',N'輕巧羊毛球，適合追逐與撥弄。請陪同使用，玩具破損時停止使用。',N'喵日常',190,N'ON_SALE',1,N'羊毛纖維',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'toys' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-wool-ball');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/feather-wand-toy.png',N'喵日常 羊毛互動球組（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-wool-ball' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-009',N'奶油與鼠尾草色 / 3 入',190,64,10,1 FROM product p WHERE p.slug=N'daily-wool-ball' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 可收納躲貓隧道',N'daily-play-tunnel',N'可折疊收納的遊戲隧道，提供探索與躲藏空間。平放於地面，使用後可快速整理。',N'喵日常',690,N'ON_SALE',1,N'聚酯布、彈性支架',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'toys' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-play-tunnel');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/linen-cat-bed.jpg',N'喵日常 可收納躲貓隧道（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-play-tunnel' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-010',N'燕麥色 / 90 公分',690,67,10,1 FROM product p WHERE p.slug=N'daily-play-tunnel' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 鼠尾草高腳陶瓷碗',N'daily-sage-bowl',N'霧面釉色搭配穩固高腳底座，讓用餐角落融入居家風格。碗身可拆下清潔。',N'喵日常',580,N'ON_SALE',1,N'高溫燒製陶瓷',N'成貓',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'ceramic-bowls' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-sage-bowl');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/ceramic-bowl.jpg',N'喵日常 鼠尾草高腳陶瓷碗（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-sage-bowl' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-011',N'鼠尾草綠 / 350 毫升',580,70,10,1 FROM product p WHERE p.slug=N'daily-sage-bowl' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 奶油陶瓷雙碗組',N'daily-dual-bowl',N'一組安排用餐與飲水，圓潤碗口方便日常清潔。請定期換水並保持碗身乾淨。',N'喵日常',990,N'ON_SALE',1,N'高溫燒製陶瓷',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'ceramic-bowls' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-dual-bowl');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/ceramic-bowl.jpg',N'喵日常 奶油陶瓷雙碗組（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-dual-bowl' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-012',N'奶油白 / 食碗與水碗組',990,73,10,1 FROM product p WHERE p.slug=N'daily-dual-bowl' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 寬口飲水碗',N'daily-water-bowl',N'寬口淺碗設計，適合放在主子常經過的角落。無電力需求，清洗與換水都簡單。',N'喵日常',420,N'ON_SALE',1,N'陶瓷',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'dining' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-water-bowl');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/ceramic-bowl.jpg',N'喵日常 寬口飲水碗（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-water-bowl' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-013',N'奶油白 / 600 毫升',420,76,10,1 FROM product p WHERE p.slug=N'daily-water-bowl' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 亞麻舒眠貓窩',N'daily-linen-bed',N'柔軟厚墊與環抱邊緣，為午後小睡留一個舒服的位置。內墊可取出，清潔請依洗標。',N'喵日常',1280,N'ON_SALE',1,N'亞麻混紡布、聚酯填充棉',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'cat-beds' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-linen-bed');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/linen-cat-bed.jpg',N'喵日常 亞麻舒眠貓窩（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-linen-bed' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-014',N'燕麥色 / 中型',1280,79,10,1 FROM product p WHERE p.slug=N'daily-linen-bed' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 編織圓形貓窩',N'daily-woven-bed',N'自然色編織外型搭配可拆內墊，適合安靜的休息角落。請放置於室內乾燥處。',N'喵日常',1680,N'ON_SALE',1,N'棉繩、聚酯內墊',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'cat-beds' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-woven-bed');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/linen-cat-bed.jpg',N'喵日常 編織圓形貓窩（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-woven-bed' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-015',N'自然色 / 直徑 45 公分',1680,82,10,1 FROM product p WHERE p.slug=N'daily-woven-bed' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 四季柔軟休息墊',N'daily-rest-mat',N'薄型軟墊可放在沙發、窗邊或提籠內，讓熟悉的氣味陪著主子。可拆布套方便整理。',N'喵日常',790,N'ON_SALE',1,N'棉混紡布、聚酯填充棉',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'beds' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-rest-mat');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/linen-cat-bed.jpg',N'喵日常 四季柔軟休息墊（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-rest-mat' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-016',N'奶油色 / 60 × 45 公分',790,85,10,1 FROM product p WHERE p.slug=N'daily-rest-mat' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 曲線貓抓沙發',N'daily-scratch-lounge',N'弧形抓面結合休息空間，穩固放置即可使用。定期清理紙屑，磨耗後替換。',N'喵日常',880,N'ON_SALE',1,N'高密度瓦楞紙、木質飾板',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'scratchers' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-scratch-lounge');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/cat-scratcher.jpg',N'喵日常 曲線貓抓沙發（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-scratch-lounge' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-017',N'胡桃色 / 60 公分',880,88,10,1 FROM product p WHERE p.slug=N'daily-scratch-lounge' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 雙層抓板休息台',N'daily-scratch-platform',N'抓磨與休息兼顧的雙層平台，適合在客廳安排一個專屬位置。請依說明組裝並確認穩固。',N'喵日常',1480,N'ON_SALE',1,N'木板、高密度瓦楞紙',N'成貓',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'scratchers' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-scratch-platform');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/cat-scratcher.jpg',N'喵日常 雙層抓板休息台（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-scratch-platform' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-018',N'自然木色 / 雙層',1480,91,10,1 FROM product p WHERE p.slug=N'daily-scratch-platform' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 可替換貓抓板',N'daily-replace-scratcher',N'日常消耗型抓板，輕巧易替換。可平放於乾燥地面，讓主子自由抓磨。',N'喵日常',320,N'ON_SALE',1,N'瓦楞紙',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'scratchers' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-replace-scratcher');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/cat-scratcher.jpg',N'喵日常 可替換貓抓板（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-replace-scratcher' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-019',N'原色 / 2 入',320,94,10,1 FROM product p WHERE p.slug=N'daily-replace-scratcher' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 圓針梳毛刷',N'daily-groom-brush',N'圓針梳頭搭配易握手柄，適合溫柔整理毛髮。順著毛流輕梳，避免拉扯打結處。',N'喵日常',320,N'ON_SALE',1,N'ABS、金屬圓針',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'grooming' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-groom-brush');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/cat-grooming.jpg',N'喵日常 圓針梳毛刷（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-groom-brush' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-020',N'鼠尾草綠 / 單支',320,97,10,1 FROM product p WHERE p.slug=N'daily-groom-brush' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 細齒毛髮整理梳',N'daily-groom-comb',N'細齒梳方便局部整理，握柄輕巧。請以短時間、低壓力的方式陪主子完成日常梳理。',N'喵日常',250,N'ON_SALE',1,N'ABS、不鏽鋼',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'grooming' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-groom-comb');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/cat-grooming.jpg',N'喵日常 細齒毛髮整理梳（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-groom-comb' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-021',N'奶油白 / 單支',250,100,10,1 FROM product p WHERE p.slug=N'daily-groom-comb' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 梳毛入門工具組',N'daily-care-kit',N'日常梳理一次備齊，收納簡單，適合剛開始一起生活的家庭。工具使用後請清潔乾燥。',N'喵日常',520,N'ON_SALE',1,N'ABS、不鏽鋼',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'grooming' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-care-kit');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/cat-grooming.jpg',N'喵日常 梳毛入門工具組（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-care-kit' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-022',N'圓針刷與細齒梳 / 2 件組',520,103,10,1 FROM product p WHERE p.slug=N'daily-care-kit' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 輕量外出提籠',N'daily-soft-carrier',N'透氣網面搭配穩固底板，短途外出與就診方便攜帶。使用前請確認拉鍊與扣具完整。',N'喵日常',1980,N'ON_SALE',1,N'聚酯布、透氣網、支撐底板',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'carriers' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-soft-carrier');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/cat-carrier.jpg',N'喵日常 輕量外出提籠（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-soft-carrier' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-023',N'燕麥色 / 建議寵物 6 公斤以下',1980,106,10,1 FROM product p WHERE p.slug=N'daily-soft-carrier' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);

INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,N'喵日常 都會旅行提籠',N'daily-travel-carrier',N'寬版空間與側面通風設計，搭配可拆洗內墊。請先讓主子在家熟悉提籠，再規劃外出。',N'喵日常',2680,N'ON_SALE',1,N'耐磨聚酯布、透氣網、可拆內墊',N'各年齡貓咪',N'中國',N'保持乾燥，避免陽光直射；清潔方式請依商品標示。' FROM category c WHERE c.slug=N'carriers' AND NOT EXISTS (SELECT 1 FROM product WHERE slug=N'daily-travel-carrier');
INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,N'/product-images/cat-carrier.jpg',N'喵日常 都會旅行提籠（原創商品示意圖）',0,1 FROM product p WHERE p.slug=N'daily-travel-carrier' AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);
INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,N'MD-COLLECTION-024',N'沙岩色 / 寬版',2680,109,10,1 FROM product p WHERE p.slug=N'daily-travel-carrier' AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);
