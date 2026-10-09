# 計畫表 R09：收件地址列表／新增／編輯拆頁

日期：2026-10-09。延續 R01–R08，不重設展示資料。原始 index.html 規格書仍未取得，不宣稱原始規格全部完成；本批未改寫 Excel 計畫表。

## 完成內容

- /account 只保留會員功能總覽，增加「收件地址」入口，不再放置地址 CRUD 表單。
- 新增 /account/addresses、/account/addresses/new、/account/addresses/:id/edit，皆須登入。
- 新增 GET /api/members/me/addresses/{id}，會員 ID 取自登入身分，使用既有 AddressResponse 安全 DTO。
- 未登入 401；他人或不存在地址一律 404，不洩漏地址是否屬於別人。非法非正整數 ID 回傳 400；前端非法 ID 不發讀取 API。
- 編輯頁依 ID 直接取得本人地址，支援直開、重整與重試，不依賴列表暫存資料。
- 新增頁取得本人基本資料與地址清單，預填收件人／電話；沒有任何地址時預設勾選，已有地址時不自動取代預設。
- 沿用 POST／PUT／DELETE 地址 API。提交白名單只有 receiverName、phone、city、district、line1、line2、postalCode、isDefault，不含 id、memberId 或 Entity 關聯。
- 新增成功 replace 至新地址編輯網址，可重整而不再次建立；更新成功維持編輯頁並更新草稿基準。
- 必填收件人／電話／縣市／地址，欄位長度沿用後端 DTO；保留 FieldError 與 aria-invalid／aria-describedby。失敗保留草稿，重新載入清除草稿前確認。
- 未儲存離開／beforeunload 提醒；儲存中阻止重送與站內導覽；登入 epoch 切換或登出不被草稿提醒阻擋，舊讀取不覆蓋新頁。
- 刪除需要確認；刪除失敗保持列表與重試入口，成功重新讀取列表。刪除處理中停用刪除按鈕並阻止站內導覽。
- 新增／更新／刪除在交易內先以會員列 PESSIMISTIC_WRITE 鎖序列化，同會員同時切換預設地址不會在服務寫入路徑產生兩筆預設。
- 保留原有「最多一筆，可沒有預設」語意：取消勾選可沒有預設；刪除預設地址不自動指定其他地址。沒有新增資料庫唯一索引，也沒有回填／修復既有不良資料。
- 編輯沒有新增 version 欄位，同筆地址內容仍沿用最後完成的寫入為準；會員列鎖保障預設一致性，不宣稱提供樂觀衝突偵測。
- 地址列表與表單 390px 無整頁橫向溢出。結帳仍使用既有本人地址清單 API，不改下單契約。

## 網址

前台主機維持 http://127.0.0.1:5173：

| 用途 | 路徑 |
|---|---|
| 會員中心總覽 | /account |
| 地址列表 | /account/addresses |
| 新增地址 | /account/addresses/new |
| 編輯本人地址 | /account/addresses/:id/edit |

Admin 5174 網址與會員管理不變。

## 具體檔案依據

| 檔案 | 內容 |
|---|---|
| frontend/src/views/AccountView.vue | 移除內嵌地址 CRUD，功能入口與 me 讀取 |
| frontend/src/views/AddressListView.vue | 列表、編輯導覽、刪除確認／失敗保留／重讀及登入序號防護 |
| frontend/src/views/AddressEditView.vue | 單筆載入、白名單、預填與預設、草稿／錯誤／導覽保護 |
| frontend/src/router/storefront.js | 三個受保護路由 |
| frontend/src/api/index.js | address(id) |
| backend/src/main/java/com/catshop/controller/AddressController.java | detail 取 SecurityUtils.requireMemberId，不接受前端 memberId |
| backend/src/main/java/com/catshop/service/AddressService.java | detail／findOwned、非正整數驗證、寫入會員列鎖及 clearDefault |
| backend/src/main/java/com/catshop/repository/MemberRepository.java | lockForAddressMutation 專用 PESSIMISTIC_WRITE 查詢 |
| backend/src/test/java/com/catshop/CheckoutIntegrationTest.java | 本人單筆、未登入、他人、非法／不存在、DTO 不帶會員關聯 |
| backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java | 真實 SQL／HTTP 擁有者隔離、並行新增／切換預設與刪除後零預設 |
| frontend/scripts/address-routes.cjs | 10 項地址拆頁測試 |
| frontend/scripts/account-routes.cjs | 會員總覽新地址入口回歸 |
| frontend/scripts/e2e.cjs | 真實地址新增、直開重整、修改、列表、確認刪除，再繼續密碼／登入流程 |

Address Entity／DTO、JWT／Security 與 Flyway 均未修改，不需要資料庫遷移。既有資料庫與 Desktop 原始專案未變動。

## 驗證

- npm run test:address-routes：10 項通過，涵蓋直開／重整／白名單／手機、新增成功轉址、首筆與非首筆預設、欄位錯誤／重載確認、離開／防重送、刪除失敗／重試、404／非法 ID、慢回應、跨分頁登出與未登入拒絕。
- npm run test:account-routes：10 項通過，會員資料與密碼回歸；獨立重跑亦通過。
- npm run test:stability：12 項通過。
- npm run test:admin-members：15 項通過。
- npm run build：前台與 Admin 通過。
- backend/scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser：BUILD SUCCESS，133 項後端測試、18 項真實 SQL Server 整合測試與真實瀏覽器流程通過。
- 實測 SQL Server 為本機 Express 17.x，不是規格指定的 SQL Server 2022；2022 須在目標環境重跑。唯一命名 catshop_it_* 測試資料庫與登入均已清理，未操作展示資料庫。

## 下一步

R01–R09 拆頁批次已完成；下一批接 M01：統一前後台列表 query 的 page／size／sort／filter 驗證與返回動線，保留目前已完成的篩選狀態，不重做會員與商品現有導覽。

本批未提交 Git 或上傳、未啟動／重啟展示服務、未清空展示資料。交付 ZIP 為本機更新專案，不包含 node_modules、target、dist、.local、uploads 或日誌。
