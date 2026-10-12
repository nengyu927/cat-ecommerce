const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '..')
const catalog = JSON.parse(fs.readFileSync(path.join(root, 'demo/catalog-expansion.json'), 'utf8'))
const sql = value => value == null ? 'NULL' : "N'" + String(value).replaceAll("'", "''") + "'"
const lines = ['-- Additive presentation catalog. Existing products, images and customer data are preserved.', '-- Fictional Meow Daily products and original illustrative images; no real brand claims.', '']
for (const category of catalog.categories) {
  const parent = category.parent ? `(SELECT id FROM category WHERE slug=${sql(category.parent)})` : 'NULL'
  lines.push(`INSERT INTO category (name,slug,parent_id,sort_order,is_active) SELECT ${sql(category.name)},${sql(category.slug)},${parent},${category.sortOrder},1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug=${sql(category.slug)});`)
}
for (const [index, product] of catalog.products.entries()) {
  const origin = ['food', 'dry-food', 'wet-food', 'treats', 'health', 'litter'].includes(product.category) ? '臺灣' : '中國'
  const storage = ['dry-food', 'wet-food', 'treats', 'health'].includes(product.category) ? '請置於陰涼乾燥處，開封後依包裝說明保存。' : '保持乾燥，避免陽光直射；清潔方式請依商品標示。'
  lines.push('', `INSERT INTO product (category_id,name,slug,description,brand,base_price,status,is_active,ingredients,suitable_age,origin,storage_instructions)
SELECT c.id,${sql(product.name)},${sql(product.slug)},${sql(product.description)},N'喵日常',${product.price},N'ON_SALE',1,${sql(product.ingredients)},${sql(product.age)},${sql(origin)},${sql(storage)} FROM category c WHERE c.slug=${sql(product.category)} AND NOT EXISTS (SELECT 1 FROM product WHERE slug=${sql(product.slug)});`,
  `INSERT INTO product_image (product_id,url,alt_text,sort_order,is_primary) SELECT p.id,${sql('/product-images/' + product.image)},${sql(product.name + '（原創商品示意圖）')},0,1 FROM product p WHERE p.slug=${sql(product.slug)} AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id=p.id);`,
  `INSERT INTO product_sku (product_id,sku_code,spec_name,price,stock,safety_stock,is_active) SELECT p.id,${sql('MD-COLLECTION-' + String(index + 1).padStart(3, '0'))},${sql(product.spec)},${product.price},${40 + index * 3},10,1 FROM product p WHERE p.slug=${sql(product.slug)} AND NOT EXISTS (SELECT 1 FROM product_sku s WHERE s.product_id=p.id);`)
}
fs.writeFileSync(path.join(root, 'src/main/resources/db/migration/V14__expanded_lifestyle_catalog.sql'), lines.join('\n') + '\n')
console.log(`Generated catalog migration: ${catalog.categories.length} categories, ${catalog.products.length} products with images and SKUs.`)
