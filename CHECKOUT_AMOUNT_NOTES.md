# M01 第十七批：結帳報價、金額變動確認與前後台預覽

日期：2026-10-09。延續 CHECKOUT_SNAPSHOT_NOTES.md。原 index.html 尚未取得，未宣告 M01 全部完成，未修改 Excel 計畫表。

## 本批完成

- 新增登入限定 GET /api/orders/checkout-quote，取得無優惠券的後端報價；既有 POST /api/orders/quote 仍用於套用優惠券。兩者回商品金額、折扣、運費、總額，以及商品／數量與逐 SKU 單價快照。
- 結帳載入或重新同步時取得後端報價，不再自行估算運費與免運門檻。若報價失敗、格式無法辨識，或報價商品／單價與購物車顯示不同，停止送單並提供重新載入；保留已填寫的收件資料。
- 套用優惠券也驗證報價快照；清除、編輯或重新同步優惠碼時解除舊優惠報價，無優惠的金額仍來自後端。優惠名額不保留，下單當下仍重新驗證。
- 新版 CreateOrderRequest 加入 expectedAmounts：goodsTotal、discountAmount、shippingFee、totalAmount、priceSnapshot。四個金額都須非負、最多兩位小數，priceSnapshot 必填且有長度上限。
- 後端在購物車鎖／優惠券鎖保護的交易內，重新取得單價、計算運費與折扣，逐項比對四個確認金額與每 SKU 單價。任何差異回 HTTP 409／CHECKOUT_PRICE_CONFLICT，整筆交易回滾，不建立訂單、不扣庫存、不消耗優惠名額。
- 金額變動後前端保留收件表單、清除明確遭拒的請求暫存，要求手動重新同步與再次確認；不自動按新的金額下單。商品改價後又恢復相同金額與單價，仍視為內容相同。
- 後端只信任自己的計算，客戶端金額不是折扣或定價來源。SKU 若在確認後被後台並發更改，既有 optimistic version 保護會讓衝突交易回滾。
- 確認金額加入 UUID 請求指紋，金額用去尾零的十進位表示，100／100.00 視為相同數值。不傳 expectedAmounts 時保留前批指紋算法與 5／6／7／8 參數建構子相容。
- 已成立訂單的同 UUID／同內容重送仍先查回原訂單，不以後來價格或運費重新建立訂單，也不清掉後來加入的購物車。
- 能力端點版本升至 3。新版前端要求版本 3；舊版待確認暫存缺少確認金額時，只能查回原結果，不擅自補金額或更換 UUID。

## 重要界線

- 報價不鎖定價格／庫存，不保留優惠名額，也沒有報價有效期或背景預留；真正下單仍以交易內比對為準。不是付款金流串接，MOCK 仍為模擬付款。
- 為舊客戶端相容，未傳 expectedAmounts 的直接 API 請求仍依舊流程；新版前端才要求完整金額確認。未提供 cartSnapshot 卻有 expectedAmounts 時會拒絕。
- 快照內容不是遞增版本，沒有新增 Flyway 遷移。本次展示啟動將先前未套用的 V12 正常套用，不重建資料庫。
- 兩小時暫存期限、會員隔離、舊回應防護等前批界線維持；正式部署仍需保持所有後端節點契約一致。

## 程式碼依據

