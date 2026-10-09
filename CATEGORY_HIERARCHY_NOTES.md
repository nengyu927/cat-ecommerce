# 分類階層防護（2026-10-08）

## 本批完成

- 更新分類時沿新上層往根節點檢查，禁止將自己、子分類、孫分類或更深的下層分類設為上層。停用分類也納入判斷。
- 新增與修改都檢查上層鏈的重複節點；已有循環的上層回覆 `CATEGORY_HIERARCHY_INVALID`（409），不會無限走訪。
- 保留自行設為上層的既有 `CATEGORY_HAS_CHILDREN`（409）；下層造成的循環也使用此代碼。不存在的上層仍是 `PARENT_NOT_FOUND`（404）。
- 檢查通過前不改動名稱、slug、父分類、排序或啟用狀態；拒絕時交易回滾。
- 合法搬到其他分支或根分類仍可操作；既有錯誤階層可透過把受影響節點移回根分類來修復。不自動批次修正舊資料。
- 分類新增／修改／停用先鎖住最小 ID 的分類列，直到交易結束。現行分類僅軟刪除，因此該列作為穩定的階層寫入鎖；防止兩個交易同時把 A 掛到 B、B 掛到 A。沒有分類時不可能出現既有節點循環。
- 後台上層選單排除正在編輯的分類及全部下層分類，包括停用、較後分頁的分類。遍歷使用已訪問集合，避免錯誤資料造成前端無限迴圈。
- 顯示選擇規則，並補明確的「上層分類」無障礙標籤；409 錯誤仍保留使用者正在編輯的表單。

API、Entity、DTO、Flyway V1～V5 與前台分類樹回傳格式未修改。前後台獨立入口、名稱與展示一鍵登入保留。

## 具體程式依據

- `backend/src/main/java/com/catshop/repository/CategoryRepository.java`：`findFirstByOrderByIdAsc` 使用 `PESSIMISTIC_WRITE`。
- `backend/src/main/java/com/catshop/service/admin/AdminCategoryService.java`：`lockHierarchy` 在分類讀取之前取得鎖；`validateParent` 檢查新上層鏈、目標分類及重複節點。
- `frontend/src/views/AdminView.vue`：`parentCategories` 以 parentId 建立下層索引，迭代排除自身和所有下層；分類編輯選單改用此集合。
- `backend/src/test/java/com/catshop/CategoryHierarchyIntegrationTest.java`：5 個 H2 測試。
- `backend/src/test/java/com/catshop/support/CategoryHierarchyChecks.java`：H2／SQL Server 共用斷言，包含兩個真實交易同時互移、原始 SQL 模擬錯誤階層及回滾／修復確認；僅清理測試自行建立的分類。
- `backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java`：SQL Server 重跑所有分類階層案例。
- `frontend/scripts/category-hierarchy.cjs`：獨立 Admin 5176 瀏覽器測試，所有 API 都被攔截為測試替身，不修改展示資料。

## 實測結果

- 全部 62 個後端單元／H2 測試通過，0 失敗、0 錯誤。
- 全部 6 個 SQL Server 整合測試通過，0 失敗、0 錯誤；含 Flyway、Hibernate validate、分類階層、並行互移、分類查詢、購物車競態及真實 HTTP。
- 真實 Edge 購物流程通過：註冊、HttpOnly Cookie、購物車持久化、結帳扣庫存、取消回庫存、更新登入、改密碼跨分頁登出、一鍵展示登入與會員拒絕進入後台。
- `npm run test:category-hierarchy` 通過：跨頁／停用下層不可選、合法選項保留、衝突保留表單、新增分類保留全部父分類選項。
- `npm run build` 前台與 Admin 後台建置通過；重啟後 `npm run test:split` 通過，8080 健康 API 為 UP。

本機 SQL 是 Express 17.x，不是規格要求的 SQL Server 2022；2022 驗收仍待完成。第一輪瀏覽器驗證因預設 Chromium 未安裝而失敗；改用已安裝 Edge 後完整重跑成功，沒有隱藏第一次失敗。

重跑完整驗證（PowerShell，backend 目錄）：

```powershell
$env:E2E_BROWSER_CHANNEL = 'msedge'
.\scripts\verify-app-mssql.ps1 -AllowOtherVersion -Browser
```

後台畫面測試（frontend 目錄）：

```powershell
npm run test:category-hierarchy
```

## 展示狀態與限制

經使用者明確同意，重啟展示後端並重建暫存展示資料。舊展示資料庫及專用登入已移除，沒有留下備份；展示會員、訂單、修改紀錄不保留，需重新登入。

- 前台「喵喵商城」：`http://127.0.0.1:5173/`
- 後台「喵喵商城 Admin」：`http://127.0.0.1:5174/`

兩輪整合驗證各自建立的隔離測試資料庫與帳號均已清理，未以展示資料庫進行破壞性整合測試。桌面原始專案未改，沒有 Git 提交或上傳。

階層鎖保護的是透過此服務的寫入，不是資料庫層的循環約束；直接 SQL／未採用此鎖的其他程式仍可能引入錯誤。鎖依赖分類僅軟刪除及正常遞增 ID；未來若新增實體刪除、重編 ID 或匯入功能，必須重新設計共用鎖。已測兩個同時交易，不等同已完成多節點或大量資料壓力測試。前台既有錯誤資料全盤偵測、分類樹批次查詢及深層樹壓力測試仍未完成。

## 建議接續順序

1. 購物車多商品查詢批次化與回歸測試。
2. 既有分類樹查詢／錯誤資料檢查，並測試較深層分類。
3. 孤兒圖片清理及前後端功能模組整理。
4. 持久展示資料啟動方式、正式反向代理／Cookie 部署設定及 SQL Server 2022 驗收。
5. 依規格確認訪客結帳；正式金流、通知與雲端圖片屬後續擴充，尚未實作。
