# 喵喵商城 · 貓用品電商（Spring Boot + Vue 3 + MS SQL Server）

> 公開原始碼為學習／展示用途，不是正式商店部署。資料庫密碼與 JWT 金鑰必須自行設定；正式部署請使用 `mssql` profile，關閉一鍵展示登入，並設定 HTTPS、Cookie 與 CORS。商品圖片為 AI 示意圖。

> 結帳送單離頁提示：[CHECKOUT_LEAVE_CONFIRMATION_NOTES.md](CHECKOUT_LEAVE_CONFIRMATION_NOTES.md)，原識別碼、離頁不取消下單、待確認結果與回應同時到達防護。

> 會員原因草稿確認：[MEMBER_REASON_CONFIRMATION_NOTES.md](MEMBER_REASON_CONFIRMATION_NOTES.md)，停用／恢復主題視窗、原因保留、版本與登入身分隔離。

> 列表下架／停用確認：[ADMIN_LIST_CONFIRMATION_NOTES.md](ADMIN_LIST_CONFIRMATION_NOTES.md)，操作對象核對、取消保留、重複操作與跨頁回應防護。

> 出貨與訂單確認：[SHIPMENT_CONFIRMATION_NOTES.md](SHIPMENT_CONFIRMATION_NOTES.md)，物流摘要、草稿保留、明確確認與重複送出防護。

> SKU／圖片草稿確認：[ASSET_DRAFT_CONFIRMATION_NOTES.md](ASSET_DRAFT_CONFIRMATION_NOTES.md)，主題離頁／重新載入確認、操作鎖定與桌機／手機驗證。

> Admin 草稿確認：[ADMIN_DRAFT_CONFIRMATION_NOTES.md](ADMIN_DRAFT_CONFIRMATION_NOTES.md)，商品、分類與優惠券離頁／重新載入主題確認，保留版本與表單規則。

> 下單最後確認：[CHECKOUT_REVIEW_NOTES.md](CHECKOUT_REVIEW_NOTES.md)，商品、收件、付款與優惠金額摘要，確認前不送單、變更／離頁／登出取消舊確認。

> 結帳優惠復原：[COUPON_RECOVERY_NOTES.md](COUPON_RECOVERY_NOTES.md)，8 種失效原因、保留優惠碼與收件草稿、明確移除／重新報價與名額並發驗證。

> 前後台視覺整理：[VISUAL_REFINEMENT_NOTES.md](VISUAL_REFINEMENT_NOTES.md)，前台主視覺／商品卡片、Admin 圖示／篩選／表格與桌機、平板、手機驗證。

> 草稿確認優化：[UNSAVED_CONFIRMATION_NOTES.md](UNSAVED_CONFIRMATION_NOTES.md)，會員資料／密碼與地址的離頁、重新載入主題確認，保留編輯與跨頁／會員隔離。

> 卡片與頁首互動：[QUICK_ADD_HEADER_HOVER_NOTES.md](QUICK_ADD_HEADER_HOVER_NOTES.md)，圖片進詳情、卡片快速加入／規格選擇及會員、購物車移入選單。

> 前台確認優化：[STOREFRONT_CONFIRMATION_NOTES.md](STOREFRONT_CONFIRMATION_NOTES.md)，取消訂單、刪除地址的主題確認視窗與跨頁／會員隔離。

> 前台互動優化：[STOREFRONT_FEEDBACK_NOTES.md](STOREFRONT_FEEDBACK_NOTES.md)，可關閉成功通知、持續顯示的錯誤提示及支援減少動態效果的點擊回饋。

> 使用者優先指定：[PRODUCT_IMAGES_CART_DRAWER_NOTES.md](PRODUCT_IMAGES_CART_DRAWER_NOTES.md)，10 張本地 AI 商品示意圖、保留自訂圖片的 V13 更新及即時右側購物車。

