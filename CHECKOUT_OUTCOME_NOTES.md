# M01 第十三批：下單結果與離頁防護

日期：2026-10-09。延續 CHECKOUT_RECOVERY_NOTES.md。原 index.html 尚未取得，不宣告 M01 全部完成，未修改 Excel 計畫表。

## 完成內容

- 送單期間鎖定收件資訊與付款選擇，避免畫面編輯值與已送出的內容不一致；現有送單防重入保留。
- 站內離頁使用確認提示，明確告知離頁不會取消後端下單；瀏覽器重新整理／關閉使用 beforeunload 標準提醒，是否顯示由瀏覽器決定。
- 取消離頁則繼續等待原請求，不多送 POST；同意離頁後，舊成功／失敗不重新導向、不清空新頁購物車、不污染新頁錯誤。離頁不取消訂單 API。
- 有效成功回應記錄已成立訂單、清空本頁購物車狀態並跳轉詳情；若詳情跳轉被阻擋，保留已成立訂單入口，禁止再次下單。
- 網路中斷、HTTP 5xx 或成功回應缺少有效訂單編號，視為結果尚未確認，不宣稱訂單失敗。顯示我的訂單查詢入口並阻擋直接重送；一般 400 欄位錯誤與 409 衝突沿用前批復原。
- 使用者自行查詢並勾選沒有成立訂單後，才能允許重新同步，再自行送出。此確認不是後端自動核對；仍不明時應停止重送、聯絡商店。同步本身不解除結果不明限制，不自動重送 POST。
- 所有結果不明／已成立狀態都是本頁生命週期內防護，不能取代跨頁、跨裝置或重新整理後的後端冪等機制。

## 依據與範圍

- frontend/src/views/CheckoutView.vue：submitSequence、onBeforeRouteLeave、beforeunload、createdOrder、outcomeUnknown、reviewConfirmed、allowRetryAfterReview、fieldset、submit。
- frontend/scripts/checkout-outcome.cjs：兩種模式的受控晚到回應、成功詳情、離頁取消／確認、503 與網路中斷、阻擋跳轉後禁止重送。
- frontend/package.json：test:checkout-outcome。
- backend/src/main/java/com/catshop/service/OrderService.java：既有 createOrder 使用交易、購物車鎖與清空，但沒有請求識別碼查回既有訂單的機制；本批僅閱讀，未修改後端。

## 驗證

- test:checkout-outcome：14 項通過，前台與整合模式各 7 項。
- test:checkout-recovery：12 項；test:stability：12 項；合計 38 項通過。
- 前台與 Admin 正式建置均通過。
- 全部使用隔離 Edge、模擬 API，未重跑後端／SQL Server；不引用歷史測試當作本批新結果。

## 下一步

設計並實作後端下單請求識別碼與結果查回機制：同會員、同識別碼只能成立一次；相同識別碼但不同內容需拒絕；重送應取得原訂單，不重扣庫存、優惠券或付款。需一起補交易／併發測試與資料庫遷移，再接回前端，才能取代本批手動確認並改善跨頁及斷線恢復。

## 交付界線

只修改工作區並建立本機 ZIP。不提交、不上傳、不重啟展示服務，不修改 Desktop 原始專案或展示持久資料。本批沒有 SQL 建立／刪除操作；ZIP 排除相依套件、建置產物、.local、uploads、.git 與日誌。
