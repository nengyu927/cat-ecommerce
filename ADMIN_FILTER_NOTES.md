# 後台進階篩選（2026-10-08）

## 本批完成

- 商品管理：新增分類、完整品牌名稱、啟用／停用篩選，可與名稱、商品狀態一起使用。分類選項包含停用分類；categoryId 只比對該分類本身，不自動包含下層。
- 分類管理：新增啟用／停用篩選，明確送出 isActive=false，不將 false 誤當成未選擇。
- 訂單管理：新增會員編號、開始與結束日期，可與訂單編號、訂單狀態合併。列表顯示會員 ID、姓名、Email 與建立時間，方便對照。
- 共用「搜尋」和「清除篩選」。搜尋回第一頁；翻頁使用已套用的條件，不會偷套用尚未按搜尋的草稿。狀態選單維持選擇後立即套用的既有行為。切換模組清空全部篩選。
- 不合法的會員 ID、開始日期晚於結束日期在前端先提示，不送出篩選查詢。後端也拒絕非正會員 ID、顛倒日期及非法分頁，回 400。
- 查詢加入版本識別，忽略過時成功／失敗回應。切換模組或同時發出查詢時，舊回應不再覆蓋新的列表、錯誤、分類選項與載入狀態。
- 編輯用分類選項仍取全部分類，不套用列表篩選，保留先前自身／下層分類不可作上層的防護。

## 後端規則

商品 API 新增選填 `brand`，既有路徑 `/api/admin/products` 不變。去除輸入前後空白、不分大小寫，完整名稱比對，最多 100 字；空白代表不限。`%`、`_`、`!`、`[` 在品牌篩選不是萬用字元。未提供 brand 的既有 HTTP 用法不變，Service 保留原簽名並轉呼叫新版本。

訂單 `from`／`to` 仍為 `YYYY-MM-DD`，兩端包含當天；服務轉成 `createdAt >= 開始零點`、`createdAt < 結束日期隔天零點`。因此納入 23:59:59.999，排除隔天 00:00:00，可只填單邊日期。時間依既有訂單 createdAt 的應用程式表示，未新增業務時區或改動全站 UTC JDBC 設定。

不修改 Entity、DTO、Flyway V1～V5、資料表、JWT／權限、訂單狀態機或購物流程。本批沒有新增會員管理／會員搜尋 API；會員篩選目前輸入 ID。

## 程式依據

- `frontend/src/views/AdminView.vue`：filters 草稿、appliedFilters、日期／會員 ID 驗證、清除條件、request version 與進階篩選表單。
- `backend/src/main/java/com/catshop/controller/admin/AdminProductController.java`：選填 brand 參數。
- `backend/src/main/java/com/catshop/service/admin/AdminProductService.java`：相容舊簽名、新品牌輸入檢查與正規化。
- `backend/src/main/java/com/catshop/repository/ProductRepository.java`：品牌完整比對，可與既有條件共同使用。
- `backend/src/main/java/com/catshop/service/admin/AdminOrderService.java`：會員／日期／分頁檢查與隔天零點邊界。
- `backend/src/main/java/com/catshop/repository/OrderRepository.java`：結束上限改為排他 `<`。
- `backend/src/test/java/com/catshop/ListQueryIntegrationTest.java`：新增 3 個篩選測試，涵蓋品牌組合、特殊字元、完整日期邊界及非法輸入。
- `backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java`：新增真實 SQL／HTTP 驗證，含品牌、會員、狀態、同日最後一毫秒、隔天零點、分類布林及 400 回應。時間 fixture 用既有 JPA UTC JDBC 綁定並先確認讀回值。
- `frontend/scripts/admin-filters.cjs`：獨立 5177 Admin 畫面測試，全部 API 替身，不修改展示資料。涵蓋參數、false、翻頁、清除、模組切換、輸入驗證、完整分類選項及過時回應。
- `frontend/scripts/category-hierarchy.cjs`：原測試縮小到「上層分類」選單，不將新篩選表單的選項誤算入。
- `documents/admin-api/ADMIN_API_REFERENCE.md`：更新品牌與日期 API 用法。

## 驗證

最後完整重跑：70 個後端單元／H2 測試、8 個 SQL Server 整合測試，均為 0 失敗、0 錯誤。後台篩選畫面、分類階層畫面與前後台正式建置通過；既有真實 Edge 購物流程也通過。日期測試明確納入當天 23:59:59.999 並排除隔天 00:00:00。

測試命令（frontend）：

```powershell
npm run test:admin-filters
npm run test:category-hierarchy
npm run build
```

完整 SQL／Edge 驗證（backend）：

```powershell
$env:E2E_BROWSER_CHANNEL = 'msedge'
.\scripts\verify-app-mssql.ps1 -AllowOtherVersion -Browser
```

驗證過程曾遇到分類 UI 測試選取範圍過大、測試訂單編號超過既有欄位長度，以及直接 SQL fixture 與 JPA UTC 綁定不一致；均修正測試本身，不擴大欄位或改全站時區。結束日期也從原本整秒上限改成隔天零點排他比較。不能把 fixture 時區差異歸因於日期精度。

本機 SQL Server 為 Express 17.x；規格指定的 SQL Server 2022 仍待驗收。整合驗證使用唯一命名的隔離資料庫及專用帳號，結束後清理；不使用展示資料庫做破壞性測試。畫面替身測試與 SQL／HTTP 測試分開驗證，未宣稱新篩選已跑過完整真實瀏覽器端到端操作。

## 展示與交付狀態

本批尚未重啟展示後端或重建展示資料，等待使用者同意。前端開發頁會即時更新，但品牌 API 與新日期規則要啟動新版後端才完整生效。上一批重啟同意不作為本批清空資料的授權。

- 前台「喵喵商城」：`http://127.0.0.1:5173/`
- 後台「喵喵商城 Admin」：`http://127.0.0.1:5174/`

保留前後台獨立入口、一鍵展示登入、購物車批次查詢、分類階層防護及各批成果。桌面原始專案未修改，未提交或上傳。

接續建議：持久展示資料啟動方式、購物車同 SKU 並行操作防護、分類樹檢查／批次查詢及孤兒圖片清理。會員 ID 輸入仍可再擴充為會員搜尋選單，URL 分享／重新整理保留篩選條件也尚未實作。
