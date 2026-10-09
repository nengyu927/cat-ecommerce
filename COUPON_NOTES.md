# 固定金額優惠券

2026-10-08。第一版採每單一碼、固定金額折抵商品金額，不折運費、不與其他券疊加。

## 商業規則

- Admin 可建立、編輯、啟用／停用優惠券，設定固定折抵、最低商品金額、開始／結束時間、總使用上限。優惠碼 3–32 字，ASCII 英數、底線及連字號，統一大寫；建立後不可更名或刪除。
- 折抵最高為本單商品金額，訂單不會因折扣變成負數。免運門檻仍使用折扣前商品金額：滿 1000 免運，否則 80。
- 有效期間使用 UTC：[開始時間, 結束時間)，結束時間不包含。Admin 以瀏覽器本地時間輸入／顯示，傳送 UTC；API 的 startsAt、endsAt 為無 Z 的 UTC LocalDateTime。
- 所有非 CANCELLED 訂單均占用名額，包括待處理、已付款、已出貨、已完成。每位會員同券最多一張未取消訂單。
- 會員取消或 Admin 取消未出貨訂單後，可重新使用名額；已出貨不可取消。取消仍保留原折扣快照；不代表已串接付款退款或售後退货流程。
- 預覽不保留名額。下單時以最新 SKU 價格、券規則及可用名額重新驗證，失效券不會悄悄改成原價下單，而是返回錯誤。
- 目前以 coupon_id + 非取消訂單計算核銷量，不另維護容易失步的 used_count。折扣快照保存 coupon_code 與 discount_amount，不讀當前券金額覆蓋舊訂單。

## API 與主要檔案

- GET /api/admin/coupons?page=0&size=20（size 1–100）
- POST /api/admin/coupons（201）
- PUT /api/admin/coupons/{id}：必須帶讀取時的 version，舊版本 409。三個管理 API 僅 ADMIN 可用，回傳 DTO，不直接暴露 JPA Entity。
- POST /api/orders/quote，body：{"code":"SAVE100"}。商品總額、折扣、運費、合計皆由後端依自己的購物車算出；不相信用戶提交的金額。
- POST /api/orders 新增選填 couponCode；原本未使用券的呼叫維持相容。
- backend/src/main/resources/db/migration/V11__coupons.sql：coupon、訂單折扣欄位與核銷查詢索引；舊訂單折扣預設 0。
- backend/src/main/java/com/catshop/service/CouponService.java：日期、門檻、名額、每人限制、折扣計算；核銷用 MANDATORY 交易與券列鎖，鎖維持至整筆下單提交。
- backend/src/main/java/com/catshop/service/OrderService.java：訂單建立與券核銷、庫存扣除、購物車清空為同一交易。
- backend/src/main/java/com/catshop/repository/CouponRepository.java、OrderRepository.java：券悲觀鎖、全券／會員非取消訂單計數。
- backend/src/main/java/com/catshop/dto/admin/CouponRequest.java、CouponResponse.java；dto/order/CouponQuoteRequest.java、CouponQuoteResponse.java、CreateOrderRequest.java、OrderResponse.java。
- frontend/src/views/AdminCouponView.vue：Admin /coupons；版本衝突、表單與分頁。
- frontend/src/views/CheckoutView.vue：按套用才帶入訂單、優惠預覽、輸入改變時清除先前優惠、較慢舊回應防護。
- frontend/src/views/OrderDetailView.vue、AdminView.vue：訂單原始折扣顯示。

## 驗證與展示

- 133 項後端測試通過，包括新增 5 項優惠券測試：最新價格、不保留名額、每人限制、會員取消返還、規則修改不改舊單、停用／到期／門檻、版本衝突、折抵上限、交易回滾。
- 真實 SQL Server 16 項整合測試通過；新增優惠券測試同時使用不同會員與不同 SKU 爭最後名額，只一單成功；驗證 Admin／會員隔離、拒絕客戶端假折扣、失敗時購物車保留、Admin／會員取消返還、快照及停用。所有變更在隔離測試資料庫，測完移除測試庫。
- 前台／Admin 建置通過，5 項優惠券畫面測試通過，真實 Vue／後端／SQL Server 購物流程回歸通過。frontend 執行 npm run test:coupons 可重跑畫面測試。
- 本機 SQL Server 為 17.x，測試使用 AllowOtherVersion；不宣稱已在 SQL Server 2022 確認。
- 展示已沿用原持久資料庫套用 V11，不重建會員、商品或訂單；重啟前後會員 2、商品 10，訂單／購物車／收藏／出貨紀錄 0，筆數一致。Admin API 查詢與前後台頁面驗證可用。不自動建立測試優惠券；可在 Admin「優惠券管理」自行新增。

## 尚未實作

百分比折扣、指定商品／分類／會員、領券、首購券、多券疊加、使用紀錄專頁、已用數統計、批次發券、通知、營運變更稽核、折扣明細分攤、部分退貨／真實退款均未做。此版是核心固定金額流程，不是完整促銷引擎。

原始 index.html 規格書尚未在本機找到；此批依產品待辦與現有架構延伸，不宣稱已逐條符合原規格。未提交或上傳程式碼。
