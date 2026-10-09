# 下單前最後確認（2026-10-10）

## 完成內容

「確認下單」先開啟主題確認視窗，不立即送單或建立下單暫存。
摘要列出商品／規格／件數／小計、收件人／電話／地址／備註、付款方式、優惠碼、商品金額／折抵／運費／合計。
付款標示區分貨到付款與模擬付款，不宣稱串接正式金流。
預設焦點在「返回修改」，Esc／返回修改保持收件草稿；按「確認成立訂單」後才建立識別碼與送出。
摘要沿用後端報價，確認不保留優惠名額或庫存；伺服器仍會重新驗證。

## 防護

- 視窗期間鎖住收件欄位／優惠碼；函式也拒絕重複觸發與重新同步。
- 監測完整表單、購物車、報價、優惠碼、同步狀態與會員 epoch；內容變更即取消舊視窗。
- 確認返回後，再比對完整 fingerprint、登入及結帳阻擋狀態，傳送當時顯示的 payload。
- 取消、換頁、登出不建立新的 requestId，不儲存新的收件資料。
- 原請求重試仍傳相同 requestId／原內容，不讓新確認視窗改寫結果不明的請求。
- 送單中的瀏覽器離頁提醒、後端金額／購物車快照、優惠拒絕與結果查回沿用既有防護。
- 摘要以純文字渲染，不以 HTML 注入商品名稱或地址；長內容可在摘要區捲動。

## 程式依據

- frontend/src/views/CheckoutView.vue：reviewFingerprint、confirmation.ask、確認後檢查與固定 payload。
- frontend/src/components/ConfirmationDialog.vue：支援主題綠色確認操作，刪除／取消仍維持原危險操作色；長摘要捲動。
- frontend/scripts/checkout-review.cjs：桌機／手機取消、正確摘要、優惠、購物車變更與登出，共 8 組。
- frontend/scripts/confirm-checkout.cjs：既有結帳與真實端到端測試明確操作最後確認，不自動略過 UI。
- 更新 coupon-recovery、checkout-recovery、checkout-outcome、checkout-identity 與 e2e 的操作步驟。

## 展示與範圍

本批未改後端或資料表；前端已熱更新。可在 http://127.0.0.1:5173/checkout 填寫資料後點「確認下單」，檢查摘要後選「返回修改」即可安全預覽。
上一批優惠錯誤碼的後端仍待使用者核准重啟；本批未將「繼續」視為清除資料或重啟授權。
未提交或上傳。

## 本批驗證

前後台 build 通過；checkout-review 8 組、confirmation-dialog 2 組、coupon-recovery 8 組、checkout-outcome 14 組、checkout-identity 34 組、checkout-recovery 12 組通過。
後端 163 項、隔離 SQL Server 22 項及真實 Edge 下單／金額衝突／斷線查回與重送流程通過；隔離測試資料庫與登入已清理。
展示前台 1440／390 寬僅開啟摘要後取消，無訂單 POST 或新收件暫存；展示資料未清除。
截圖位於工作區 outputs/checkout-review-{1440,390}.png 及 checkout-review-live-{1440,390}.png。

## 下一步

將 Admin 商品／分類／優惠券等表單的未儲存離頁與重新載入確認統一為主題視窗，保留各表單既有版本衝突與送出鎖定規則。
