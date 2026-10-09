# 結帳優惠券原因提示與復原（2026-10-10）

## 本批完成

- 後端將優惠券拒絕分成 8 個明確 error.code，保留 HTTP 400 與原有 API 路徑。
- 前端依錯誤碼呈現原因，不解析錯誤文字猜測類型。
- 輸入／修改優惠碼、重新同步結帳或優惠報價失敗後，需重新套用成功，或明確移除並取得最新原價報價，才能下單。
- 清空輸入不會悄悄解除原價確認；移除報價失敗仍鎖住下單。
- 已套用優惠在下單時失效：清除已被明確拒絕的請求暫存，不自動重送，保留優惠碼及收件草稿，顯示原因與下一個選擇。
- 套用成功提示名額不保留；原價重新取得後仍需使用者自行按確認下單。
- 失敗不是結果不明：5xx／斷線與識別碼衝突仍沿用原請求查回／重試防護。
- 會員 epoch、頁面卸載與重新同步會使舊優惠／原價回應失效，不影響其他頁或會員。

## 原因碼

| error.code | 原因 |
| --- | --- |
| COUPON_INVALID_FORMAT | 格式錯誤 |
| COUPON_NOT_FOUND | 不存在 |
| COUPON_INACTIVE | 已停用 |
| COUPON_NOT_STARTED | 未開始 |
| COUPON_EXPIRED | 已到期 |
| COUPON_MINIMUM_NOT_MET | 未達商品金額門檻 |
| COUPON_EXHAUSTED | 全店名額用完 |
| COUPON_ALREADY_USED | 本會員已使用 |

## 程式依據

- backend/src/main/java/com/catshop/service/CouponService.java：quote／create 共用 redeem 原因碼；保留交易與悲觀鎖，不改名額或取消返還規則。
- frontend/src/utils/couponFailure.js：明確原因對應與未知錯誤 fallback。
- frontend/src/views/CheckoutView.vue：couponDecision 防誤送、removeCoupon 最新報價與 sendAttempt 拒絕復原。
- frontend/scripts/coupon-recovery.cjs：8 個拒絕原因、清空文字仍鎖住、移除報價失敗、成功報價後再次拒絕、不自動送單與草稿保留，含手機檢查。
- frontend/scripts/checkout-recovery.cjs：重新同步後優惠碼需重新確認的測試。
- backend/src/test/java/com/catshop/CheckoutIntegrationTest.java：8 原因碼與拒絕後庫存／購物車／訂單防護。
- backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java：兩位會員先取得報價再同時搶最後名額，失敗回傳 COUPON_EXHAUSTED，購物車保留；取消後名額返還與歷史折扣快照不變。

## 資料與部署

無新增 Entity、SQL migration 或資料重建。前端可熱更新；後端新錯誤碼需重啟後才生效。
舊後端仍可顯示原有錯誤訊息；但下單時的細分類復原，需新後端的錯誤碼才能啟用。
所有業務寫入測試只使用模擬資料或隔離測試資料庫，不修改展示會員、商品、購物車與訂單。

## 本批驗證結果

前後台 build 通過；coupon-recovery 8 組（含手機）、checkout-recovery 12 組、checkout-outcome 14 組、checkout-identity 34 組通過。
後端 163 項與隔離 SQL Server 22 項通過；真實 Edge 購物、報價衝突、識別碼重播、分頁與跨分頁登出流程通過。
SQL 最後名額並發驗證包含失敗者 COUPON_EXHAUSTED 錯誤碼，測試資料庫與登入已清理；展示資料未清除。
2026-10-10 本批交付時：前端熱更新，後端仍待使用者核准重啟套用；未提交或上傳。

## 下一步

補上結帳送單前「最後一次金額與優惠確認」的清楚摘要，並保留請求識別碼與結果不明時的安全查回；之後統一 Admin 表單未儲存的確認風格。