> 計畫表 M01 第十七批：[CHECKOUT_AMOUNT_NOTES.md](CHECKOUT_AMOUNT_NOTES.md)，後端結帳報價、逐商品單價／折扣／運費／總額確認、改價衝突復原與前後台預覽。

> 計畫表 M01 第十六批：[CHECKOUT_SNAPSHOT_NOTES.md](CHECKOUT_SNAPSHOT_NOTES.md)，後端交易內購物車快照比對、變更衝突復原、原請求重播與版本 2 防護。

> 計畫表 M01 第十五批：[CHECKOUT_IDENTITY_NOTES.md](CHECKOUT_IDENTITY_NOTES.md)，結帳識別碼保存、斷線查回／原請求重試、會員隔離與後端版本防護。

> 計畫表 M01 第十四批：[ORDER_REQUEST_ID_NOTES.md](ORDER_REQUEST_ID_NOTES.md)，後端下單識別碼、同請求重送及會員限定結果查回；前端自動產生／保留識別碼待下一批。

> 計畫表 M01 第十三批：[CHECKOUT_OUTCOME_NOTES.md](CHECKOUT_OUTCOME_NOTES.md)，送單離頁提醒、晚到回應防護與結果不明時的確認流程。

> 計畫表 M01 第十二批：[CHECKOUT_RECOVERY_NOTES.md](CHECKOUT_RECOVERY_NOTES.md)，結帳同步保留填寫內容、庫存不足提示與下單衝突復原。

> 計畫表 M01 第十一批：[CART_SYNC_NOTES.md](CART_SYNC_NOTES.md)，購物車庫存／價格重新同步、失敗保留資料與結帳防護。

> 計畫表 M01 第十批：[PRODUCT_CART_ACTION_NOTES.md](PRODUCT_CART_ACTION_NOTES.md)，商品頁購物錯誤獨立提示、重試及跨商品操作回應防護。

> 計畫表 M01 第九批：[CATALOG_ASYNC_NOTES.md](CATALOG_ASYNC_NOTES.md)，商品先顯示、推薦獨立載入與跨商品／離頁延遲回應防護。

> 計畫表 M01 第八批：[CATALOG_RECOVERY_NOTES.md](CATALOG_RECOVERY_NOTES.md)，商品／相關商品獨立重試與 SQL Server 下架後返回縮減列表。

> 計畫表 M01 第七批：[CATALOG_ROUTE_STATE_NOTES.md](CATALOG_ROUTE_STATE_NOTES.md)，分類內篩選維持 slug，以及前台／整合模式的商品來源返回與分頁回歸。

> 計畫表 M01 第六批：[SEARCH_RULE_NOTES.md](SEARCH_RULE_NOTES.md)，搜尋原始長度限制、Admin 狀態正規化與 SQL Server 真實分頁／排序驗證。

> 計畫表 M01 第五批：[PAGINATION_UI_NOTES.md](PAGINATION_UI_NOTES.md)，前後台共用每頁筆數／頁碼跳轉、Admin 商品／訂單排序選單與前台商品末頁校正。

> 計畫表 M01 第四批：[QUERY_GUARD_NOTES.md](QUERY_GUARD_NOTES.md)，後端重複列表 query 拒絕、Admin 末頁校正與會員歷程獨立 page／size。

> 計畫表 M01 第三批：[INVENTORY_LIST_STATE_NOTES.md](INVENTORY_LIST_STATE_NOTES.md)，庫存列表網址狀態／安全返回，以及 Admin 商品／訂單／分類分頁上限與嚴格排序。

> 計畫表 M01 第二批：[ADMIN_LIST_STATE_NOTES.md](ADMIN_LIST_STATE_NOTES.md)，五種主要 Admin 列表共用參數驗證與 size／sort 返回保留；庫存列表及後端全面統一仍待後續。

> 計畫表 M01 第一批：[LIST_STATE_NOTES.md](LIST_STATE_NOTES.md)，前台列表網址參數驗證、訂單／收藏分頁與詳情返回；後台全面統一尚待後續批次。

