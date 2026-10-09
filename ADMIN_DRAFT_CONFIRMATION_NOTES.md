# Admin 商品／分類／優惠券草稿確認（2026-10-10）

## 本批完成

商品主檔（含新增商品第一個 SKU／圖片）、分類與優惠券的新增／編輯頁，未儲存離頁與重新載入改用共用主題視窗。
預設焦點在「繼續編輯」；Esc／取消保留草稿，明確放棄才離頁或取得最新資料。
載入成功時也提供「重新載入」，不必先遇到錯誤才能更新資料。
確認期間停用欄位／儲存／新增商品圖片上傳；函式再檢查 pending，不能藉額外 submit 誤送。
同元件切換不同資料編號也經確認；確認返回後比對載入序號、會員 epoch、登入與 Admin 角色。
登出、換會員、離頁、卸載取消舊確認，恢復頁面捲動。
保留 beforeunload 原生分頁關閉提醒與儲存中離頁阻擋。

## 保留原規則

不改 API 路徑或 DTO；不移除商品第一個 SKU 要求、分類子孫排除／後端階層檢查、優惠券版本衝突／不可更名與 UTC 原始精度。
不重建資料、不重啟後端；本批前端熱更新，上一批後端錯誤碼仍待使用者核准重啟。
SKU／圖片獨立管理頁與出貨表單未在本批改動，仍待後續統一。

## 程式依據

- frontend/src/composables/useAdminDraftGuard.js：共用 Router guard、reload 確認、pending 鎖定與角色／序號／epoch 防護。
- frontend/src/views/AdminProductView.vue、AdminCategoryView.vue、AdminCouponEditView.vue：接入 guard、ConfirmationDialog、blocked 及載入成功後的重新載入入口。
- frontend/scripts/admin-draft-confirmation.cjs：三表單 × 新增／編輯 × 桌機／手機，共 12 組；安全焦點、保留／放棄、額外 submit 不寫入、同元件換資料、原生離頁與登出。
- product-routes／category-routes／coupons：原生確認操作改為明確按主題視窗按鈕；慢讀取測試先等待請求開始，再用 Router 切換，避免固定等待的初始化競態。
- coupons 測試補新版報價快照／request identity／最後確認，驗證編輯優惠碼後需明確移除或重新套用，不悄悄原價下單。

## 預覽

http://127.0.0.1:5174/products/1/edit
http://127.0.0.1:5174/categories/1/edit
http://127.0.0.1:5174/coupons/new
登入 Admin 後修改欄位，再點返回或重新載入；選繼續編輯即可安全檢查，不必儲存。
桌機／手機截圖位於工作區 outputs/admin-draft-{product,category,coupon}-{1440,390}.png。

## 本批驗證

前後台 build 通過；新增 Admin 草稿確認 12 組、既有確認視窗 2 組、商品頁 10 組、分類頁 11 組、優惠券 14 組通過。
展示前後台只讀檢查通過，無業務資料修改；未提交或上傳。本批後端無修改，未將上批後端測試結果列為本批重新執行的結果。

## 下一步

將商品 SKU／圖片獨立管理頁的未儲存確認統一，保留圖片上傳、主圖排序與 SKU 版本／庫存異動規則；再處理出貨表單。
