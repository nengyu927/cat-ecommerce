# 前台只看現貨篩選

日期：2026-10-08。本批補上品牌／價格篩選後的現貨條件，已套用展示網站；不改訂單、購物車與結帳交易邏輯，不修改資料表或 Flyway V1～V6。重啟沿用持久資料庫，會員／訂單／購物車／收藏／商品筆數未變。

## 現貨定義與使用

- 前台所有商品、搜尋、分類商品頁新增「只看現貨」勾選框。
- 商品必須沿用前台可見性規則：啟用、ON_SALE、直接分類啟用；且至少一個 `ProductSku.isActive=true`、`stock>0`，才會符合現貨條件。
- 沒有 SKU、所有啟用 SKU 零庫存、只有停用 SKU 有庫存，都排除。多個有庫存 SKU 不會造成重複商品或錯誤的分頁總數。
- 與品牌、商品基本售價範圍、分類下層、關鍵字、排序一起在資料庫查詢。價格仍是 `Product.basePrice`，不是有庫存 SKU 的最低價格；品牌選單仍是全站可見品牌，不因現貨條件縮減。
- GET `/api/products?inStock=true` 開啟；省略或 `false` 維持原本商品列表，不排除缺貨商品。不可解析的後端參數回 400；前端網址只接受 true／false，錯誤可用清除條件恢復。
- 勾選回第一頁，網址保存 `inStock=true`；換頁、重新整理保持勾選，清除條件會一併取消。
- 現貨只是查詢當下的庫存，不保留庫存，也不保證結帳時仍有貨；既有後端結帳／加購檢查與交易鎖仍是最後依據。

## 程式碼依據

- `backend/src/main/java/com/catshop/repository/ProductRepository.java`：使用 correlated EXISTS 查至少一個可用 SKU，不以 collection JOIN 放大商品列，也不逐筆載入 SKU。
- `backend/src/main/java/com/catshop/service/ProductService.java`：新增帶 inStock 的 search；保留舊方法，預設 false，沿用價格驗證及穩定分頁。
- `backend/src/main/java/com/catshop/controller/ProductController.java`：公開列表新增預設 false 的 inStock 參數。既有公開商品 GET 授權規則不需更動。
- `frontend/src/views/ProductListView.vue`：勾選、網址恢復、非法網址提示、條件摘要與清除。
- `frontend/src/stores/product.js`：將現貨條件傳到 API，沿用 requestVersion 防舊結果覆蓋。
- `frontend/src/assets/main.css`：主題綠勾選框樣式。

## 驗證

- `ProductFilterIntegrationTest` 增加三項：多 SKU 防重複、零庫存／停用／無 SKU 排除、庫存與啟用變化即時反映、組合條件與分頁、匿名 HTTP 與非法參數。
- `npm run test:product-filters` 共 7 項通過，新增現貨勾選的組合條件、第一頁重置、換頁／重新整理、清除與非法網址恢復；無未處理頁面錯誤。
- 前台與 Admin 正式建置通過。
- 完整後端 91 項、獨立 SQL 整合 10 項通過，零失敗／錯誤。庫存變更測試曾因未取回保存後的版本號失敗，已修正測試資料操作並完整重跑。
- 真實瀏覽器整合在既有品牌／價格篩選後勾選現貨，再完成收藏、購物車、結帳、取消還庫存與登入授權。隔離測試資料庫與展示資料庫不同，測試結束只清除此次測試 DB。
- 展示網站現貨清單每件均有啟用且正庫存 SKU；品牌＋價格＋現貨組合回傳正確結果，非法參數回 400，前後台 HTTP 200。

本機 SQL Server Express 17.x；SQL Server 2022 目標環境仍需重跑。工作副本實作，不修改桌面原始專案；未提交或上傳。
