# 規格書續作紀錄

本批依上傳的系統規劃文件補齊會員購物流程與管理後台。保留既有三層架構與 CUSTOMER / ADMIN 角色。

第二批已補上登入更新／輪替／撤銷，最新行為與驗證請見 `AUTH_IMPLEMENTATION_NOTES.md`。
第三批已加入 MapStruct 與真實 SQL／瀏覽器整合測試，詳見 `MAPPING_INTEGRATION_NOTES.md`。

## 已完成

- SKU 與訂單加入 `@Version`，併發更新不成功時整筆交易回滾；透過 HTTP 409 告知重試。
- 結帳檢查商品／SKU 上下架，以當下 SKU 價格建立訂單快照；購物車重新載入時更新價格。
- 支援 COD 貨到付款與 MOCK 模擬付款，不接受或儲存卡號。
- `PaymentService` / `ShippingService` 介面與 MVP 實作；免運門檻 1000 元，未滿運費 80 元。
- 尚未出貨的 PENDING / PAID 訂單可以取消；取消回補庫存，重複取消不重複回補。
- COD 可由 PENDING 出貨；其他未付款訂單不可出貨。
- 商品列表支援 `sort=price,asc|desc`、`createdAt,asc|desc`、`name,asc|desc`；分類包含子分類商品。
- 公開商品列表與詳情排除下架商品；新增相關商品 API。
- 分類 API 回傳分類樹；依 ID 查分類，slug 查詢另有明確路徑。
- 購物車數量採 PATCH；舊 PUT 端點暫時保留相容。
- 回應增加規格的 `code/message/data/errors` 欄位，保留既有 `success/error/timestamp` 供舊客戶端解包。
- 註冊、加入購物車與建立訂單回傳 HTTP 201。
- 登入每個來源 IP 每分鐘最多 10 次，超過回 429 / Retry-After；此為單一節點限制器。
- 每次 JWT 認證確認最新帳號狀態與角色，停用帳號／撤銷管理權限立即生效。
- 一般錯誤不回傳內部例外內容；新增 `/api/health`。
- Vue 搜尋、分類 slug 路由、商品多圖、成分／年齡／產地／保存欄位、付款選單。
- 管理後台商品新增／編輯／下架、SKU 新增／價格／庫存、分類 CRUD、訂單詳情／狀態操作。
- SKU 後台編輯需附版本，避免過期表單覆蓋結帳後庫存。
- 圖片由 ADMIN 上傳至本機儲存，限 PNG / JPEG、5 MB、1600 萬像素；隨機檔名與路徑限制。
- 會員收件地址新增／編輯／刪除／預設地址操作。
- 本機版與正式 SQL Server profile 分開；正式 profile 必須提供資料庫與 JWT 環境變數。
- Vite 更新至 8.3.3 以上、Vue plugin 更新至 6.0.9 以上，修正原專案的 Vite / esbuild 安全報告；Node 需 20.19+ 或 22.12+。

## 端點契約

```text
GET   /api/health
GET   /api/categories                 分類樹
GET   /api/categories/tree            相容路徑
GET   /api/categories/{id}
GET   /api/categories/slug/{slug}
GET   /api/products?categoryId=&keyword=&page=0&size=12&sort=price,asc
GET   /api/products/{id}/related
PATCH /api/cart/items/{itemId}         {quantity}
POST  /api/orders                     {receiverName, receiverPhone, shippingAddress, remark, paymentMethod}
POST  /api/admin/uploads/images       multipart/form-data，file 欄位
GET   /api/images/{filename}          圖片二進位回應
PUT   /api/admin/skus/{skuId}          既有欄位另需 version
```

前端新增 `/search?q=...`、`/category/:slug`、`/product/:id` 相容路由、`/admin/products`、`/admin/categories`、`/admin/orders`。

`paymentMethod` 僅允許 COD 或 MOCK；MOCK 下單同步視為已付款。這是明示的測試選項，不是第三方金流。

## 驗證方式與限制

第一批本機驗證：後端 `mvn test` 共 17 個測試，失敗 0、錯誤 0、略過 0；Vite 8.3.3 正式建置與無介面 Edge 操作測試通過；npm 安全檢查 0 項漏洞；SQL V1–V4、Unicode、庫存 CHECK 與過期版本更新拒絕已於 SQL Server Express 17.0.1000.7 通過。第二批驗證紀錄見下方。

```powershell
cd backend
mvn test
```

