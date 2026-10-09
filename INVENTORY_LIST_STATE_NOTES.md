# M01 第三批：庫存列表返回與後端列表防護

日期：2026-10-09。延續 ADMIN_LIST_STATE_NOTES.md。M01 仍部分完成；未取得原始 index.html 規格書，不能宣稱完整符合原始規格，本批未修改 Excel 計畫表。

## 前台程式（Admin 網站）

- LowStockView 與 StockMovementView 接上 parseAdminListQuery。頁碼預設 0、size 預設 20／範圍 1–100；拒絕負數、指數格式、前導零、超 Java int 頁碼、重複 page／size 等已知參數。
- 低庫存驗證 keyword 上限 200、outOfStockOnly 布林值；庫存異動驗證正整數 Java Long 範圍 SKU ID、既有四種 reason 白名單。這兩種列表仍採固定排序，不支援 sort query。
- 搜尋保留 size 並重置 page；換頁與重整保留已套用的網址條件。清除移除查詢條件及 size，恢復預設；庫存異動保留 returnTo 來源以供返回。
- 載入先將 result 清空，失敗不保留舊列／總數，也不顯示假空清單；保留重試及讀取 version 防護。
- 低庫存列的「前往商品管理」與「異動紀錄」帶入來源 returnTo。商品管理列表及異動頁新增「返回低庫存清單」，保留原 keyword、缺貨條件、page、size。
- 商品管理搜尋／清除、編輯、SKU／圖片操作的既有返回白名單可沿用 returnTo，最後回到商品列表仍可返回原低庫存來源。
- 返回只允許 admin-inventory 路由；外部網址、其他模組或非法格式使用預設低庫存入口，沒有任意 URL 跳轉。
- 使用路由名稱，沿用獨立 Admin 與 combined 模式的路由設定。新增瀏覽器案例主要驗證獨立 Admin 模式。

## 後端

- 新增 common/AdminListParameters.java，提供 paging、text、sort 驗證，不依賴前端檢查。
- AdminProductService、AdminOrderService、AdminCategoryService 新增 size 最大 100；page 仍不可為負數。既有預設大小與 API 路徑不變。
- 商品／分類 keyword 與訂單 orderNo 上限 200；商品品牌原本 100 上限保留並檢查原始長度；非法分類 ID、商品／訂單狀態回傳 BAD_REQUEST。
- 訂單 from／to 限制年份 1–9998，避免 SQL datetime2 範圍及結束日期加一天溢位；開始不可晚於結束的原規則保留。
- 商品／訂單 sort 必須為兩段「允許欄位,asc／desc」，拒絕只有欄位、空方向、非法方向、第三段與非法欄位。後端接受欄位／方向周邊空白、大小寫方向；null 採 createdAt,desc，明確空字串不再作預設值。
- 排序主欄位不是 id 時加入 id DESC 作唯一第二排序，避免相同主欄位值跨頁漂移；id 本身為主排序則不重複加欄位。
- 未修改 Entity、DTO、Repository 查詢、Flyway、Security／JWT、會員資料或庫存寫入流程。

## 檔案與依據

| 檔案 | 依據 |
|---|---|
| frontend/src/utils/adminListQuery.js | inventory／movements 參數與 products returnTo 白名單 |
| frontend/src/views/LowStockView.vue | query 驗證、size、清除與兩個來源連結 |
| frontend/src/views/StockMovementView.vue | query 驗證、safeListReturn、重試與返回 |
| frontend/src/views/AdminView.vue | inventoryReturn、搜尋／清除保留來源 |
| backend/src/main/java/com/catshop/common/AdminListParameters.java | paging／text／sort、id 唯一排序 |
| backend/src/main/java/com/catshop/service/admin/AdminProductService.java | 商品列表驗證與共用排序 |
| backend/src/main/java/com/catshop/service/admin/AdminOrderService.java | 訂單日期／狀態／長度與共用排序 |
| backend/src/main/java/com/catshop/service/admin/AdminCategoryService.java | 分類大小／長度驗證 |
| backend/src/test/java/com/catshop/common/AdminListParametersTest.java | 3 個純參數測試 |
| backend/src/test/java/com/catshop/ListQueryIntegrationTest.java | 新增 2 個邊界／相同排序值分頁測試 |
| backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java | 新增 HTTP 分頁／排序／日期／狀態拒絕與合法查詢 |
| frontend/scripts/admin-query.mjs | 共 85 項參數斷言 |
| frontend/scripts/low-stock.cjs | 7 組，含兩條返回來源動線及非法 query |
| frontend/scripts/stock-movements.cjs | 6 組，含 size／重複參數／非法返回目標 |

## 驗證結果

- 前台與 Admin 正式建置通過。
- 85 項參數斷言、低庫存 7 組、庫存異動 6 組皆通過。
- test:admin-list-state、test:admin-filters、test:product-routes、test:product-assets、test:list-state 回歸通過。
- 後端 Surefire 報告 139 個測試，失敗／錯誤為 0；真實 SQL Server Failsafe 19 個，失敗／錯誤為 0。
- 真實 Edge 購物流程亦通過，涵蓋登入、收藏、商品、購物車、下單、庫存、取消、刷新及跨分頁登出；使用隔離資料庫與唯一測試登入，驗證後只刪除該測試庫／登入，展示資料保留。
- 本機 SQL Server 為 Express 17.x，使用既有 AllowOtherVersion 入口；不宣稱已在 SQL Server 2022 實測。
- 初次新增返回測試因低庫存測試的異動 API mock 返回陣列而非 PageResult，產生 render 錯誤；已修正 mock 契約並重跑，未隱藏頁面錯誤斷言。

## 下一批／剩餘工作

1. 後端重複 query 參數的統一拒絕策略與 API HTTP 測試；目前前端拒絕陣列，不代表直接後端請求已有同等防護。
2. 會員歷程 historyPage 共用解析、Admin 超出末頁的校正與一致錯誤復原；目前後端仍允許合法但沒有內容的遠端頁碼。
3. 前後台 page／size／sort 操作介面及規則表整理，目前 size／sort 的新能力主要透過 URL，未新增選單。
4. 補 combined 路由模式的庫存返回測試、取得原始規格書並更新規格計畫表；完成後再判定 M01 狀態。

## 交付

只修改工作區並建立本機 ZIP；沒有提交 Git、上傳、修改 Desktop 原始專案或重啟展示網站。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌，不含私有資料庫設定／展示資料／實際上傳內容。
