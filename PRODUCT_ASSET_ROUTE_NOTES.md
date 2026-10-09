# 計畫表 R04：SKU／商品圖片獨立操作頁

日期：2026-10-08。對照 work/plan-table/build-plan.mjs 的 R04：/products/:id/skus、/products/:id/images。本批完成拆頁與操作防護，未改 Excel 計畫表；不宣稱已完成原始 index.html 規格全部內容。

## 完成範圍

- Admin 商品編輯頁新增「管理 SKU」「管理圖片」入口，兩者有自己的網址、可直接開啟／重整。
- SKU 頁提供規格、價格、庫存、安全庫存、啟用與新增規格。沿用 version，成功後採用新版本；409 保留草稿、不覆蓋他人最新庫存。
- 圖片頁提供網址加入、PNG／JPEG 上傳、排序、替代文字、主圖與移除；上傳只取得網址，必須另按「儲存圖片」才關聯商品。
- 圖片儲存改用專用 API，商品主資料編輯不再提交舊圖片清單，因此不會因更新名稱／價格而把圖片改回舊狀態。
- 未儲存離開／瀏覽器離開提醒、重載清除草稿確認、儲存中防重送／阻擋導覽、錯誤重試、404／非法 ID、慢回應換商品防護。
- 返回商品編輯／商品列表保留 keyword/status/categoryId/brand/isActive/page；正式 Admin 舊 /admin/products/:id/skus 或 images 路徑轉址並保留 query/hash。
- 390px 手機版 SKU／圖片頁通過整頁寬度檢查，SKU 表格可在區域內捲動。

## 網址

| 用途 | Admin 路由 |
|---|---|
| 商品主資料 | http://127.0.0.1:5174/products/:id/edit |
| SKU 管理 | http://127.0.0.1:5174/products/:id/skus |
| 圖片管理 | http://127.0.0.1:5174/products/:id/images |

前台維持 5173，Admin 維持 5174。新增商品仍先在 /products/new 建立商品與第一個 SKU，建立成功後再進入獨立操作頁。切換頁面不保存未提交草稿；同意離開代表放棄該頁草稿，不是自動儲存。

## 後端安全儲存

新增 PUT /api/admin/products/:id/images，僅 ADMIN 可操作：

- DTO：expectedImageIds 為載入時依排序取得的圖片 ID 清單；images 為新圖片資料，不包含商品名稱、價格、分類或 SKU。
- 交易內鎖定商品，確認目前圖片 ID 與預期快照完全一致，才取代圖片關聯；不一致回 409，草稿保留。
- 非空清單須恰有一張主圖，最多 100 張；排序依提交陣列重編為 0 起算，回應帶新 ID 供下一次儲存使用。
- 空清單可清除商品圖片關聯，不刪除已上傳檔案。未使用上傳檔案清理仍是後續工作。
- 舊商品主檔 PUT 的更新與圖片操作採相同商品交易鎖，避免交錯刪除／建立；新版主檔頁刻意不送 images。
- 為維持契約，舊主檔 PUT 仍接受 images，但舊客戶端沒有 expectedImageIds，不能聲稱該舊寫法已具快照衝突檢查；應使用新圖片 API。
- 商品主資料仍沒有版本欄位，多人更新主檔的最後寫入覆蓋問題並未因此解決。
- 不新增 Entity 欄位或 Flyway 遷移，SQL Server 設定與展示資料庫不變。

## 主要檔案依據

| 檔案 | 重點 |
|---|---|
| frontend/src/views/AdminProductAssetView.vue | SKU／圖片頁、獨立草稿、version／expectedImageIds、排序主圖、上傳與離開防護 |
| frontend/src/views/AdminProductView.vue | 改為操作入口；編輯商品 payload 不帶 images；新增商品仍允許初始 SKU／圖片 |
| frontend/src/router/admin.js | 新路由、Admin meta、舊址相容 |
| frontend/src/AdminApp.vue | 新頁面標題與商品主選單狀態 |
| frontend/src/api/admin.js | updateProductImages |
| backend/src/main/java/com/catshop/dto/admin/product/UpdateProductImagesRequest.java | 圖片 DTO／欄位、數量與元素驗證 |
| backend/src/main/java/com/catshop/controller/admin/AdminProductController.java | ADMIN 專用圖片更新端點 |
| backend/src/main/java/com/catshop/service/admin/AdminProductService.java | 交易鎖、圖片 ID 快照比對、主圖檢查、只改圖片 |
| backend/src/main/java/com/catshop/repository/ProductRepository.java | findForUpdate，PESSIMISTIC_WRITE |
| backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java | 真實 SQL／HTTP 圖片並行更新、權限、驗證與主欄位保留 |
| frontend/scripts/product-assets.cjs | 11 項 SKU／圖片瀏覽器測試 |
| frontend/scripts/product-routes.cjs | 調整為 10 項主資料頁回歸，確保不送舊圖片快照 |
| frontend/scripts/e2e.cjs | 隔離資料庫上直接開啟 SKU／圖片頁 |

## 本批已實測

- npm run test:product-assets：11 項通過；直接網址、重整、手機、SKU 新增／版本／衝突、數量檢查、未儲存、圖片排序／主圖／移除、專用快照提交、圖片衝突／重試、上傳／檔案格式、返回條件、慢回應、404／非法 ID、舊址與會員拒絕。
- npm run test:product-routes：10 項通過；商品新增／編輯／列表返回，主資料更新不帶圖片。
- npm run test:admin-filters：通過。
- npm run build：前台與 Admin 通過。
- backend/scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser：BUILD SUCCESS；133 項後端測試、17 項真實 SQL 整合測試與真實瀏覽器購物／登入／Admin 頁面流程通過。
- 真實 SQL 圖片競態：同快照兩筆並行 PUT 結果為 200 與 409；主資料不變、非法主圖／缺快照被拒絕、未登入 401、會員 403，清空清單成功。

SQL 實測環境為本機 SQL Server Express 17.x，不是規格指定的 SQL Server 2022；2022 仍需在目標環境重跑。驗證使用唯一命名的 catshop_it_* 測試資料庫／登入，成功及首次編譯失敗的暫存環境都已清理，不清除展示資料。

交付 ZIP 排除 .local、uploads、node_modules、target、dist、日誌與資料庫機密。未 Git 提交／上傳。本批未啟動展示服務；檢查時 5173／5174／8080 未運行，需啟動新版前後端後使用新圖片 API。持久化啟動方式見 PERSISTENT_PREVIEW_NOTES.md。

## 下一批

依計畫 R05：分類列表、新增／編輯獨立頁。先保留分類階層／循環防護，再補直接網址、未儲存提醒、列表條件返回，不擴展到點數、金流或物流。