Spring Boot / JPA / HTTP 測試採 H2 記憶體資料庫，涵蓋併發結帳、快照、庫存不足、取消、會員隔離、API 契約、最新角色／停用狀態、SKU 過期表單、搜尋排序與子分類。

Windows 本機已安裝 SQL Server 與 sqlcmd 時：

```powershell
cd backend
.\scripts\verify-mssql.ps1 -Server '.\SQLEXPRESS'
```

此腳本只建立 `catshop_verify_<隨機識別碼>`，執行 V1–V5 與資料檢查後，在 finally 中清除此測試資料庫。需有建立／刪除資料庫的本機權限。不操作既有 catshop 或其他資料庫。

這項測試證明 SQL 腳本可由本機 SQL Server 執行；它沒有使用 Flyway，因此不等同完整 JDBC、Flyway history 或 Hibernate validate 啟動測試。本機為 Express 17.x，尚未於規格指定 SQL Server 2022 執行。

前端：

```powershell
cd frontend
npm ci
npm run build
```

`frontend/scripts/smoke.cjs` 使用 Playwright + 本機 Edge 無介面瀏覽器測試；先在 5175 啟動 Vite，再設定 PLAYWRIGHT_MODULE 指向已安裝的 Playwright 模組執行。測試的 API 是攔截的固定資料，不能當作前後端連線測試。

## 環境與資料升級

- 現有資料庫保留 V1/V2；新增 V3/V4/V5 由 Flyway 執行，不修改舊 migration 避免 checksum 差異。
- 新增欄位可為空；舊商品不臆造品牌的成分、產地或年齡資訊，管理員可在後台填入。
- `application-local.yml` 為 localhost SQL Server 2022 開發設定；先建立 catshop 資料庫。
- 正式環境用 `SPRING_PROFILES_ACTIVE=mssql`，提供 DB_HOST、DB_PORT、DB_NAME、DB_USER、DB_PASSWORD、APP_JWT_SECRET。憑證必須由 JVM 信任。
- 本機圖片預設放在 backend/uploads；不包含在原始碼 ZIP 中，需自行持久化及備份。
- SKU 管理頁讀取 API 回應的 version，儲存時原樣附上；409 時重新載入商品後再編輯。

## 未完成／需另外決定

- 訪客結帳：規格摘要建議不強制註冊，但 API 表與路由表要求登入。本批採明確 API 表的會員結帳，沒有自行改成訪客身分模型。
- Refresh Token 與 token 輪替／撤銷：第二批已加入，詳見下方。部署更新後，原本不含 sid 的 JWT 必須重新登入。
- package 仍依 Controller / Service / Repository 分層，未全面改為 member/catalog/cart/order 模組包。
- DTO 映射已於第三批改為 MapStruct；15 個 Entity 回應 DTO 不再依賴 Entity，Service 注入 Mapper。
- Spring Boot + Flyway + 前端實連已於第三批在 Express 17.x 通過，SQL Server 2022 版本驗收仍待執行。
- 優惠券、評價、願望清單、第三方金流、物流追蹤依規格延後。

本批未建立 Git commit，也未上傳或發布專案。

## 關鍵程式碼位置

```text
backend/src/main/java/com/catshop/entity/ProductSku.java             SKU @Version
backend/src/main/java/com/catshop/entity/Order.java                  訂單 @Version / paymentMethod
backend/src/main/java/com/catshop/service/OrderService.java          交易、快照、扣庫存、取消
backend/src/main/java/com/catshop/service/admin/AdminProductService.java  後台 SKU 過期版本檢查
backend/src/main/java/com/catshop/service/admin/AdminOrderService.java    出貨與取消狀態機
backend/src/main/java/com/catshop/service/ProductService.java        商品可見性、排序、子分類
backend/src/main/java/com/catshop/security/LoginRateLimitFilter.java 登入限流
backend/src/main/java/com/catshop/security/JwtAuthenticationFilter.java   最新角色／停用狀態
backend/src/main/java/com/catshop/service/LocalFileStorage.java       圖片格式與路徑限制
backend/src/main/resources/db/migration/V3__checkout_consistency.sql
backend/src/main/resources/db/migration/V4__product_information.sql
backend/src/test/java/com/catshop/CheckoutIntegrationTest.java       14 個 JPA / HTTP 案例
frontend/src/views/AdminView.vue                                   後台操作
frontend/src/views/ProductListView.vue                             搜尋與排序
frontend/src/views/CheckoutView.vue                                付款與收件表單
frontend/scripts/smoke.cjs                                         瀏覽器操作測試
```
