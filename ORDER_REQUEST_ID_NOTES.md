# M01 第十四批：後端下單識別碼與結果查回

日期：2026-10-09。延續 CHECKOUT_OUTCOME_NOTES.md。原 index.html 尚未取得，不宣告 M01 全部完成；未修改 Excel 計畫表。

## 完成內容與界線

- POST /api/orders 新增選填 requestId，格式為完整 UUID；大小寫統一為小寫。未帶 requestId 的舊客戶端仍走原流程，沒有本批識別碼防重複保障。
- 同會員、同識別碼、完全相同的下單欄位重送，回傳原訂單的目前詳情，包括取消後的 CANCELLED 狀態；不建立第二筆、不再扣庫存、不再兌換優惠券，也不重新決定付款狀態。
- 識別碼重送不消耗後來新增的購物車內容。識別碼代表原下單動作，不代表購物車目前版本；新一次購買必須產生新識別碼。
- 同會員、同識別碼若收件人、電話、地址、備註、付款方式或優惠碼有任一差異，回應 409／REQUEST_ID_CONFLICT。比較原始欄位值，空字串與 null、大小寫與空白差異不視為相同內容。
- 使用長度分隔的 UTF-8 欄位 SHA-256 指紋，避免單純字串串接歧義。指紋不包含識別碼、不另保存完整請求 JSON，亦不公開指紋於 DTO。
- 查原訂單成功可直接回傳，不要求目前購物車有商品。新訂單取得既有購物車寫入鎖後再次查重，避免兩個併發請求皆以第一次查詢未找到而重複建立。
- 識別碼與訂單存於同一交易。下單回滾不留下占用識別碼，可以使用同一識別碼重試。
- 新增 GET /api/orders/requests/{requestId}，使用登入會員範圍查詢。其他會員即使知道識別碼也查不到；UUID 格式不符回 400，未登入回 401，未找到回 404。
- 404 只表示當次查詢尚未看到已提交的訂單，不證明先前請求已停止；後續應以相同識別碼、相同內容重試，不換新碼。
- V12 新增 orders.request_id、request_hash，含會員／識別碼的 SQL Server filtered unique index 及欄位成對存在檢查；舊資料保持 NULL。不改舊 Flyway 檔案。
- 前端 API 層新增 orderApi.byRequestId；本批沒有替換結帳 UI 的手動確認，也尚未自動產生、保存或傳送 requestId。現有展示頁面不因此自動獲得完整防重複下單能力。

## API 範例

POST /api/orders，原有登入 JWT 保持不變：

```json
{
  "receiverName": "收件人",
  "receiverPhone": "0912345678",
  "shippingAddress": "台北市測試路一號",
  "remark": null,
  "paymentMethod": "COD",
  "couponCode": null,
  "requestId": "abcdefab-1234-1234-1234-abcdefabcdef"
}
```

建立及相同內容重送均保留原 POST 201 契約，回應既有 OrderResponse。查回使用 GET /api/orders/requests/abcdefab-1234-1234-1234-abcdefabcdef，成功回 200／OrderResponse。

## 具體檔案

- backend/src/main/java/com/catshop/dto/order/CreateOrderRequest.java：選填 UUID 與舊 5／6 參數 Java 建構子相容。
- backend/src/main/java/com/catshop/common/OrderRequestIdentity.java：格式驗證、正規化及請求指紋。
- backend/src/main/java/com/catshop/entity/Order.java、repository/OrderRepository.java：不可更新的識別碼欄位、會員限定查詢。
- backend/src/main/java/com/catshop/service/OrderService.java：鎖前／鎖後查重、不同內容拒絕、原訂單詳情與查回。
- backend/src/main/java/com/catshop/controller/OrderController.java：受登入保護的識別碼查詢入口。
- backend/src/main/resources/db/migration/V12__order_request_identity.sql：新增欄位與索引。
- backend/src/test/java/com/catshop/CheckoutIntegrationTest.java：7 組新測試。
- backend/src/test/java/com/catshop/common/OrderRequestIdentityTest.java：3 組新測試。
- backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java：SQL Server 真實 HTTP／4 路併發、回滾、優惠、會員隔離、唯一索引拒絕重複與取消後重送。
- frontend/src/api/index.js：新增查回呼叫，尚未整合 UI。

## 驗證

- 後端一般測試 152 項通過（0 失敗／錯誤／略過）；新增 7 項結帳整合測試與 3 項識別碼工具測試。
- SQL Server 整合測試 22 項通過（0 失敗／錯誤／略過）。V1 至 V12 遷移與 Hibernate validate 成功，識別碼測試包含真實 HTTP 4 路併發、重送原訂單、優惠券僅兌換一次、回滾後同碼重試、會員隔離及唯一索引實際拒絕重複。
- 真實 Vue → 後端 → SQL Server 瀏覽器流程通過，覆蓋舊格式結帳、訂單取消／回補、會員與登入，以及既有分類／搜尋返回與分頁回歸；不是新識別碼 UI 的端到端驗證。
- 前台與 Admin 正式建置均通過。
- 使用 runner 的 AllowOtherVersion 對本機 SQL Server 17.x 驗證；不將此當作指定 SQL Server 2022 版本驗收。
- 最後一輪隔離資料庫及專用登入均已清理，沒有更動展示資料庫。未將 H2 測試當作 SQL Server 唯一索引驗證。

隔離 SQL 測試實際找出並修正 V12 的兩個問題：新欄位後的 filtered index／CHECK 需用 GO 分批；欄位型別需用 NVARCHAR，對齊專案的 Hibernate Unicode 設定。舊遷移沒有修改；失敗回合的隔離資料庫與登入均由 runner 清除。第一次受限模式測試因 Java 程序權限未完成，未將其算入通過結果。

## 下一步

將 requestId 接上結帳：第一次送出產生識別碼，結果不明時保留原內容與識別碼，提供查回與相同識別碼重試；禁止在未確認原結果時改內容或換碼。再補重新整理、離頁返回、登入會員切換及真正斷線後恢復測試。需先設計暫存資料的會員隔離與個資清除，不能將個資長期留存。

## 交付界線

只修改工作區並建立本機 ZIP。不提交、不上傳、不重啟展示服務，不修改 Desktop 原始專案或展示持久資料。展示後端尚未套用 V12；需要後續另行部署，不能直接認定展示網址已提供新 API。SQL 驗證只操作 runner 產生的隔離 catshop_it_ 資料庫及專用登入，結束後清理。ZIP 排除相依套件、建置產物、.local、uploads、.git 與日誌。
