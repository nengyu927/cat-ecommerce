# 前台生活選品素材

使用內建 image_gen 製作原創示意圖，再以 JPEG quality 85 編碼，沒有使用外部品牌照片。原始圖保留於 Codex generated_images；網站所需版本已存入專案。

## 主視覺

檔案：`public/campaigns/cat-lifestyle-hero.jpg`

最終提示詞：

> Use case: photorealistic-natural. Asset type: premium cat lifestyle e-commerce hero image. Create an original landscape editorial photograph, 1536 x 1024 composition: a healthy cream British shorthair cat resting beside a sculptural warm ivory ceramic cat bowl and linen cushion in a serene sunlit apartment. Natural oat, pale travertine, subtle muted sage green accents, warm window shadows, refined tactile textures, understated high-end Japanese lifestyle magazine atmosphere. Cat positioned center-right, full natural face visible, calm expression, lots of negative space at left and above. No text, logos, watermark, people, plants, packaging, or brand marks. Photorealistic fur and correct anatomy; no illustration.

## 商品情境圖

共同最終提示詞，將 Subject 換為下表內容：

> Use case: product-mockup. Asset type: original fictional cat supplies catalog product image. Premium studio photograph, square composition. Subject: [subject]. Soft ivory seamless studio background, neutral oat and sage palette, realistic material texture, soft directional window lighting and subtle grounded shadow. Product entirely visible, centered with generous margins, refined Japanese lifestyle retail aesthetic, no cats, no people, no text, logos, packaging or watermark. This is a fictional demonstration product, not an existing brand.

| 儲存位置 | Subject |
|---|---|
| `public/product-images/ceramic-bowl.jpg` | A sculptural matte sage green elevated ceramic cat feeding bowl, beside one smaller ivory ceramic water bowl, warm travertine plinth |
| `public/product-images/linen-cat-bed.jpg` | An oval oatmeal linen cat bed with thick ivory cushion, natural woven texture, empty, gentle inviting shape |
| `public/product-images/cat-scratcher.jpg` | A contemporary curved corrugated cardboard cat scratching lounge with walnut-colored trim, tactile cardboard ridges |
| `public/product-images/cat-grooming.jpg` | A sage green cat grooming brush with rounded metal pins and an ivory handled fine-tooth comb arranged elegantly |
| `public/product-images/cat-carrier.jpg` | A structured beige fabric cat travel carrier with dark sage mesh ventilation panels and wide secure carrying handle |

新增商品屬虛構演示選品，同類商品共用情境示意圖；前台保留商品示意圖標記。活動內容為生活企劃，不包含未實作的折扣或贈品承諾。
