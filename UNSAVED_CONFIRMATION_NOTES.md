# 會員／地址草稿的主題確認視窗

## 本批完成（2026-10-09）

- 個人資料、密碼與新增／編輯地址的未儲存離頁、重新載入確認，改用共用主題 dialog。
- 預設焦點在「繼續編輯」；按 Esc 或繼續編輯保留欄位，明確放棄才離頁或重新載入。
- 確認期間停用表單及儲存；儲存函式也檢查 pending，不能藉由額外 submit 送出。
- 同元件切換個人資料／密碼，以及不同地址編號，均經過 Router 更新保護。
- 等待確認後再次檢查頁面序號、會員 epoch 與登入狀態；登出或換會員取消舊確認，不沿用舊回應。
- 視窗不顯示密碼，不新增密碼本地保存、自動儲存或草稿持久化。

## 保留原防護

瀏覽器重新整理、關閉分頁仍使用 beforeunload 的原生提醒，這類提醒不能用自訂視窗取代。
本批未改結帳送單離頁／結果不明的防護；刪除地址與取消訂單的預設「保留不變」文字仍保留。
未改後端 API、資料庫或 Admin 表單，不需重啟／清除展示資料。

## 主要檔案

- frontend/src/components/ConfirmationDialog.vue：可指定 cancelLabel，原有操作維持預設文字。
- frontend/src/views/AccountSettingsView.vue：資料與密碼 guard、retry、blocked。
- frontend/src/views/AddressEditView.vue：新增／編輯地址 guard、retry、blocked。
- frontend/scripts/unsaved-confirmation.cjs：桌機／手機草稿確認、同元件切換、鎖住 submit、密碼隱私、登出與原生離頁提醒。
- frontend/scripts/account-routes.cjs、address-routes.cjs：原生 confirm 測試轉為主題 dialog 操作。

## 本批驗證

前後台建置通過；草稿確認 6 組、會員頁 10 組、地址頁 10 組、刪除／取消確認 2 組、下單結果 14 組、穩定性 12 組、快速加入／頁首移入 2 組通過。
草稿確認測試使用模擬 API，不寫入展示會員、地址或購物車。
展示網站只讀驗證通過：10 張商品圖片映射、前後台 20 個圖片回應、會員／購物車選單、右側購物車及 Admin 圖片頁；展示登入／登出不修改業務資料。
桌機與手機確認截圖位於工作區 outputs/unsaved-{profile,password,address}-{1440,390}.png。

## 預覽

會員資料：http://127.0.0.1:5173/account/profile
地址列表：http://127.0.0.1:5173/account/addresses
Admin：http://127.0.0.1:5174/products
登入後修改欄位，再點返回或重新載入即可看到確認視窗；不需要先儲存。

## 下一步

接回結帳復原計畫：細化優惠券失效、名額用完與門檻未達的原因提示，保留輸入優惠碼，讓使用者明確選擇重新套用或移除後確認新金額，補跨分頁／並發名額用完測試。