> 計畫表 R09：[ADDRESS_ROUTE_NOTES.md](ADDRESS_ROUTE_NOTES.md)，收件地址列表／新增／編輯獨立頁、本人單筆 GET 與預設地址並行防護。

> 計畫表 R08：[ACCOUNT_ROUTE_NOTES.md](ACCOUNT_ROUTE_NOTES.md)，會員中心總覽、個人資料／密碼獨立頁與跨分頁登出驗證。

> 計畫表 R07：[MEMBER_ROUTE_NOTES.md](MEMBER_ROUTE_NOTES.md)，Admin 會員詳情／歷程獨立頁、安全單筆 GET 與狀態操作防護。

> 計畫表 R06：[COUPON_ROUTE_NOTES.md](COUPON_ROUTE_NOTES.md)，優惠券新增／編輯獨立頁、單筆 GET、UTC 日期與版本防護。

> 計畫表 R05：[CATEGORY_ROUTE_NOTES.md](CATEGORY_ROUTE_NOTES.md)，分類新增／編輯獨立頁、完整階層排除與列表條件返回。

> 計畫表 R04：[PRODUCT_ASSET_ROUTE_NOTES.md](PRODUCT_ASSET_ROUTE_NOTES.md)，SKU／圖片獨立頁、圖片專用 API 與 SQL Server 並行更新驗證。

> 計畫表 R03：[PRODUCT_ROUTE_NOTES.md](PRODUCT_ROUTE_NOTES.md)，Admin 商品新增／編輯獨立頁、列表條件返回與主檔／SKU 草稿保護。

> 計畫表 R01／R02：[ORDER_ROUTE_NOTES.md](ORDER_ROUTE_NOTES.md)，Router 分模組、Admin 訂單詳情／出貨獨立頁與列表條件保留。

> 固定金額優惠券：[COUPON_NOTES.md](COUPON_NOTES.md)，含 Admin 管理、結帳折抵、名額限制與取消返還。

> 人工出貨登錄：[SHIPMENT_NOTES.md](SHIPMENT_NOTES.md)，含物流單號、出貨紀錄及出貨／取消競態驗證；不是物流即時追蹤。

前後端分離的貓用品電商示範專案，涵蓋核心購物流程：
**瀏覽商品 → 加入購物車 → 結帳 → 產生訂單**。

- 後端：Java 17 + Spring Boot 3.2 + Spring Data JPA + Spring Security(JWT) + Flyway + MapStruct
- 前端：Vue 3 + Vite + Vue Router + Pinia + axios
- 資料庫：Microsoft SQL Server 2022（本機以 Docker 啟動）
- 本機開發，前後端分離啟動（不做 CI/CD）

