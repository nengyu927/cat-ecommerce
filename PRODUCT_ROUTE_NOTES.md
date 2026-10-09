# 計畫表 R03：Admin 商品新增／編輯獨立頁

日期：2026-10-08。本批依前後台規格計畫表接續 R03，僅修改前端、測試與文件，未修改 Java／API 契約／Flyway／展示資料庫。原始 index.html 規格書仍未取得，不宣稱原規格全部完成。

## 完成範圍

| 計畫項目 | 本批結果 |
|---|---|
| R03 商品新增／編輯 | 完成 /products/new、/products/:id/edit，直接網址／重整、列表入口、返回列表與 Admin 頁面標題 |
| M01 查詢返回 | Admin 商品 keyword/status/categoryId/brand/isActive/page 同步網址，返回與瀏覽器歷史保留已套用條件；false 啟用狀態不遺失 |
| M02 表單保護 | 商品主檔、圖片、既有 SKU 與新增 SKU 草稿納入未儲存提醒；儲存中阻擋重複操作／頁面離開；重載清除草稿須確認 |
| M03 手機 | 390px 商品編輯無整頁橫向溢出，SKU 表格在區域內捲動 |
| R04 SKU／圖片拆頁 | 尚未完成。既有圖片與 SKU 操作先完整搬到商品頁，下一批才拆成自己的 Router 頁面 |

Excel 計畫表本批未改寫。分類編輯保留原列表內操作，未提前完成 R05。

## 使用方式

- 前台網址維持 http://127.0.0.1:5173。
- Admin 商品列表 http://127.0.0.1:5174/products。
- Admin 新增商品 http://127.0.0.1:5174/products/new。
- Admin 編輯商品 http://127.0.0.1:5174/products/:id/edit。
- 正式 Admin 舊 /admin/products/new、/admin/products/:id/edit 網址相容轉址，保留 query/hash；測試 combined 模式仍使用 /admin 前綴。
- 新增至少填第一個 SKU；成功後改到新商品編輯網址，避免重新整理重送新增。
- 主檔與圖片按主表單「儲存」；SKU 逐筆按各自「儲存」。兩者不互相清除未儲存的草稿，新增 SKU 也不清掉主資料草稿。
- 圖片上傳只先取得網址，主表單儲存後才關聯商品。離開放棄草稿不刪除伺服器已上傳檔案；未使用檔案清理並非本批範圍。
- 錯誤 ID 不送商品 API；404、slug 重複、SKU 版本衝突顯示訊息並保留可修正的草稿。重新載入可取得最新資料，但會先確認放棄未儲存內容。
- 手機、直接網址與讀取重試不代表已完成全站手機版／正式部署驗收。非 Vite 部署仍須設各站 history fallback。

## 保留的後端契約與限制

- 沿用 GET /api/admin/products/:id、POST /api/admin/products、PUT /api/admin/products/:id；SKU 仍使用既有專用新增／修改 API。
- 主資料提交只傳 DTO 欄位，不提交 response 的 id、categoryName、skus、時間欄位或暫存 imageUrl；更新商品不會偷偷儲存未送出的 SKU 草稿。
- SKU 修改保留原 version，成功後採用回應的新 version；409 不冒充儲存成功。
- 商品主資料 UpdateProductRequest 目前沒有版本欄位，尚無多人同時修改的版本衝突防護；不能把 slug 409 當作商品主檔已有樂觀鎖。此項需另行規劃後端契約與測試。
- 同時開啟多頁時的登入撤銷仍由現有登入同步與 API 權限控制處理，本批沒有放寬 Admin 權限。

## 主要程式依據

| 檔案 | 內容 |
|---|---|
| frontend/src/router/admin.js | 新增商品／編輯路由、舊址相容、Admin meta |
| frontend/src/views/AdminProductView.vue | 獨立表單、請求序號保護、草稿比較、路由離開／beforeunload、DTO 欄位白名單、SKU 局部回應套用 |
| frontend/src/views/AdminView.vue | 移除內嵌商品編輯，商品入口導向新頁；列表查詢網址同步與驗證；分類表單保留 |
| frontend/src/AdminApp.vue | 商品頁標題與主選單選取狀態 |
| frontend/scripts/product-routes.cjs | 13 項商品路由測試，以攔截 API 執行，不寫展示資料 |
| frontend/scripts/smoke.cjs | 調整測試 API 支援新增後的編輯頁載入 |
| frontend/package.json | 新增 test:product-routes |

## 本批已執行驗證

- npm run test:product-routes：13 項通過。直接網址／重整、分類跨頁、手機、新增必填 SKU、成功轉編輯、主資料白名單與 slug 衝突、取消／確認離開、SKU 版本與失敗草稿、主資料／SKU 草稿互不覆蓋、圖片加入與儲存、取消重試、新增 SKU、非法上傳、篩選往返與歷史、慢回應換商品、非法 ID／404 重試、舊址權限轉址、非法列表查詢。
- npm run test:admin-filters：通過。
- npm run test:category-hierarchy：通過。
- npm run test:shipments：12 項通過。
- npm run test:smoke：通過。
- npm run build：前台與 Admin 通過。

以上為本批前端與攔截 API 的瀏覽器回歸；本批沒有重新跑真實 SQL Server／後端整合測試，也未重啟／清除展示資料。ZIP 不包含 .local、資料庫設定機密、uploads、node_modules、target、dist 或日誌。未 Git 提交或上傳。

## 下一步

依計畫 R04 將 SKU 與圖片分出獨立操作頁，沿用已有 API，保留獨立草稿／版本與返回商品編輯條件；再處理 R05 分類拆頁。商品主檔併發防護另列後端待辦，不混淆為本次已完成項目。
