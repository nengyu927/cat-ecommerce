# 商品圖與即時購物車預覽
## 本批範圍
優先完成使用者指定的 10 張商品圖，再加右側即時購物車預覽。原定優惠碼失效復原往後排；不宣稱 M01 全部完成。

## 圖片
- 使用內建 imagegen，逐項生成並目視檢查 10 張統一奶油白／森林綠風格 PNG。
- 保存：frontend/public/product-images/；前台、Admin 的獨立建置均複製 public 內容。
- 為 AI 泛用包裝示意圖，不是商品品牌官方照片，不模仿品牌識別；列表／詳細頁有示意提示。
- Flyway V13 按商品 slug + 原始 placeholder URL 精確比對，只換原種子圖，不改 V2、不覆蓋自訂圖片。
- 詳細頁、購物車、推薦商品、Admin 圖片編輯預覽以 ProductImage 統一處理圖片失敗，本地 SVG 後備，不依賴 placeholder 外站。
- 圖片為原尺寸 PNG，約 19 MB 合計；正式部署下一階段應製作授權實拍圖／多尺寸 WebP 與 CDN。現在保留生成原件。

## 右側購物車
- 點頁首購物車按鈕開啟；商品詳細頁加入成功且仍停在同一商品時自動開啟，晚到回應不打開別頁側欄。
- 顯示圖片、名稱、規格、件數、單價與商品小計；運費與優惠由結帳確認，不在側欄猜算。
- 加減數量、庫存上限、移除、完整購物車與結帳入口。
- 開啟重新同步，失敗保留舊資料但鎖定寫入／結帳，提供重試；寫入失敗也需要重新同步。
- 沿用會員 epoch 隔離與請求排隊。關閉、換頁、換會員後不顯示舊回應錯誤；登出清除資料並關閉。
- 原生 dialog 管理焦點、背景不可操作、Esc、背景點擊、身體捲動鎖定與還原；手機滿寬，尊重減少動畫設定。
- 完整 /cart 與 /checkout 路由保留，後台不引入商城側欄。

## 驗證
新增 frontend/scripts/cart-drawer.cjs，涵蓋桌機1440與手機390、真圖載入、焦點停留、數量、庫存、移除、失敗重試、Esc、捲動還原與登出訪客狀態。
更新既有 checkout-outcome、checkout-identity、stability、product-cart-ui、SQL 真實購物 E2E 的入口操作。
SQL 整合新增 V13／10張圖映射檢查。當次實際結果另列於交付回報。

2026-10-09 最終驗證：前後台 build 通過；側欄桌機／手機 2 組、商品加入跨頁 8 組、購物車同步 10 組、購物車 store 8 組、商品素材路由 11 組、穩定性 12 組，以及結帳 outcome／identity 回歸通過。後端 162 與 SQL 整合 22 項零失敗，真實 Edge 全購物流程通過。

展示持久資料庫已升級到 V13：會員 2、訂單 1、購物車明細 0，與升級前一致；10 個商品圖映射與前後台共 20 個圖片回應均通過。保存三張即時展示截圖，檢查未修改業務資料。

## 下一步
回到結帳優惠碼失效／門檻變動時的欄位保留與重新報價流程；圖片正式商用前需換成有授權的品牌實拍素材。

## 可重現 prompt set
每張共用以下 prompt，只替換 Subject：
```
Use case: product-mockup
Asset type: square product catalogue image for a demonstration cat-supply ecommerce website.
Subject: [下列逐張 subject].
Style/medium: polished photorealistic studio product mockup, tangible matte packaging and realistic materials.
Scene/backdrop: clean warm ivory seamless studio background matching a forest-green and cream storefront.
Composition: one centered product composition, completely visible, generous margins, three-quarter view, soft natural studio shadow, no border or collage.
Constraints: no real brand identity or imitation branded packaging, no printed words, letters, numbers, logos or watermark; no cats or humans. This is a generic illustrative product image, not an official product photo.
```
- `royal-canin-kitten-dry.png`: a small upright ivory kitten dry-food pouch with a muted terracotta band and a simple unbranded kitten silhouette, with a few brown dry kibble pieces beside its base
- `orijen-cat-dry.png`: a premium upright dark forest-green adult grain-free cat-food pouch with a beige band and simple unbranded cat silhouette, with a few brown dry kibble pieces beside its base
- `seeds-wet-food.png`: one open shallow metal cat wet-food can showing realistic shredded meat, and its detached plain cream and sage-green lid leaning beside it
- `lysine-supplement.png`: a short opaque ivory cat supplement powder jar with a forest-green screw cap and a simple unbranded cat silhouette, with a small measuring scoop holding white powder
- `hairball-gel.png`: a cream squeeze tube of cat hairball paste with a terracotta band and green cap, beside a small natural-looking ribbon of amber gel
- `feather-wand-toy.png`: a cat teaser wand with a slender natural wooden handle, thin dark cord, soft cream and muted terracotta feathers and one tiny metal bell, fully visible arranged diagonally
- `laser-cat-toy.png`: a compact white and muted sage-green automatic cat laser toy, rounded sturdy base with a small dark lens and USB charging port, no visible laser beam
- `freeze-dried-chicken.png`: a clear-front resealable kraft pouch filled with small porous freeze-dried chicken cubes, with a few pale golden cubes in front, simple forest-green band, unbranded
- `creamy-treat-tube.png`: three slim sealed cat creamy treat sachets arranged neatly with cream, muted terracotta and sage-green color blocking, one sachet open showing a small dab of smooth tuna paste
- `tofu-litter.png`: a large sturdy resealable ivory tofu cat litter pouch with a sage-green lower section, simple unbranded cat silhouette, with a small mound of pale cylindrical tofu litter pellets in front