> 本次實作與驗證範圍請先閱讀 [IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md)。
> 第三批：請閱讀 [MAPPING_INTEGRATION_NOTES.md](MAPPING_INTEGRATION_NOTES.md)，包含 Mapper 與真實 SQL／瀏覽器測試。
> 第四批：請閱讀 [FRONTEND_STABILITY_NOTES.md](FRONTEND_STABILITY_NOTES.md)，包含欄位錯誤、載入重試、跨分頁登入同步與防連點。
> 第五批：[DEMO_LOGIN_NOTES.md](DEMO_LOGIN_NOTES.md) 說明前台／後台一鍵展示登入與正式環境關閉方式。
> 第六批：[SESSION_CLEANUP_NOTES.md](SESSION_CLEANUP_NOTES.md) 說明過期登入紀錄分批清理與排程設定。
> 最新第七批：[LIST_QUERY_NOTES.md](LIST_QUERY_NOTES.md) 說明商品／後台訂單列表批次查詢與首次購物車建立競態修正。
> 前後台獨立網址與 VS Code 啟動方式：[SEPARATE_SITES_NOTES.md](SEPARATE_SITES_NOTES.md)。前台 5173，後台 5174，不共用頁面導覽。
> 後台分類列表最新改進：[CATEGORY_QUERY_NOTES.md](CATEGORY_QUERY_NOTES.md)，改用資料庫分頁、批次計數及字面關鍵字搜尋。
> 分類階層防護：[CATEGORY_HIERARCHY_NOTES.md](CATEGORY_HIERARCHY_NOTES.md)，避免循環上層、處理同時互移及後台選單排除下層分類。
> 購物車多商品查詢：[CART_QUERY_NOTES.md](CART_QUERY_NOTES.md)，一次取得項目／規格／商品，批次載入主圖並保留售價更新規則。
> 後台進階篩選：[ADMIN_FILTER_NOTES.md](ADMIN_FILTER_NOTES.md)，商品分類／品牌、啟用狀態、訂單會員／日期、清除條件及查詢競態防護。
> 購物車並行操作防護：[CART_CONCURRENCY_NOTES.md](CART_CONCURRENCY_NOTES.md)，同規格加購累加、庫存上限、結帳與修改互斥，以及 SQL Server 實測。
> 前端購物車狀態防護：[CART_STATE_NOTES.md](CART_STATE_NOTES.md)，操作依序執行、讀取去重、登入切換及清空後忽略舊回應。
> 台灣寵物電商風格改版：[VISUAL_REDESIGN_NOTES.md](VISUAL_REDESIGN_NOTES.md)；可實作功能與優先順序：[COMMERCE_FEATURE_BACKLOG.md](COMMERCE_FEATURE_BACKLOG.md)。
> 前台頁首紅色小圖示：[HEADER_ICON_NOTES.md](HEADER_ICON_NOTES.md)，會員／購物車圖示、縮小操作區及導覽回歸驗證。
> 最新頁首調整：[HEADER_THEME_NOTES.md](HEADER_THEME_NOTES.md)，前台改回主題綠、前後台小巧登出圖示、Admin 帳號區對齊。
> 會員商品收藏：[WISHLIST_NOTES.md](WISHLIST_NOTES.md)，商品卡愛心、收藏分頁、會員隔離與並行操作防護。
> 最新展示啟動方式：[PERSISTENT_PREVIEW_NOTES.md](PERSISTENT_PREVIEW_NOTES.md)，重啟保留展示資料、Windows 加密設定與 VS Code 啟動工作；取代先前暫存展示啟動方式。
> 前台品牌／價格篩選：[PRODUCT_FILTER_NOTES.md](PRODUCT_FILTER_NOTES.md)，組合查詢、URL 保留、清除條件與競態防護。
> 前台現貨篩選：[STOCK_FILTER_NOTES.md](STOCK_FILTER_NOTES.md)，至少一個啟用規格有庫存、組合查詢及分頁防重複。
> Admin 低庫存清單：[LOW_STOCK_NOTES.md](LOW_STOCK_NOTES.md)，SKU 預警／缺貨、全條件統計與商品管理導覽。
> Admin 庫存異動紀錄：[STOCK_MOVEMENT_NOTES.md](STOCK_MOVEMENT_NOTES.md)，建立規格／人工調整／下單／取消的交易內紀錄與歷史快照，Flyway V7。
> 忘記／重設密碼：[PASSWORD_RESET_NOTES.md](PASSWORD_RESET_NOTES.md)，前後台入口、15 分鐘一次性憑證、限流與全登入撤銷，Flyway V8；本機測試信件，不是 SMTP。
> Admin 會員管理：[ADMIN_MEMBER_NOTES.md](ADMIN_MEMBER_NOTES.md)，會員查詢、一般會員停用／恢復、登入撤銷、版本與操作紀錄，Flyway V9；所有管理員帳號禁止從此功能停用。

---

## 0. 建置驗證結果（已實測）

| 項目 | 指令 | 結果 |
|---|---|---|
| 後端 | `mvn test` | 結帳、併發、授權、API 契約、圖片與限流測試；最新結果見實作紀錄 |
| 前端 | `npm ci`、`npm run build` | Vue 正式建置通過；已新增後台頁面 |
| 本機 SQL Server | `backend/scripts/verify-mssql.ps1` | 在唯一命名的暫存資料庫驗證 SQL 遷移，結束後清除 |
| 真實整合 | `backend/scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser` | Express 17.x：Flyway、Hibernate validate、真實 HTTP 及 Vue 購物流程通過；僅操作隔離測試資料庫 |

