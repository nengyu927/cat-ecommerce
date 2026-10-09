# 計畫表 R06：Admin 優惠券新增／編輯獨立頁

日期：2026-10-08。延續 R01–R05 拆頁，不重設展示資料。原始 index.html 規格書仍未取得，不宣稱原始規格全部完成；本批沒有改寫 Excel 計畫表。

## 完成內容與網址

- 優惠券列表 `/coupons` 不再展開內嵌表單。
- 新增 `/coupons/new`，編輯 `/coupons/:id/edit`。Admin 展示網址維持 `http://127.0.0.1:5174`；前台 5173 結帳不變。
- 新增成功以 replace 轉到新券編輯網址，可重整而不重送建立；更新成功留在編輯頁並更新 version。
- 新增 GET `/api/admin/coupons/{id}`：Admin 安全 DTO、唯讀交易；非法 ID 400、不存在 404，未登入 401、一般會員 403。
- 編輯依 ID 直接取得資料，不依賴先開列表；日期／金額／使用上限輸入限制、錯誤保留草稿、重新載入清除草稿前確認。
- 沿用 PUT 的交易鎖／version 衝突 409；優惠碼建立後不可更名，不提供刪除。
- 儲存白名單只有 code、discountAmount、minimumAmount、maxUses、active、version、startsAt、endsAt，不夾帶 id 或其他唯讀資料。
- 日期顯示為瀏覽器本地時間，修改後轉成 API 契約的 UTC LocalDateTime 字串；保留秒數與毫秒。未修改日期直接送回 GET 的原始字串，不額外截斷。
- 既有 SQL Server V11 日期欄位為 DATETIME2(3)，實際儲存只到毫秒；本批不升級精度。若其他資料來源回傳更多小數位，未修改日期仍完整保留原始 API 字串。
- 台北／UTC 與夏令時間缺口驗證；不存在的本地日期時間拒絕，不默默向後調整。夏令時間重複時段沿用瀏覽器 Date 的選擇，UI 不提供偏移量選擇。
- 返回列表保留 page，上一／下一頁同步網址；非法頁碼不發 API，可清除重試。瀏覽器前後頁可回到編輯。
- 未儲存離開／beforeunload 提醒，儲存中阻止重送與站內導覽；讀取序號防止慢回應覆蓋新頁。
- 舊 `/admin/coupons/new`、`/admin/coupons/:id/edit` 相容轉址並保留 query/hash；主選單／標題維持優惠券選取狀態，390px 編輯頁不整頁溢出。
- 不修改結帳折扣計算、名額、取消返還或舊訂單快照；沒有新增核銷／稽核專頁（仍是 M04）。

## 程式碼依據

| 檔案 | 依據 |
|---|---|
| frontend/src/views/AdminCouponEditView.vue | 依 ID 載入、表單白名單、version、時間保留、草稿與導覽防護 |
| frontend/src/views/AdminCouponView.vue | 列表專頁、路由新增／編輯、page 與重試 |
| frontend/src/utils/couponTime.js | UTC／本地轉換、格式與不存在時間驗證 |
| frontend/src/router/admin.js | 新增／編輯路由與舊址轉換、Admin meta |
| frontend/src/AdminApp.vue | 優惠券子頁標題與主選單 |
| frontend/src/api/admin.js | coupon(id) 單筆 GET |
| backend/src/main/java/com/catshop/controller/admin/AdminCouponController.java | 單筆 GET 與既有 Admin 權限 |
| backend/src/main/java/com/catshop/service/CouponService.java | detail 唯讀查詢，沿用 save 的不可更名／version 防護 |
| backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java | 單筆 HTTP、401/403/400/404、DTO、API 日期往返、更新版本、不可更名、舊快照與名額競態 |
| frontend/scripts/coupons.cjs | 14 項瀏覽器測試，含既有前台優惠券回歸 |
| frontend/scripts/coupon-time.mjs | 可重跑的日期與三種時區測試 |
| frontend/scripts/e2e.cjs | 隔離真實後端新增測試券，編輯直開／重整／返回列表 |

Entity、Repository、DTO 欄位、Security/JWT 與 Flyway 均未修改；重用既有 CouponResponse，沒有資料庫遷移。

## 驗證

- npm run test:coupons：14 項通過；前台 quote／下單／快照、編輯版本與衝突、日期精度、直接載入與重整、離開確認、404 重試、非法 ID、舊址、會員拒絕、衝突重載、慢回應、非法列表頁碼。
- npm run test:coupon-time：台北、UTC、毫秒與格式、無效日期、夏令時間缺口通過。
- npm run test:category-routes：11 項通過。
- npm run test:product-routes：10 項通過。
- npm run test:product-assets：11 項通過。
- npm run test:shipments：12 項通過。
- npm run test:admin-filters：通過。
- npm run build：前台與 Admin 通過。

- backend/scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser：最終 BUILD SUCCESS，133 項後端測試、17 項真實 SQL Server 整合測試與真實瀏覽器流程通過，含新增優惠券直開／重整／返回列表。
- 實測 SQL Server 為本機 Express 17.x，不是規格指定的 SQL Server 2022；2022 仍需在目標環境重跑。唯一命名 catshop_it_* 測試資料庫與登入均已清理，沒有操作展示資料庫。

本批前兩次 SQL 測試因測試比較方式失敗：建立回應直接與重新讀取 JSON 比較有 SQL 毫秒精度與 BigDecimal 表示差；JDBC Timestamp 直接比較亦有時區轉換差。已改為依 API UTC 契約與毫秒精度比較日期，金額採數值比較，最終全部通過。本批未更改 Hibernate JDBC 時區設定；不同 JVM 時區、原始 DATETIME2 值的互通／遷移仍需另外審查。

## 下一步

R07：Admin 會員詳情 `/members/:id` 與歷程 `/members/:id/history`；新增安全詳情 DTO，不回傳密碼／token，狀態操作保留原因與版本。不新增會員升降權限或帳號刪除。

未提交 Git 或上傳；本機展示服務沒有啟動／套用。本批在工作區修改，Desktop 原始專案未變動。
