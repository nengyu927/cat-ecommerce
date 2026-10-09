# M01 第十五批：前端下單識別碼與斷線恢復

日期：2026-10-09。延續 ORDER_REQUEST_ID_NOTES.md。原 index.html 尚未取得，不宣告 M01 全部完成，未修改 Excel 計畫表。

## 本批完成

- 第一次送出產生 UUID，先在目前分頁的 sessionStorage 保存識別碼、登入會員 ID／epoch、建立時間、商品 SKU／數量摘要與原下單欄位，再發出請求。儲存失敗則不送 POST。
- 收件人、電話、地址、備註、付款方式與實際套用的優惠碼一併保存；不是保存尚未套用的優惠碼，也不是信任客戶端折扣金額。送出與重試都使用同一份原欄位及 requestId。
- 結果不明、重新整理或離頁返回後，恢復待確認請求並鎖定原收件／付款欄位，正常「確認下單」不能另建新請求。不再提供前批手動勾選後換碼送出的流程。
- 提供「查詢此次下單結果」與「以相同識別碼重試」。404 不代表原請求已停止，也不自動解除待確認狀態或改用新碼。查詢失敗仍保留原請求；不自動重送 POST。
- 已知 400／422 資料拒絕或一般 409 衝突可釋放本次暫存，依原錯誤流程修正／同步後再送；REQUEST_ID_CONFLICT 保留識別碼、停用重送，改由查回確認。
- 成功建立、成功重送或成功查回後，清除本次暫存並開啟訂單詳情。查回的是已成立原訂單，不直接清空後來加入的購物車，改為讀取後端目前購物車。
- 離頁後的舊 POST／GET 回應不導向、不清除新頁暫存、不回填新頁錯誤；保留識別碼供重新進入結帳時查回。登出與登入 epoch 變更清除目前分頁暫存，舊會員結果不能影響新會員。
- 收件內容超過兩小時時，在下次存取暫存時移除，保留識別碼讓使用者查回；此時禁止重新送出。不是精準兩小時背景刪除，也不宣稱 sessionStorage 是加密儲存。
- 暫存只使用 sessionStorage，不寫入 localStorage；通常限定目前分頁，但瀏覽器還原分頁可能還原 sessionStorage。新分頁、換裝置或登出後應使用我的訂單列表確認結果，本批不是跨裝置請求恢復。
- 暫存格式錯誤或讀取失敗採停止送出的方式處理，不默默丟失未知結果後建立新請求。

## 後端版本與購物車防護

- 新增登入限定 GET /api/orders/request-identity，回 {version:1}。第一次下單與原請求重試都先確認此契約；不支援、版本不符或連線失敗就不發出該次 POST。初次請求安全釋放未送出的暫存；已有未知結果則保留原識別碼。
- 此檢查避免新版前端連到未更新後端時，後端忽略 requestId 而失去防重複保障。需要一起更新後端與 V12，不能只複製前端就視為完整套用。
- 若目前購物車商品／數量與保存摘要不同，停用重試但保留查回。重試函式送出前也重新讀取購物車並比對，避免伺服器未收到原請求時誤買後來加入的商品。
- 空購物車允許相同識別碼重試，讓後端回傳已成立原訂單；若真的沒有原訂單，後端會拒絕空購物車。一般「確認下單」仍不能對空購物車送出。
- 比對商品／數量不凍結價格、庫存或優惠，後端仍做最終驗證。讀取與 POST 之間仍可能被其他分頁修改；本批不宣稱已提供資料庫層的購物車快照／版本驗證。

## 具體程式碼

- frontend/src/utils/checkoutAttempt.js：read／save／settle、TTL、會員／epoch、欄位驗證與 checkoutCartSignature。
- frontend/src/views/CheckoutView.vue：restoreAttempt、lookupResult、retryPending、sendAttempt、finishOrder、契約版本與重試前商品比對、暫存／恢復畫面。
- frontend/src/stores/auth.js：syncFromStorage、accept(newLogin)、clearSession 清除暫存。
- frontend/src/api/index.js：orderApi.requestIdentity 與前批 byRequestId。
- backend/src/main/java/com/catshop/controller/OrderController.java：requestIdentity 版本端點。
- backend/src/test/java/com/catshop/CheckoutIntegrationTest.java：版本端點成功與未登入拒絕。
- frontend/scripts/checkout-identity.cjs：兩模式的重新整理、原內容重試、空購物車、查回失敗、個資逾期、儲存失敗、會員切換、離頁、識別碼衝突、登出、舊後端與購物車變更。
- frontend/scripts/checkout-outcome.cjs、checkout-recovery.cjs、stability.cjs：對新契約更新原回歸斷言。
- frontend/scripts/e2e.cjs：請求真正送到 SQL Server 後，僅丟棄瀏覽器回應，再重新整理與實際重送；不使用假訂單代替真實後端。

## 本次驗證

- 前端 test:checkout-identity 24、test:checkout-outcome 14、test:checkout-recovery 12、test:stability 12、test:cart-state 8，合計 70 項通過。
- 前台與 Admin 建置皆通過。

- 後端一般測試 152 項通過；SQL Server 整合測試 22 項通過，皆 0 失敗／錯誤／略過。
- 真實 Vue → Spring Boot → SQL Server：成立訂單後回應中斷、重新整理、同 UUID／同內容重送、仍只一筆訂單／一次庫存扣減、依識別碼查回及暫存清除均通過。既有分類／搜尋返回、分頁、取消與登入回歸通過。
- 本機 SQL Server 17.x 搭配 AllowOtherVersion；不是指定 SQL Server 2022 版本驗收。所有 runner 生成的隔離資料庫與專用登入已清理，展示資料未更動。

## 下一步

把商品／數量比對提升到後端交易內的購物車快照／版本驗證：第一次接收到該識別碼的下單也必須符合使用者確認過的購物車，避免查詢後到 POST 之間被其他分頁改動。需一起確認 DTO、請求指紋、舊格式相容、購物車變更版本與交易併發測試，再補完整錯誤復原。

## 交付界線

只更新工作區與本機 ZIP，不提交、不上傳、不重啟展示服務，不修改 Desktop 原始專案或展示持久資料。展示後端尚未套用時，新前端會停止送出並提示更新，而不是退回無識別碼下單。原始規格書及計畫表仍待整體驗收。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。