> 使用 Java 21 以 Java 17 目標編譯。本機 SQL Server Express 17.x 的 JDBC/Flyway/瀏覽器實連已驗證；規格指定的 SQL Server 2022 仍須在目標環境重跑。

---

## 1. 環境需求

| 工具 | 版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 20.19+ 或 22.12+（Vite 8 需求） |
| npm | 9+ |
| Docker | 任一近期版本（用於跑 MS SQL） |

---

## 2. 啟動 MS SQL Server（Docker）

```powershell
# 在專案根目錄，以同一個 PowerShell 工作階段設定環境變數。
# 密碼輸入不會顯示；請自行選擇符合 SQL Server 強度要求的密碼。
$dbSecret = Read-Host 'SQL Server 密碼' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
$env:APP_JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
docker compose up -d

# 待 SQL Server 就緒後建立 catshop。密碼由容器環境提供，不寫入原始碼。
docker exec catshop-mssql bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "IF DB_ID(''catshop'') IS NULL CREATE DATABASE catshop;"'
```

> 若容器內路徑不同，可改用 `/opt/mssql-tools/bin/sqlcmd`（舊版映像）。
> 也可以直接用 Azure Data Studio / SSMS 連線 `localhost,1433` 建立 `catshop` 資料庫。

**資料庫連線資訊**

| 項目 | 值 |
|---|---|
| Host / Port | `localhost:1433` |
| Database | `catshop` |
| User | `sa` |
| Password | 自行設定的 `DB_PASSWORD`（不提交或上傳） |
| JDBC URL | `jdbc:sqlserver://localhost:1433;databaseName=catshop;encrypt=true;trustServerCertificate=true` |

---

## 3. 啟動後端（Spring Boot，port 8080）

```bash
cd backend
mvn spring-boot:run
```

需沿用上一節已設定 `DB_PASSWORD` 與 `APP_JWT_SECRET` 的工作階段。Spring Boot 不會自動讀取 Docker 的 `.env`；使用其他終端時請重新設定環境變數。既有 Windows 持久展示啟動腳本會讀取加密設定，不需要重建展示資料。

啟動時 Flyway 會自動執行：
- `V1__init.sql` — 建立 10 張資料表（含主鍵、外鍵、索引、約束）
- `V2__seed.sql` — 寫入分類、商品、SKU 與測試帳號
- `V3__checkout_consistency.sql` — SKU／訂單版本鎖、付款方式與複合索引
- `V4__product_information.sql` — 成分、適用年齡、產地、保存方式
- `V5__auth_sessions.sql` — Refresh Token 工作階段、輪替雜湊與撤銷

驗證：
- API 文件（Swagger UI）：<http://localhost:8080/swagger-ui.html>
- 健康檢查：<http://localhost:8080/api/health>

---

## 4. 啟動前端（Vue 3 + Vite，port 5173）

```bash
cd frontend
npm install
npm run dev
```

開啟 <http://localhost:5173>

> 前端以 Vite proxy 將 `/api` 轉發到 `http://localhost:8080`，因此瀏覽器看到的是同源請求，
> 本機開發不會觸發 CORS。後端同時也設定了 CORS 白名單（`http://localhost:5173`）作為備援。

---

## 5. 預設帳號

| 角色 | Email | 密碼 |
|---|---|---|
| 一般會員 | `customer@catshop.local` | `password123` |
| 管理員 | `admin@catshop.local` | `password123` |

---

## 6. 專案結構

