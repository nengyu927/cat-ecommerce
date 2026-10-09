# 前台主題確認視窗

## 範圍
- 取消訂單與刪除收件地址由瀏覽器 confirm 改成森林綠／奶油白／陶色主題視窗。
- 顯示訂單編號或地址摘要；說明不可復原，預設聚焦「保留不變」，危險操作需按明確確認鈕。
- Esc 等同取消；點背景不執行動作；原生 dialog 隔離背景、支援鍵盤焦點與捲動鎖定／還原。
- 減少動態效果設定停用入場動畫。沿用前批短暫點擊回饋。

## 防護
- useConfirmation 同時只允許一個待確認請求；換頁、會員 epoch 改變、卸載時以取消結束，不將操作帶到另一個會員。
- 確認後再次同步登入狀態，比對原頁載入序號、會員 epoch、原目標與可操作狀態，才送出 API。
- 重複點擊不重複提交。失敗保留原資料與可重試錯誤提示。
- 訂單取消回應（成功、錯誤、finally）全部檢查載入序號／會員／卸載狀態，避免舊訂單回應污染新頁面。
- 原未儲存離頁、地址重新載入、送單離頁、beforeunload 警告保持不變。
- 不修改後端 API、取消規則、地址預設邏輯或展示資料庫；不需重啟後端。

## 主要檔案
- frontend/src/components/ConfirmationDialog.vue
- frontend/src/composables/useConfirmation.js
- frontend/src/views/AddressListView.vue
- frontend/src/views/OrderDetailView.vue
- frontend/scripts/confirmation-dialog.cjs
- 更新 address-routes、stability 與真實 SQL e2e 對確認視窗的操作。

## 驗證
新增桌機1440／手機390的安全焦點、Esc／取消、背景不誤觸、跨頁及會員切換取消、重複提交、舊回應與成功取消測試。
真實 API 寫入只在 verify-app-mssql 隔離資料庫執行；展示網站驗證只讀取。
測試 screenshot 以測試地址製作，不含真實個資。

2026-10-09 最終結果：前後台 build、新確認視窗桌機／手機2組、地址路由10組、穩定性12組、下單結果14組通過且無頁面未處理錯誤；SQL整合22項與真實Edge購物流程通過。隔離測試資料庫／登入已由驗證程式清理；展示网站只讀驗證通過，不重啟、不刪除資料。

## 下一步
將會員資料與地址「尚未儲存」的換頁／重新載入確認主題化；送單離頁與瀏覽器關閉提醒需保留完整防護，不與一般確認混用。
