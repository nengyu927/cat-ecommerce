# M01 第八批：商品錯誤復原與下架返回

日期：2026-10-09，延續 CATALOG_ROUTE_STATE_NOTES.md。原始 index.html 未取得，未更新 Excel 計畫表；本批不宣稱 M01 或原始規格全部完成。

## 實作

- ProductDetailView 將商品主體與相關商品錯誤拆開。相關商品 API 失敗不再讓已成功取得的商品詳情消失，顯示專用錯誤與「重試相關商品」按鈕。
- 「重試相關商品」只讀取 related API，不重新讀商品或改網址；讀取中停用重試，仍有產品切換與離頁的 loadSequence 防護。
- 商品詳情失敗新增「重試載入商品」，保留 returnTo；既有安全返回連結持續存在，因此商品已下架的 404 畫面仍可回原分類／搜尋列表。
- 沿用既有列表 emptyPageReplacement：返回來源若商品數縮減，原末頁成功收到空頁後校正至新末頁；全部商品消失時回 page=0 並顯示空列表，保留分類 slug／搜尋、價格、排序、筆數與 hash。
- 未變更 API、Entity、資料庫結構、Security 或下架業務規則；後台 DELETE 仍是軟下架，並非實體刪除。

## 新增驗證

- catalog-routes 在 storefront 與 combined 模式各補 3 組：下架詳情返回縮減分類與空搜尋頁、相關商品失敗只重試相關 API、商品詳情重試保留來源。合計 16 組（含上一批既有 10 組）。
- SQL-backed e2e 在專用測試分類內建立每種來源各 3 筆測試商品，不改任何種子商品；分類與搜尋各驗證詳情／相關商品返回、重新整理、下架後 404、末頁 2 → 1、全下架後 2 → 0，以及實際公開 API totalElements=0。
- 每次下架只使用本次 create 回應的商品 ID。測試紀錄與軟下架商品留在隔離資料庫，最後由驗證腳本清除整個本次測試資料庫及專用登入。
- e2e 新增啟動防護：必須由隔離 runner 提供 CATSHOP_IT_BROWSER=true、隨機 catshop_it_<guid> JDBC 資料庫名稱與後端 URL；缺少資訊即在啟動瀏覽器／送請求前中止，不再預設使用 8080 進行測試修改。
- 來源返回比對完整解析後的 query 與 hash，避免 URLSearchParams 和 Vue Router 對逗號的編碼差異造成假失敗；不放寬任何搜尋條件的保留斷言。

## 程式碼依據

| 檔案 | 關鍵依據 |
|---|---|
| frontend/src/views/ProductDetailView.vue | loadProduct、loadRelated、relatedError／relatedLoading、loadSequence、兩種錯誤的重試按鈕 |
| frontend/scripts/catalog-routes.cjs | state.items／detailFailure／relatedFailure 控制、16 組跨模式回歸 |
| frontend/scripts/e2e.cjs | runner guard、fixturePrefix、create 回應 ID 下架、分類／搜尋 SQL 返回驗證 |
| frontend/src/views/ProductListView.vue | 原有 emptyPageReplacement 與具名路由條件保留，本批未修改 |
| backend/scripts/verify-app-mssql.ps1 | 隔離資料庫建立與精確名稱清除，本批未修改 |

## 本次結果

- 前台與 Admin 正式建置通過。
- 本次後端 Surefire 報告合計 142 項，0 失敗／錯誤；後端原始碼未更改，但已重新完整執行。
- test:catalog-routes 16 組、test:list-state 8 組、test:product-filters 7 組，共 31 組模擬 API 瀏覽器案例通過。
- 本次完整隔離 SQL 驗證 BUILD SUCCESS：SQL Server 21 項通過，0 失敗／錯誤／跳過；真實瀏覽器輸出分類與搜尋各一筆 PASS REAL CATALOG RETURN，並通過原購物、登入、分頁、排序等回歸。
- 本機 SQL Server 為 17.x，使用 AllowOtherVersion；不能當成 SQL Server 2022 專用驗收。
- 本次隨機測試資料庫與專用登入已清除；測試商品隨隔離資料庫清除，不改任何展示資料。
- 啟動防護負向測試通過：移除隔離 runner 環境後，e2e 在瀏覽器或網路請求啟動前以退出碼 1 拒絕執行，不能誤用預設展示後端。

## 剩餘工作

1. 補延遲相關商品回應與快速切換商品的競態案例，以及相關商品載入中的顯示體驗。
2. 依 SEARCH_RULE_NOTES.md 確認數字格式、大小寫與未知 query 的 API 相容策略，不直接破壞既有使用者契約。
3. 取得原始規格書、更新計畫表，再完成 M01 整體驗收。

## 交付界線

只更新工作區與建立本機 ZIP；不提交、不上傳、不重啟展示服務、不修改 Desktop 原始專案或展示持久資料。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。