```
cat-ecommerce/
├── backend/                     # Spring Boot
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/catshop/
│       │   ├── CatShopApplication.java
│       │   ├── common/          # ApiResponse, PageResult
│       │   ├── config/          # Security, CORS, OpenAPI
│       │   ├── controller/      # 10 個 Controller（含 admin/ 後台 3 個）
│       │   ├── dto/             # 請求/回應 DTO（含驗證）
│       │   ├── entity/          # 10 個 JPA Entity
│       │   ├── exception/       # 全域例外處理
│       │   ├── repository/      # Spring Data JPA
│       │   ├── security/        # JWT provider / filter
│       │   └── service/         # 商業邏輯
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml
│           └── db/migration/    # V1__init.sql, V2__seed.sql
└── frontend/                    # Vue 3 + Vite
    ├── package.json
    ├── vite.config.js           # proxy /api -> :8080
    └── src/
        ├── api/                 # axios 封裝 + API 模組
        ├── assets/main.css
        ├── router/              # 12 條路由
        ├── stores/              # Pinia: auth / cart / product
        ├── views/               # 11 個頁面元件
        ├── App.vue
        └── main.js
```

---

## 7. API 端點一覽

所有回應皆包在統一信封：

```json
// 成功
{ "success": true, "data": { ... }, "timestamp": "2026-10-07T10:00:00" }
// 失敗
{ "success": false, "error": { "code": "VALIDATION_ERROR", "message": "...", "details": [...] }, "timestamp": "..." }
```

| 模組 | 方法 | 路徑 | 說明 | 需登入 |
|---|---|---|---|---|
| Auth | POST | `/api/auth/register` | 註冊 | ✗ |
| Auth | POST | `/api/auth/login` | 登入（回傳 JWT） | ✗ |
| Member | GET | `/api/members/me` | 取得個人資料 | ✓ |
| Member | PUT | `/api/members/me` | 更新個人資料 | ✓ |
| Member | PUT | `/api/members/me/password` | 變更密碼 | ✓ |
| Address | GET | `/api/members/me/addresses` | 列出地址 | ✓ |
| Address | POST | `/api/members/me/addresses` | 新增地址 | ✓ |
| Address | PUT | `/api/members/me/addresses/{id}` | 更新地址 | ✓ |
| Address | DELETE | `/api/members/me/addresses/{id}` | 刪除地址 | ✓ |
| Category | GET | `/api/categories` | 分類樹 | ✗ |
| Category | GET | `/api/categories/tree` | 分類樹 | ✗ |
| Category | GET | `/api/categories/{id}` | 依 ID 查分類 | ✗ |
| Category | GET | `/api/categories/slug/{slug}` | 依 slug 查分類 | ✗ |
| Product | GET | `/api/products` | 商品列表（分頁/篩選/搜尋） | ✗ |
| Product | GET | `/api/products/{id}` | 商品詳情 | ✗ |
| Product | GET | `/api/products/slug/{slug}` | 商品詳情（slug） | ✗ |
| Product | GET | `/api/products/{id}/related` | 相關商品 | ✗ |
| Cart | GET | `/api/cart` | 取得購物車 | ✓ |
| Cart | POST | `/api/cart/items` | 加入購物車 | ✓ |
| Cart | PATCH | `/api/cart/items/{itemId}` | 更新數量（保留 PUT 相容） | ✓ |
| Cart | DELETE | `/api/cart/items/{itemId}` | 移除項目 | ✓ |
| Cart | DELETE | `/api/cart` | 清空購物車 | ✓ |
| Order | POST | `/api/orders` | 結帳建立訂單 | ✓ |
| Order | GET | `/api/orders` | 我的訂單列表 | ✓ |
| Order | GET | `/api/orders/{id}` | 訂單詳情 | ✓ |
| Order | POST | `/api/orders/{id}/cancel` | 取消訂單 | ✓ |
| **AdminCategory** | GET | `/api/admin/categories` | 後台分類列表（含停用、含商品數） | ADMIN |
| **AdminCategory** | POST | `/api/admin/categories` | 新增分類 | ADMIN |
| **AdminCategory** | PUT | `/api/admin/categories/{id}` | 更新分類 | ADMIN |
| **AdminCategory** | DELETE | `/api/admin/categories/{id}` | 停用分類（軟刪除） | ADMIN |
| **AdminProduct** | GET | `/api/admin/products` | 後台商品列表（含下架、含庫存摘要） | ADMIN |
| **AdminProduct** | GET | `/api/admin/products/{id}` | 後台商品詳情（編輯用） | ADMIN |
| **AdminProduct** | POST | `/api/admin/products` | 新增商品（含 SKU／圖片） | ADMIN |
| **AdminProduct** | PUT | `/api/admin/products/{id}` | 更新商品主檔 | ADMIN |
| **AdminProduct** | DELETE | `/api/admin/products/{id}` | 下架商品（軟刪除） | ADMIN |
| **AdminProduct** | POST | `/api/admin/products/{id}/skus` | 新增商品規格 SKU | ADMIN |
| **AdminProduct** | PUT | `/api/admin/skus/{skuId}` | 更新 SKU（價格／庫存） | ADMIN |
| **AdminOrder** | GET | `/api/admin/orders` | 後台訂單列表（狀態／會員／日期篩選） | ADMIN |
| **AdminOrder** | GET | `/api/admin/orders/{id}` | 後台訂單詳情 | ADMIN |
| **AdminOrder** | PATCH | `/api/admin/orders/{id}/status` | 更新訂單狀態（出貨／完成／取消） | ADMIN |
| Image | POST | `/api/admin/uploads/images` | 圖片上傳 | ADMIN |
| Image | GET | `/api/images/{filename}` | 圖片讀取 | ✗ |
| Health | GET | `/api/health` | 健康檢查 | ✗ |

