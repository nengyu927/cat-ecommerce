# Admin 低庫存清單

日期：2026-10-08。本批新增低庫存查詢工作台，沒有新增庫存異動帳、自動補貨或直接批次改庫存，不更動 Flyway V1～V6／資料表。

已套用展示網站，沿用持久展示資料庫。重啟前後會員／訂單／購物車／收藏／商品筆數一致，沒有重建資料。

## 規則與使用

- Admin 選單新增「低庫存清單」，獨立後台 `/inventory`；前台不註冊此頁。管理員可查詢，匿名 401、一般會員 403。
- 規則：商品啟用、SKU 啟用、SKU stock 小於或等於 safetyStock。包含啟用但下架／售罄的商品；不以商品上架狀態或分類是否啟用排除，方便備貨。停用商品／規格不列入。
- 零庫存且安全庫存也是零，仍屬缺貨；等於安全庫存也列預警，但「補至安全庫存差額」為零。差額只是 `max(0,safetyStock-stock)`，不是採購建議量。
- 商品名稱或 SKU 編號搜尋，去除前後空白、不分大小寫；%、_、[、! 以字面比對，不當成 LIKE 萬用字元。關鍵字最多 200 字。
- 勾選「只看缺貨」再搜尋，只顯示 stock=0 的預警規格。關鍵字與條件寫入 URL，換頁／重新整理保留，清除會回第一頁。
- 摘要低庫存／缺貨數是符合關鍵字的全部預警 SKU，不是當頁數量；勾選只看缺貨不縮減低庫存摘要，列表總筆數另行顯示。
- 每頁 20 筆（API size 1～100），先按庫存升序，再按 SKU id，穩定分頁。多規格商品會有多列，每列明確顯示商品／SKU id、分類、商品狀態、規格、庫存與安全庫存。
- 「前往商品管理」帶入商品名稱搜尋；在既有編輯畫面補貨／修改安全庫存，再回此頁重新查詢。同條件再次按搜尋也會重新讀取。本頁本身不寫庫存，也沒有定時自動更新。
- 手機表格可區塊內橫向捲動；載入失敗可重試，查詢版本防止舊結果覆蓋。

## 程式碼依據

| 檔案 | 實作 |
|---|---|
| `backend/src/main/java/com/catshop/repository/ProductSkuRepository.java` | 低庫存資料庫分頁與 aggregate 統計；EntityGraph 一次載入 product/category，避免逐筆查詢。 |
| `backend/src/main/java/com/catshop/dto/admin/inventory/LowStockResponse.java` | 分頁 items、全條件 lowStockCount／outOfStockCount 與 SKU 摘要。 |
| `backend/src/main/java/com/catshop/service/admin/AdminInventoryService.java` | 門檻、查詢合法值、字面 LIKE 搜尋與穩定排序。 |
| `backend/src/main/java/com/catshop/controller/admin/AdminInventoryController.java` | GET `/api/admin/inventory/low-stock`，ADMIN 方法授權。 |
| `frontend/src/views/LowStockView.vue` | 搜尋、缺貨條件、URL、分頁、摘要、錯誤與手機表格。 |
| `frontend/src/AdminApp.vue`、`frontend/src/router/index.js` | Admin 選單、頁首標題、受保護路由及前台排除。 |
| `frontend/src/views/AdminView.vue` | 接收商品名稱 keyword 並套用商品搜尋；不自動打開或保存商品。 |
| `frontend/src/api/admin.js` | 沿用 Admin API 代理與授權，沒有改 cookie 邊界。 |

## 驗證範圍

- `LowStockIntegrationTest` 5 項：門檻／可見性／穩定分頁、摘要範圍、字面搜尋、空清單、補貨後移出、1／2 筆分頁最多 3 條 SQL、匿名／會員拒絕、管理員成功及非法參數。
- `npm run test:low-stock` 5 項：全清單摘要、篩選／分頁／重新整理／清除、重試、查詢競態、商品管理帶入與 390px 手機寬度、會員禁止存取。
- `npm run test:admin-filters` 回歸既有商品／分類／訂單篩選。
- SQL Server HTTP 整合新增唯一命名的下架商品與多規格，驗證摘要、分頁、字面萬用字元、補貨後更新及 ADMIN 權限；結束只移除此測試商品／SKU／分類。
- 真實瀏覽器購物回歸增加管理員低庫存頁載入；使用獨立測試 DB，沒有替身 API。
- 完整後端 96 項、SQL HTTP 整合 11 項、前台低庫存 5 項與既有 Admin 篩選回歸均通過；前後台正式建置通過。
- 展示 API 實測管理員成功、匿名 401、會員 403、非法分頁 400。當前種子商品尚未低於安全庫存，清單正常為空；未為展示效果而更改商品庫存。可在既有商品編輯畫面調整庫存／安全庫存後，重新搜尋查看預警。

本機 SQL Server Express 17.x，規格指定 SQL Server 2022 仍須在目標環境重跑。工作副本實作，桌面原始專案未修改；未提交或上傳。

下一步：庫存異動帳，讓人工調整、結帳扣庫存與取消還庫存都有原因、操作者與歷程；不把本批清單視為異動帳已完成。
