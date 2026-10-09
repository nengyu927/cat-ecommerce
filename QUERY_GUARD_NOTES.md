# M01 第四批：重複查詢參數、末頁校正與會員歷程分頁

日期：2026-10-09。延續庫存列表與 Admin 參數批次，M01 尚未全部完成。原始 index.html 規格書未取得，不宣稱已完整符合；本批未修改 Excel 計畫表。

## 後端完成

- 新增 ListQueryConfig 的 MVC interceptor，在 Security filter chain 權限檢查之後、Controller 綁定參數之前，拒絕指定列表 GET 請求中同名參數有多個值。
- 回傳 HTTP 400、ApiResponse error.code = DUPLICATE_QUERY_PARAMETER，訊息「列表查詢參數不可重複」。不回顯參數值。
- 即使同名參數值相同也拒絕；Servlet 解碼後的同名參數也拒絕，例如 page 與 %70age。
- 覆蓋 /api/products、/api/orders、/api/wishlist，以及 /api/admin/products、orders、categories、coupons、members、members/*/history、inventory/low-stock、inventory/movements。
- 在這些端點所有 query key（含未支援的 key）均為單值；不是只檢查 page／size。端點沒有陣列查詢契約，未來若新增多選查詢須明確調整此規則。
- 其他 API、POST／PUT／PATCH 等寫入及登入請求未套用此 interceptor。未新增身份／角色繞過或更動 JWT。
- 未登入存取受保護 Admin 列表仍為 401；一般會員仍為 403，不因重複參數變為 400。已有權限或公開商品列表才進入參數拒絕處理。

## Admin 完成

- 新增 emptyPageReplacement 共用末頁判定：查詢成功、內容為空、page > 0 且 page 超過有效總頁數，才 replace 到 totalPages - 1；沒有資料時 replace 到 0。
- 七種主要列表接上此邏輯：商品、訂單、分類、會員、優惠券、低庫存、庫存異動。保留原篩選、size、sort、來源 returnTo 與 hash（存在時），不逐頁重試或建立一串新歷史。
- 查詢失敗不校正網址；有資料、仍在有效範圍的空頁或缺少有效 totalPages 不擅自改頁碼。舊回應在讀取版本檢查後才能校正，避免離開模組後被舊頁碼導回。
- 不修改後端 page 契約；直接 API 查合法但遠端頁碼仍可回空列表，網址修正是前端導覽行為。
- 會員歷程使用共用 queryInteger：historyPage 預設 0、historySize 預設 10／範圍 1–100，獨立於原會員列表 page／size。
- 非法／重複 historyPage 或 historySize 不發會員或歷程 API；新增「清除紀錄查詢」入口，保留原會員列表條件，恢復歷程預設分頁。
- 歷程換頁保留 historySize，末頁超界只替換 historyPage；返回會員列表不混入歷程欄位。切換會員詳情／歷程的原有頁籤仍沿用列表 query，不宣稱跨頁籤保存完整歷程狀態。
- 將會員錯誤文字放在獨立 span，不與新增操作按鈕文字連在一起，保留既有可讀提示與精準測試定位。

## 主要檔案

| 檔案 | 依據 |
|---|---|
| backend/src/main/java/com/catshop/config/ListQueryConfig.java | interceptor、GET 範圍、parameterMap 多值拒絕 |
| backend/src/test/java/com/catshop/CheckoutIntegrationTest.java | duplicateListParametersAreRejectedAfterAuthorization |
| backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java | duplicateListQueryParametersThroughSqlServerHttp、編碼同名參數及權限順序 |
| frontend/src/utils/listQuery.js | emptyPageReplacement、既有 queryInteger |
| frontend/src/views/AdminView.vue | 商品／訂單／分類成功後校正 |
| frontend/src/views/AdminMemberView.vue | 會員列表校正 |
| frontend/src/views/AdminCouponView.vue | 優惠券列表校正 |
| frontend/src/views/LowStockView.vue | 包裝 items 分頁結果的校正 |
| frontend/src/views/StockMovementView.vue | 異動列表校正 |
| frontend/src/views/AdminMemberDetailView.vue | historyPage／historySize、獨立末頁及清除入口 |
| frontend/scripts/page-bounds.cjs | 新增 12 組瀏覽器案例 |
| frontend/scripts/admin-query.mjs | 共 91 項純參數斷言，含 6 項末頁判斷 |

## 驗證

- 前台與 Admin 建置通過。
- 後端 140 個測試、真實 SQL Server 20 個測試，失敗／錯誤為 0；Edge 真實購物流程也通過。
- SQL Server 使用隔離測試庫與唯一測試登入，結束只移除該庫／登入，不重設展示資料。本機為 Express 17.x，以 AllowOtherVersion 執行，不宣稱已在 SQL Server 2022 驗證。
- 新增瀏覽器測試 12 組：七種列表超界／重整／390px 版面、歷程獨立 page／size、非法歷程復原、零頁回到 0、失敗不假校正、舊庫存回應不能改新模組網址。
- 純參數 91 項斷言通過；既有 admin-members、admin-list-state、low-stock、stock-movements、coupons、admin-filters、list-state 回歸通過。
- 初次新測試按鈕名稱與實際 UI 不一致，已修正選取；新增錯誤復原按鈕影響既有精準文字選取，已將錯誤訊息獨立呈現並重跑，未移除頁面錯誤或非法請求斷言。

## 下一批

1. 前後台每頁筆數／頁碼操作介面，以及 Admin 商品／訂單排序選單；目前 size／sort 新能力主要透過網址使用。
2. 前台商品列表超出末頁的行為與前後端搜尋長度規則對照，目前本批只涵蓋 Admin 七種列表及會員歷程。
3. combined 模式的庫存／詳情返回回歸、整理最終 API/query 規則表，取得原始規格並更新計畫表，再判定 M01 是否完整。

## 交付範圍

工作區實作與本機 ZIP；沒有提交 Git、上傳、重啟展示網站或修改 Desktop 原始專案。排除 node_modules、target、dist、.local、uploads、.git 與日誌，不含展示設定、資料庫內容及實際上傳檔。