> 後台端點由 `SecurityConfig` 的
> `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 與 Controller 層級
> `@PreAuthorize("hasRole('ADMIN')")` 雙層守護；訂單狀態機集中於 `AdminOrderService`，
> 取消訂單會於同一交易回補 SKU 庫存。詳見 `documents/admin-api/ADMIN_API_REFERENCE.md`。

---

## 8. 資料庫設計（10 張表）

`member` · `address` · `category` · `product` · `product_image` · `product_sku` · `cart` · `cart_item` · `orders` · `order_item`

MS SQL 型別對應重點：

| 用途 | MS SQL 型別 |
|---|---|
| 主鍵 | `BIGINT IDENTITY(1,1)` |
| 文字（含中文） | `NVARCHAR(n)` / `NVARCHAR(MAX)` |
| 金額 | `DECIMAL(10,2)` |
| 時間 | `DATETIME2(3)` |
| 布林 | `BIT` |

---

## 9. 常見問題

**Q：後端啟動時報 `Cannot open database "catshop"`？**
A：Flyway 不會建立 database，請先執行第 2 步的 `CREATE DATABASE catshop`。

**Q：連線報憑證錯誤（SSL）？**
A：本機開發已在 JDBC URL 加上 `encrypt=true;trustServerCertificate=true`，可略過自簽憑證驗證。

**Q：中文變亂碼？**
A：所有字串欄位使用 `NVARCHAR`，寫入時字串常值需加 `N'...'` 前綴（遷移檔已處理）。

**Q：前端呼叫 API 出現 401？**
A：需先登入取得 JWT；axios 攔截器會帶上 `Authorization: Bearer <token>`，過期時先用 HttpOnly Cookie 更新並重送一次；更新失敗才導向登入頁。

---

## 10. 後續可延伸（非 MVP）

- ~~商品管理後台（ADMIN 角色 CRUD）~~ ✅ 已完成（14 個後台端點，見第 7 節）
- 金流串接（綠界 / Stripe）與付款狀態機
- 圖片儲存遷移至物件儲存／CDN（本機上傳已完成）
- 訂單狀態通知（Email / 簡訊）
- 在 SQL Server 2022 重跑真實整合驗收（Express 17.x 的 JDBC/Flyway 與瀏覽器實連已通過）
