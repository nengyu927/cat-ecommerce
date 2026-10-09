# M01 第六批：搜尋限制對齊與真實分頁整合

日期：2026-10-09。延續 PAGINATION_UI_NOTES.md。原始 index.html 規格書尚未取得，未修改 Excel 計畫表；M01 不在本批直接標記全部完成。

## 修改範圍

- 公開商品 API 補 keyword 最多 200 字元；brand 最多 100 字元改為檢查原始輸入。Admin 會員與低庫存 keyword 亦先檢查原始 200 字元再 trim，與前端 queryText 的長度規則一致。
- 空白也計入上限，201 個空白不再被當成合法空搜尋；200 個空白仍合法，查詢時視為未指定搜尋。合法字詞與品牌仍去除頭尾空白，不改既有模糊／精確比對語意。
- Admin 商品狀態查詢統一 trim + Locale.ROOT 大寫，避免驗證接受小寫、查詢卻傳入小寫的落差；訂單列表改用相同固定語系正規化。不改寫入流程、JWT、Entity、DTO、API 路徑或資料庫結構。
- ProductFilterIntegrationTest 補原始長度邊界、超長空白與合法空白查詢；CheckoutIntegrationTest 在區分大小寫的 H2 查詢中驗證商品狀態正規化。
- SqlServerCheckoutIT 補五種商品／分類／會員／低庫存列表 keyword、前後台品牌長度邊界，以及商品／訂單狀態大小寫與空白。
- 真實瀏覽器 e2e 增加前台每頁筆數、下一頁、跳頁、瀏覽器返回、超界末頁校正；Admin 排序、篩選保留、下一頁和重新整理。此 e2e 使用 combined 路由模式與真正 SQL Server API，不是模擬資料。

## 現有查詢規則對照

| 條件 | 前端網址 | 後端 API | 備註 |
|---|---|---|---|
| keyword／orderNo | 原始字串最多 200 字元 | 同上，keyword 各服務再 trim | Java／JS 字串長度均為 UTF-16 單位，並非視覺字數 |
| brand | 原始字串最多 100 字元 | 同上，查詢再 trim | 本批修正公開商品的空白邊界 |
| page／size | page >= 0；size 1–100；禁止前導零／指數／加號 | 綁定 int 後驗證相同數值範圍 | API 仍可能接受 01、+1 等可轉型數字；未宣稱格式完全一致 |
| ID | 正整數字串，保留 Java Long 最大值精度 | 正 Long，拒絕 <= 0 | URL 使用字串，不先轉 JS Number |
| Admin 狀態／角色 | 嚴格選單白名單 | 支援 trim 與大小寫正規化 | API 較寬鬆為相容行為，前端不接受小寫 URL |
| 排序 | 完整白名單，欄位與 asc／desc | 公開商品嚴格；Admin 商品／訂單容許頭尾空白與方向大小寫 | 固定排序列表沒有新增排序功能 |
| 價格 | 非負一般十進位格式，最多兩位小數 | BigDecimal 數值 0–99999999.99，有效小數最多兩位 | API 尾端零／指數格式較寬鬆；本批未改格式契約 |
| 日期 | 真實 ISO 日期、0001–9998、起 <= 迄 | 同範圍與順序 | 訂單篩選 |
| 重複參數 | 支援的欄位遇陣列拒絕 | 指定列表 GET 任意重複 key 拒絕 | 保護順序仍先登入與角色驗證 |
| 超界頁 | 成功收到空頁後 replace 至末頁／0 | 回傳空 PageResult，不強制改頁 | 失敗不校正，保留查詢條件 |

上表是目前程式契約盤點，不是原始規格書逐項驗收。未知 query、數值字面格式及大小寫相容策略仍需確認；勿以本批長度對齊推論所有規則都一致。

## 主要依據

- backend/src/main/java/com/catshop/service/ProductService.java：search 原始長度限制。
- backend/src/main/java/com/catshop/service/admin/AdminInventoryService.java、AdminMemberService.java：keyword 檢查位置。
- backend/src/main/java/com/catshop/service/admin/AdminProductService.java、AdminOrderService.java：查詢狀態 Locale.ROOT。
- backend/src/test/java/com/catshop/ProductFilterIntegrationTest.java、CheckoutIntegrationTest.java：H2／HTTP 回歸。
- backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java：實際 SQL Server HTTP 邊界。
- frontend/scripts/e2e.cjs：expectListResponse 與 SQL-backed 分頁／排序／路由回歸。
- frontend/scripts/page-bounds.cjs：舊庫存回應改為明確閘門，先完成跨模組導航再釋放，不依賴 500ms 延遲與固定睡眠。
- SQL 瀏覽器子程序總時限從 60 秒改為 180 秒，因原流程已含多項拆頁操作，本批再加入分頁與排序；保留每個 Playwright 操作既有逾時及全部狀態／API 錯誤斷言。逾時亦先輸出階段日誌再回報失敗，不能將逾時當通過。
- frontend/src/utils/listQuery.js、adminListQuery.js、backend/src/main/java/com/catshop/common/AdminListParameters.java：規則表來源。

## 驗證

- 前台與 Admin 正式建置通過。
- test:admin-query：91 項斷言通過。
- test:pagination-ui：12 組介面測試通過。
- test:page-bounds：12 組通過。首次在固定 500ms 舊回應案例失敗，改為受控回應閘門後重跑全部通過；未移除 URL 或錯誤斷言。
- test:product-filters：7 組通過。
- SQL Server 完整驗證第一次：後端 142 項通過；SQL 21 項中只有瀏覽器案例因 60 秒總時限失敗。當次子程序日誌未完成輸出，另有客戶端中斷連線的伺服器日誌，不把此輪宣稱全通過。
- 完整重跑通過：後端 142 項，SQL Server 21 項，皆 0 失敗／錯誤／跳過；BUILD SUCCESS。真實瀏覽器輸出 PASS REAL PAGINATION 與 PASS REAL E2E，涵蓋新增分頁／排序以及既有購物、驗證與拆頁流程。
- 兩輪隔離測試資料庫及專用登入都已清除。只刪除測試腳本本次產生的隨機名稱，不刪展示資料或任何使用者資料庫。
- 本機 SQL Server 為 17.x，使用 AllowOtherVersion；不是 SQL Server 2022 專用驗收。

## 後續順序

1. 補前台分類／搜尋具名路由與詳情返回來源的完整分頁回歸。
2. 決定是否要求 API 與前端的數字字面格式、空白／大小寫和未知 query 規則完全相同，再依明確契約修改，避免破壞既有 API 使用者。
3. 取得原始規格書、更新計畫表，再完成 M01 驗收與下一項功能排序。

## 交付界線

只修改工作區並建立本機 ZIP；不提交、不上傳、不重啟展示網站、不修改 Desktop 原始專案。SQL 驗證使用隨機命名隔離測試資料庫，結束只清除本次測試資料庫及登入，不碰展示持久資料。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。
