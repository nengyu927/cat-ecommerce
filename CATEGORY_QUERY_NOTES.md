# 後台分類列表：資料庫分頁與批次計數

## 本批完成

- 不再讀取全部分類後才在 Java 篩選／分頁，改由資料庫套用 keyword、isActive、sortOrder／ID 排序與分頁。
- 只對當頁分類 ID 批次計算啟用商品數，每批 500 個 ID；空列表不發出計數查詢。
- 分頁查詢同時取得單值 parent 關聯，上層分類不在當頁仍能顯示名稱；不 fetch children 集合，不使用記憶體集合分頁。
- 搜尋 name 或 slug，保留不分大小寫、去除輸入前後空白的字面 contains 用法；`%`、`_`、`!`、`[` 已跳脫，不當成 SQL Server LIKE 模式。
- page < 0 或 size < 1 明確拒絕為 bad request。保留原有正常分頁大小範圍，未新增每頁 100 筆限制；大頁面計數依既有 IdBatches 分批。

保留 API `/api/admin/categories` 及 AdminCategoryResponse。productCount 是 `isActive=true` 商品數，包含狀態 OFF_SHELF 但仍啟用的商品，不改成只計 ON_SALE；沒有商品時回傳 0。分類新增／修改／停用規則未改，Entity、DTO、Flyway V1～V5 也未修改。

## 程式依據

- `backend/src/main/java/com/catshop/repository/CategoryRepository.java`：adminSearch 分頁、name／slug LIKE escape 與 parent EntityGraph。
- `backend/src/main/java/com/catshop/repository/ProductRepository.java`：countActiveProductsByCategory 的 GROUP BY projection。
- `backend/src/main/java/com/catshop/service/admin/AdminCategoryService.java`：輸入檢查、字元跳脫、資料庫分頁及只對當頁批次組 DTO。
- `backend/src/test/java/com/catshop/ListQueryIntegrationTest.java`：4 個新增分類情境，驗證冷 persistence context 下的固定查詢數、頁外 parent、尾頁、啟用商品計數、字面搜尋、停用分類、空頁與非法參數。
- `backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java`：新增真實 SQL 的分類分頁、不同頁外 parent、商品計數與特殊字元搜尋測試。

## 驗證

57 個後端單元／H2 測試、5 個真實 SQL Server 整合測試通過。分類每頁 1 筆與 6 筆均為 3 次服務層 SQL：分類頁面、總筆數、當頁商品數。這不含 HTTP JWT／session 驗證或其他 API，不等同已驗證毫秒加速比例。未滿頁 count 可能省略；大於 500 個 ID 的頁面會增加批次計數查詢。

完整真實 Edge 購物流程與前後台一鍵登入已回歸通過。獨立站台測試與 storefront／admin 正式建置也通過；兩個網站名稱保留為「喵喵商城」及「喵喵商城 Admin」。後端 SQL 實測環境是 Express 17.x，2022 仍待驗收。

驗證使用獨立 catshop_it 暫存資料庫，不與使用者當時的 catshop_demo 展示資料共用。測試結束已清除測試資料庫及專用帳號。

使用者同意後，另行重啟展示後端並重建暫存展示資料以套用最新版；8080 API、5173 前台與 5174 後台網址不變。這不是正式資料遷移或持久儲存設定，重啟後需重新登入。

## 尚未完成

- 購物車多商品查詢仍可進一步批次化。
- 分類階層的完整循環關係驗證、功能模組整理及孤兒圖片清理仍待後續處理。
- 尚無大量資料／多節點壓力測試。LIKE 搜尋結果仍受實際 SQL Server collation 影響，尚未驗證所有語言與重音字元和舊版 Java contains 完全等價。
- 訪客結帳、正式部署反向代理與 SQL Server 2022 驗收尚未完成。

桌面原專案未修改，未提交或上傳；交付專案保留前後台獨立入口、一鍵展示登入與前幾批成果。
