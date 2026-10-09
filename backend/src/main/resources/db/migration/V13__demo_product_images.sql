-- Replace only original seed placeholders. Never overwrite uploaded/custom images.
UPDATE i SET url = N'/product-images/' + p.slug + N'.png',
             alt_text = p.name + N'（AI 商品示意圖，非品牌官方照片）'
FROM product_image i
JOIN product p ON p.id = i.product_id
JOIN (VALUES
 (N'royal-canin-kitten-dry', N'https://placehold.co/600x600?text=Royal+Canin+Kitten'),
 (N'orijen-cat-dry', N'https://placehold.co/600x600?text=Orijen+Cat'),
 (N'seeds-wet-food', N'https://placehold.co/600x600?text=Wet+Food'),
 (N'lysine-supplement', N'https://placehold.co/600x600?text=Lysine'),
 (N'hairball-gel', N'https://placehold.co/600x600?text=Hairball+Gel'),
 (N'feather-wand-toy', N'https://placehold.co/600x600?text=Feather+Toy'),
 (N'laser-cat-toy', N'https://placehold.co/600x600?text=Laser+Toy'),
 (N'freeze-dried-chicken', N'https://placehold.co/600x600?text=Freeze+Dried'),
 (N'creamy-treat-tube', N'https://placehold.co/600x600?text=Creamy+Treat'),
 (N'tofu-litter', N'https://placehold.co/600x600?text=Tofu+Litter')
) seed(slug, old_url) ON p.slug = seed.slug AND i.url = seed.old_url;
