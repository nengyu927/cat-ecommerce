# 第三批：MapStruct 與真實整合驗證

## 基準與範圍

以提供的桌面 cat-ecommerce-auth-updated/cat-ecommerce 為基準，檔案雜湊與第二批交付相同。在工作副本完成續作，沒有改動桌面版本，沒有建立 commit、上傳或發布。

本批補規格書的「Entity / DTO / Mapper（MapStruct）」與「前後端、Flyway、資料庫實連驗收」。不改 API 路徑、資料表、V1–V5 migration，也不重設既有資料庫。

## 實作

- 加入 MapStruct 1.6.3、編譯生成器與 Lombok binding，依[官方參考文件](https://mapstruct.org/documentation/1.6/reference/html/)及[官方 Lombok FAQ](https://mapstruct.org/faq/)設定。
- 五個 Mapper：MemberMapper、CatalogMapper、CartMapper、OrderMapper、AdminMapper；共用 DtoMapperConfig，Spring 注入、建構子注入、未映射的目標欄位直接讓編譯失敗。
- 15 個回應 DTO 移除 Entity import 與靜態 Entity 轉換方法，改為純 record；10 個 Service 注入 Mapper。AuthResponse 的靜態方法只包裝 Token 與純 MemberResponse，並非 Entity 映射，保留。
- 保留商品列表／詳情的欄位差異、主圖優先／首圖回退、分類父節點 ID、SKU 的 safetyStock/isActive/version、購物車 BigDecimal 小計，以及訂單歷史名稱／規格／價格快照。
- 商品、會員、後台 API 仍只回傳 DTO；沒有將密碼雜湊、認證工作階段或 Entity 關聯序列化到 JSON。
- 註冊與結帳欄位補 label/for/id，改善可存取性及瀏覽器驗證穩定性。
- 新增 Maven sqlserver-it profile：啟用 Failsafe 整合測試，正常 mvn test 不需要 SQL Server。
- 新增測試資料庫防護：只接受 catshop_it_ 加 32 位小寫十六進位識別碼、空白資料庫、相符的 DB_NAME；拒絕正式 catshop、重複 databaseName、將密碼放在 JDBC URL。預設要求 SQL Server 2022（major 16），缺少環境設定或版本不符會失敗，不會跳過後宣稱成功。
- 整合測試真正啟動隨機埠 Tomcat，使用 JDBC、Flyway V1–V5 與 Hibernate validate，透過真實 HTTP 驗證核心購物與認證。
- Browser 選項啟動本機 Vite 5175 與無介面瀏覽器，完整走 Vue → proxy → Spring Boot → SQL Server，不攔截或替換 API。只阻擋非本機外部圖片請求。

## 關鍵檔案與依據

| 檔案（專案相對路徑） | 關鍵內容 |
|---|---|
| backend/pom.xml | annotationProcessorPaths、MapStruct/Lombok binding、sqlserver-it/Failsafe |
| backend/src/main/java/com/catshop/mapper/DtoMapperConfig.java | unmappedTargetPolicy = ReportingPolicy.ERROR |
| backend/src/main/java/com/catshop/mapper/MemberMapper.java | 會員／地址只映射 DTO 欄位 |
| backend/src/main/java/com/catshop/mapper/CatalogMapper.java | category.id/name、父節點 ID、summary/detail、主圖回退 |
| backend/src/main/java/com/catshop/mapper/CartMapper.java | sku.product.id/name、BigDecimal 小計 |
| backend/src/main/java/com/catshop/mapper/OrderMapper.java | 從 OrderItem 快照映射，不讀即時 SKU 名稱／價格 |
| backend/src/main/java/com/catshop/mapper/AdminMapper.java | SKU version、安全庫存、後台彙總與會員平面欄位 |
| backend/src/test/java/com/catshop/mapper/DtoMapperTest.java | 8 項映射與資料不外洩測試 |
| backend/src/test/java/com/catshop/integration/TestDatabaseGuardTest.java | 3 項資料庫命名／URL 防護測試 |
| backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java | 啟動前空白 DB 檢查、5 筆 migration history、真實 HTTP/JDBC、選配真實瀏覽器 |
| backend/scripts/verify-app-mssql.ps1 | 隨機空白資料庫、僅該資料庫 db_owner 臨時帳號、finally 精確清理、還原程序環境變數 |
| frontend/scripts/e2e.cjs | 真實註冊、購物車重整、下單、庫存、取消、Cookie 更新與登出撤銷 |

## 本機驗證結果

- mvn clean test：34 項測試，0 失敗、0 錯誤、0 略過。包含原有 23 項、新增 8 項 Mapper 及 3 項資料庫防護測試。
- SQL 整合：1 項 Failsafe 測試通過，真正啟動後端／Flyway／Hibernate validate；前述 34 項亦重新執行。Browser 模式包含完整真實 UI 驗證。
- Edge 真實瀏覽器：註冊、HttpOnly/Strict Cookie、商品加入購物車、重新整理保留、MOCK 下單、庫存扣一、取消回補、無效 JWT 後自動更新／重送、登出後舊 JWT 回 401，無頁面程式錯誤。
- npm run build 通過；加入 Playwright 後 npm audit 為 0 項漏洞。
- 原有 npm run test:smoke 回歸通過：搜尋、分類、商品資料、後台新增商品、COD 出貨、訂單分頁、付款選單、手機版面及同時 401 更新測試。此 smoke 使用假 API，與上面的真實 E2E 明確區分。
- 使用 JDK 21 以 Java 17 目標編譯。SQL Server 是 Express 17.0.1000.7，不是規格指定 2022；明確使用 AllowOtherVersion 才允許測試。
- 測試建立的資料庫與帳號已移除，測試資料不可恢復；只有本次隨機命名測試資源受影響，既有資料庫未改動。

## 如何重跑

一般建置（backend、frontend 目錄分別執行）：

```text
mvn clean test
npm ci
npm run build
```

Windows SQL Server 整合測試：需 sqlcmd、Java/Maven，Windows 帳號能建立資料庫與 LOGIN，伺服器已啟用 SQL 驗證和 TCP。腳本不會替你啟用驗證模式、TCP 或重啟服務。

```powershell
# 在 backend，SQL Server 2022 預設需通過版本檢查。
.\scripts\verify-app-mssql.ps1

# 真實瀏覽器：先在 frontend 執行 npm ci，且已安裝 Edge。
$env:E2E_BROWSER_CHANNEL = 'msedge'
.\scripts\verify-app-mssql.ps1 -Browser

# 本機版本不是 2022，只能用於診斷，不能當作 2022 驗收。
.\scripts\verify-app-mssql.ps1 -AllowOtherVersion -Browser
```

若系統禁止 PowerShell 腳本，可用單次程序的 ExecutionPolicy Bypass 啟動；不要修改整部電腦執行原則。不同主機與埠使用 Server、JdbcHost、JdbcPort 參數，需指向同一 SQL 實例。

非 Windows 或 Docker 可自行準備符合名稱規則的空白測試 DB，再由環境提供 CATSHOP_IT_JDBC_URL、CATSHOP_IT_DB_USER、CATSHOP_IT_DB_PASSWORD，於 backend 執行 mvn -Psqlserver-it verify。此直接模式不建立或刪除資料庫，測試後會留下測試資料；下一次需另一個空白 DB。

瀏覽器測試需 Node 符合 frontend/package.json。沒有 Edge 可執行 npx playwright install chromium，並不設定 E2E_BROWSER_CHANNEL；啟用 CATSHOP_IT_BROWSER=true 後整合測試會啟動前端。需保持 5175 未被其他程式使用。

## 仍未完成與下一步

1. 在 SQL Server 2022 重跑同一套整合驗收，不使用 AllowOtherVersion。
2. 確認訪客結帳需求：規格摘要建議訪客結帳，但 API／路由表要求登入，未自行新增另一套身分模型。
3. 模組化 package 邊界仍需重構，目前仍按 Controller/Service/Repository 分層；這批只有 Mapper 與 DTO 解耦。
4. 過期認證工作階段清理、CSP／正式部署硬化、第三方金流與物件儲存仍未完成。

完整專案 ZIP 已排除 node_modules、target、dist、uploads、.git；部署時需要持續保留原有上傳圖片，不能用 ZIP 取代 uploads。
