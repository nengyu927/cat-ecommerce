# 庫存異動紀錄

本批在工作副本實作，不修改桌面原始專案；未提交或上傳。

## 使用方式

Admin 側邊選單「庫存異動紀錄」，獨立後台 `/movements`；整合開發模式 `/admin/movements`。前台不註冊此管理頁。僅 ADMIN 可呼叫 GET `/api/admin/inventory/movements`。

- 可依 SKU ID、四種異動原因篩選，每頁 20 筆，由異動 ID 新到舊。API size 1～100、頁碼非負、SKU ID 正整數；非法原因拒絕。
- URL 保留篩選／分頁；再次搜尋相同條件會更新，清除回第一頁。失敗可重試；慢速旧回應不能覆蓋新結果。手機表格於區塊內捲動。
- 記錄建立規格時的正初始庫存、人工改庫存、結帳扣庫存、會員或 Admin 取消訂單還庫存。零初始庫存、數量未改變、只改售價／安全庫存不新增紀錄。
- 顯示異動前後數量、帶正負號的差額、商品名稱與 SKU 編號快照、原因、操作者會員 ID、關聯訂單編號。登入請求保存實際操作者；直接內部呼叫若沒有登入身分，明確顯示「操作者未提供」，不杜撰人名。
- 每筆紀錄與庫存更新同一交易；版本衝突、結帳／取消失敗會一起回滾，不留成功假紀錄。

## 資料邊界

Flyway V7 新增 `stock_movement` 與 SKU／訂單索引，限制非負庫存、正確且非零差額、合法原因。啟用前既有庫存及 V2 種子資料不補建歷史。直接修改 SQL 或另寫 Repository 的變更不會自動記錄，因此這是應用程式異動帳，不是完整資料庫稽核。

歷史 ID、商品名稱、SKU 編號、訂單編號為當時快照，不設連到可變實體的外鍵，避免後續更名或刪除連帶破壞歷史。不提供修改／刪除異動的 API；目前沒有資料保留期限／清理政策、CSV 匯出、日期區間、自由輸入調整備註或專用補貨操作頁。

## 主要檔案

- `backend/src/main/resources/db/migration/V7__stock_movements.sql`
- `backend/src/main/java/com/catshop/entity/StockMovement.java`
- `backend/src/main/java/com/catshop/repository/StockMovementRepository.java`
- `backend/src/main/java/com/catshop/service/StockMovementService.java`：MANDATORY 要求既有交易。
- `backend/src/main/java/com/catshop/service/OrderService.java`、`service/admin/AdminOrderService.java`、`service/admin/AdminProductService.java`：成功業務流程記錄。
- `backend/src/main/java/com/catshop/service/admin/AdminInventoryService.java`、`controller/admin/AdminInventoryController.java`、`dto/admin/inventory/StockMovementResponse.java`：管理員驗證、唯讀分頁 DTO。
- `frontend/src/views/StockMovementView.vue`、`src/AdminApp.vue`、`src/router/index.js`、`src/api/admin.js`。

## 驗證

完整後端 103 項與 SQL Server 整合 12 項均通過。後端新增交易、回滾、版本衝突、快照、分頁、零數量跳過、會員／管理員取消操作者及 ADMIN 權限測試；SQL Server HTTP 整合覆蓋建立、人工調整、下單／取消、改名後快照與 Flyway V7。

新頁面瀏覽器測試 5 項通過，覆蓋 URL／分頁／更新／清除、錯誤重試、舊回應、390px 版面及會員拒絕；低庫存 5 項、既有 Admin 篩選回歸與前後台建置通過。真實購物回歸也載入異動頁。測試資料庫與登入已依精確產生名稱清除，不影響展示 DB。

展示 API 實測 ADMIN 成功、匿名 401、一般會員 403、SKU ID=0 拒絕 400；更新前後會員 2、訂單 0、購物車明細 0、收藏 0、商品 10 均不變，異動紀錄 0（未補建歷史），Flyway 由 V6 升至 V7。實測僅建立並登出本次工具的登入，沒有調整展示商品庫存。

本機 SQL Server Express 17.x；仍須在規格指定的 SQL Server 2022 目標環境驗證。展示服務更新使用原持久資料庫，沒有重置會員、商品、訂單、購物車或收藏。

下一步建議：忘記密碼（本機測試郵件介面與一次性 token）、Admin 會員管理、物流單號與出貨歷程；真實郵件／金物流需另外確認服務與設定。
