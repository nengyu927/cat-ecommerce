# M01 第九批：商品與推薦非同步顯示

日期：2026-10-09。延續 CATALOG_RECOVERY_NOTES.md。原始 index.html 尚未取得，未修改 Excel 計畫表；不宣稱 M01 或原規格全部完成。

## 完成內容

- 商品主體成功後立即顯示，不再等待相關商品請求結束；先初始化圖片與可用 SKU，再獨立讀取相關商品。
- 相關商品區提供具名區域、aria-busy 與「相關商品載入中…」status；空結果明確顯示「目前沒有相關商品」，與讀取中／失敗區分。
- 沿用 loadSequence，切換商品、重試主體與離頁都使過期主體／推薦回應失效。舊成功資料、錯誤與 finally 讀取狀態均不能覆蓋當前商品。
- 商品主體的 id 在請求開始時取得，相關商品使用同一次主體的 id；不在延遲請求結束後重新讀取已改變的 route.params.id。
- 保留上一批主體／推薦獨立重試與安全返回，不改 API、後端、購物車、JWT 或資料庫。

## 延遲回應測試

catalog-routes 新增 holdResponse／releaseResponse，測試明確控制回應釋放，不用固定睡眠猜測競態。先確認新商品／返回列表已顯示，再釋放舊回應，等待 HTTP 完成與畫面更新後斷言。

storefront 與 combined 各新增 4 組：

1. 延遲推薦成功：商品主體與購物按鈕先出現、推薦顯示讀取中；切換回另一商品後，舊推薦不得覆蓋新資料或 loading。
2. 延遲推薦失敗：切換商品後才收到舊 503，不得將新商品改成錯誤畫面。
3. 延遲主體成功：切換回另一商品後才收到舊詳情，不得覆蓋新標題，也不能觸發舊商品推薦請求。
4. 離頁及空結果：推薦未完成仍可返回搜尋列表，釋放舊回應不顯示推薦區；推薦失敗後重試可恢復明確空結果，保留商品主體。

## 檔案依據

- frontend/src/views/ProductDetailView.vue：loadProduct 的固定 id、void loadRelated，以及相關商品區的載入／空結果狀態。
- frontend/scripts/catalog-routes.cjs：gates、holdResponse、releaseResponse，跨模式舊成功／失敗回應與離頁斷言。
- README.md：本批說明索引。

## 本次驗證

- test:catalog-routes 24 組通過（原有 16 組 + 新增 8 組），保留頁面錯誤檢查。
- test:list-state 8 組回歸通過；總計 32 組模擬 API 瀏覽器案例通過。
- 前台與 Admin 正式建置通過。
- 本批只有前端變動，未重新跑後端／SQL Server；不將上一批 142／21 的結果當成新驗證。

## 後續

1. 商品詳細頁的購物操作錯誤目前仍共用主體 error；可再拆分操作錯誤與讀取錯誤，避免數量／購物車失敗隱藏商品。
2. 按查詢規則表確認 API 數字字面格式、大小寫與未知 query 相容策略。
3. 取得原始規格書、更新計畫表，再作 M01 整體驗收。

## 交付界線

只更新工作區及建立本機 ZIP；不提交、不上傳、不重啟展示網站，不修改 Desktop 原始專案或展示持久資料。本批沒有 SQL 建立、清除或資料變動。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。