- backend/src/main/java/com/catshop/dto/order/CheckoutAmounts.java：完整確認金額及驗證。
- backend/src/main/java/com/catshop/dto/order/CreateOrderRequest.java：巢狀驗證與舊建構子相容。
- backend/src/main/java/com/catshop/dto/order/CouponQuoteResponse.java：報價商品／單價快照。
- backend/src/main/java/com/catshop/common/CheckoutCartSnapshot.java：SKU／數量與單價的排序／標準化。
- backend/src/main/java/com/catshop/common/OrderRequestIdentity.java：確認金額指紋及舊算法相容。
- backend/src/main/java/com/catshop/service/CouponService.java：無優惠／有優惠的實際後端報價。
- backend/src/main/java/com/catshop/service/OrderService.java：金額重新計算、比對、衝突回滾及原訂單重播。
- backend/src/main/java/com/catshop/controller/OrderController.java：無優惠報價端點及版本 3。
- frontend/src/utils/checkoutAttempt.js：報價驗證、金額暫存與原請求恢復。
- frontend/src/views/CheckoutView.vue：基礎／優惠報價、摘要、報價失敗、金額衝突同步、舊暫存僅查回。
- backend/src/test/java/com/catshop/CheckoutIntegrationTest.java：自訂後端運費、商品／優惠／運費／總額／單價竄改、無副作用、名額未消耗、HTTP 巢狀驗證及重播。
- backend/src/test/java/com/catshop/common/OrderRequestIdentityTest.java：數值等價指紋與金額變更。
- backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java：實際改價衝突、優惠名額未耗、同碼並發重播與原快照防護。
- frontend/scripts/checkout-identity.cjs、checkout-recovery.cjs、checkout-outcome.cjs、stability.cjs：新版報價契約與回歸。
- frontend/scripts/e2e.cjs：真實 Admin API 改價、舊報價拒絕、重新報價、成立後丟失回應及相同金額重送。
- frontend/scripts/preview-check.cjs：本機前後台登入、版本 3、畫面與單一登入階段登出；不修改會員資料、商品、購物車或訂單。

## 驗證

- 前端 80 項通過：checkout-identity 34、checkout-recovery 12、checkout-outcome 14、stability 12、cart-state 8。前台與 Admin 建置通過。
- 後端一般測試 162 項、SQL Server 整合測試 22 項通過，皆 0 失敗／錯誤／略過；BUILD SUCCESS。
- 真實 Vue → Spring Boot → SQL Server：從 Admin API 更改 SKU 價格後，舊結帳報價回 409；表單保留、送出停用、不扣庫存、需重新同步。重新報價後成立訂單，但故意丟失回應、重新整理與同 UUID／同金額重送，仍只一筆訂單、一次扣庫存，訂單金額等於使用者確認的金額。
- 原商品／數量快照、分類／搜尋返回、分頁、會員、取消、庫存及登入回歸通過。
- 本次隔離資料庫 catshop_it_1f060f13c584441b993676790c1a01d6 與專用登入已清理；SQL Server 17.x 搭配 AllowOtherVersion，不是 SQL Server 2022 指定版本驗收。
- 真實預覽 test:split 通過：前後台獨立路由、同時角色、刷新 Cookie、刷新／登出隔離與會員不可進 Admin。畫面檢查通過，沒有未處理頁面錯誤。

## 已啟動的預覽

- 前台：http://127.0.0.1:5173/
- Admin：http://127.0.0.1:5174/products，未登入會前往 /login；可按「一鍵登入管理員（展示用）」。
- 前台會員可從 /login 按「一鍵登入會員（展示用）」。
- 後端：127.0.0.1:8080，沿用既有 DPAPI 加密狀態與持久資料庫 catshop_persist_373a1a7a8b3045de9817dc77d7afdd46。
- 啟動前後核對：會員 2、訂單 0、購物車項目 0 不變，Flyway installed_rank 從 11 升至 12；未重建既有資料。
- 三個服務在背景啟動；日誌位於 .local/preview-amounts-*.log，不打包本機狀態／資料／密鑰。
- 本機畫面：outputs/storefront-preview.png、outputs/admin-preview.png（位於此工作區 outputs，不在 ZIP 內）。
- 已請 Codex 開啟兩個預覽頁；工具回 queued，實際顯示由應用程式處理。網址已由真實瀏覽器確認可用。
- 公開前台首頁登出後留在首頁，會保留不含 token／會員的登入狀態 epoch；Admin 登出返回登入頁。畫面檢查依這兩個既有行為驗證，不要求刪掉整個 session 紀錄。

## 下一步

細化優惠券失效、名額用完與門檻未達的結帳提示／復原：以明確錯誤碼呈現原因，保留輸入優惠碼，讓使用者選擇重新套用或確認移除後的金額，並補上跨分頁及並發名額用完的回歸測試。

## 交付界線

僅更新工作區、背景本機預覽與本機 ZIP，不提交、不上傳、不修改 Desktop 原始專案。預覽使用原持久資料，不清除資料；隔離測試資料庫已由 runner 清理。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。原始規格書及 Excel 計畫表仍待完整驗收。
