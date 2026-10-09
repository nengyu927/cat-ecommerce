# M01 第十六批：後端交易內的購物車快照驗證

日期：2026-10-09。延續 CHECKOUT_IDENTITY_NOTES.md。原 index.html 尚未取得，未宣告 M01 全部完成，未修改 Excel 計畫表。

## 完成項目

- 新版結帳請求增加 cartSnapshot：按 SKU ID 遞增排序的商品／數量配對，例如 [[11,2],[20,1]] 的 JSON 字串。前端以使用者確認時的購物車建立快照，連同原收件內容與 UUID 保存；原請求重送不更換快照。
- 後端取得會員購物車的寫入鎖後，讀取實際項目並比對快照，位置在建立訂單、兌換優惠券及扣庫存之前。不一致回 HTTP 409／CART_SNAPSHOT_CONFLICT，保留購物車，不成立訂單、不扣庫存。
- 正常購物車新增、更新、刪除、清空、重新報價與結帳已共用該鎖。若下單等待修改交易，取得鎖後必須比對已提交內容；驗證完成後直到結帳提交，其他正常購物車修改不能穿插。
- 已成立原請求的查找與重播仍優先於快照驗證，空購物車或後來加入的商品不會妨礙原訂單重播，也不會被重播清掉。同 UUID 變更快照回 REQUEST_ID_CONFLICT。
- 快照加入請求指紋；未傳快照時保持前批六欄位指紋算法，不破壞已存在舊訂單的重播。原 5／6／7 參數 Java 建構子與舊 HTTP 格式保留。
- GET /api/orders/request-identity 的契約版本改為 2。新版前端要求版本 2；版本 1 即使有 UUID 防重複，也不提供本批快照保障，不允許新版前端送單。
- 衝突時清除明確遭拒的暫存、保留表單、要求重新同步；同步完成仍需使用者自行確認送出，不自動重試。
- 舊版未知結果暫存缺少快照時，只能使用識別碼查回。不能擅自加入快照，因為那會改變原請求指紋；仍查不到時需商店協助確認，不換新碼重送。
- HTTP 快照欄位最大 24000 字元；前端建立暫存前也檢查上限。暫存驗證會確認快照與保存的商品摘要相同。恢復表單時排除快照，不將協定欄位混入收件表單。

## 重要界線

- 本批採內容快照，不是遞增版本號；商品改動後又恢復相同 SKU／數量會被視為相同內容。沒有新增 Flyway 遷移或 Cart Entity 欄位。
- 快照不包含價格、運費、庫存及優惠金額；這些仍依後端結帳當下規則驗證／計算。本批不宣稱價格變動已取得使用者二次確認。
- 為相容舊客戶端，未傳 cartSnapshot 的請求仍可依舊流程下單；新版前端才要求完整保障。直接資料庫修改不屬於受本服務鎖保護的正常購物車操作。
- 後端能力檢查不是跨部署交易鎖；正式部署必須保持整批前後端／後端節點版本一致。
- 本批不更動現有 sessionStorage 個資期限、會員隔離及 UUID 保存／查回界線。

## 程式碼依據

- backend/src/main/java/com/catshop/common/CheckoutCartSnapshot.java：後端快照排序與產生。
- backend/src/main/java/com/catshop/dto/order/CreateOrderRequest.java：快照欄位限制與舊建構子相容。
- backend/src/main/java/com/catshop/service/OrderService.java：同請求重播、購物車鎖、交易內快照比對與 409。
- backend/src/main/java/com/catshop/common/OrderRequestIdentity.java：快照指紋及舊算法相容。
- backend/src/main/java/com/catshop/controller/OrderController.java：版本 2 能力端點。
- frontend/src/utils/checkoutAttempt.js：快照產生、保存、讀取驗證與長度限制。
- frontend/src/views/CheckoutView.vue：版本檢查、舊暫存僅查回、衝突同步與恢復表單。
- backend/src/test/java/com/catshop/CheckoutIntegrationTest.java：無副作用拒絕、重播、修改交易後驗證、結帳鎖阻擋後續變更、HTTP 上限與相容。
- backend/src/test/java/com/catshop/common/OrderRequestIdentityTest.java：快照加入指紋、不改變未傳快照算法。
- backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java：實際 SQL Server HTTP 衝突、並發同碼重播、快照指紋衝突、等待購物車修改提交後拒絕。
- frontend/scripts/checkout-identity.cjs：兩模式快照、版本 1 拒絕、舊暫存僅查回、衝突後同步與新快照。
- frontend/scripts/e2e.cjs：真實伺服器購物車變更後拒絕、無副作用、同步，以及成立訂單後丟失回應／重送。

## 驗證

- 前端 74 項通過：checkout-identity 28、checkout-recovery 12、checkout-outcome 14、stability 12、cart-state 8。前台與 Admin 建置皆通過。
- 後端一般測試 157 項、SQL Server 整合測試 22 項通過，皆 0 失敗／錯誤／略過；BUILD SUCCESS。
- SQL Server 實際驗證：等待購物車修改提交後拒絕舊快照，沒有訂單／庫存副作用；同 UUID 並發重送、已成立訂單重播與快照指紋衝突皆通過。
- 真實 Vue → Spring Boot → SQL Server：結帳頁顯示後從另一 API 修改購物車，原頁下單被拒絕；無訂單／扣庫存／清空購物車，需手動同步才能再下單。其後成立訂單但丟失瀏覽器回應、重新整理與同 UUID／同快照重送仍只產生一筆訂單、一次庫存扣減。既有取消、登入、會員、分類／搜尋返回及前後台分頁回歸通過。
- 本次隔離資料庫 catshop_it_8c72a3691fdd45579c9619a33a59f6b9 與專用登入已清理，展示持久資料未更動。
- 首次受限環境執行卡在 Java 測試代理附加權限，已核對並結束該次測試程序，改以必要權限重跑，沒有把該次卡住視為測試通過。

## 下一步

補上結帳金額的伺服器報價與價格變動確認：目前快照只保護商品／數量，應讓使用者看到並確認的商品金額、運費與折扣，和真正成立訂單的金額一致；報價失效時保留表單、重新報價並要求再次確認。先盤點運費與優惠規則，再處理價格變動與併發測試。

## 交付界線

只更新工作區與本機 ZIP，不提交、不上傳、不重啟展示服務，不修改 Desktop 原始專案或展示持久資料。測試建立的專用 SQL 資料庫／登入由 runner 清理。SQL Server 17.x 搭配 AllowOtherVersion，不是指定 SQL Server 2022 版本驗收。展示後端未更新時，新前端停止送單並提示更新。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。
