# 計畫表 R07：Admin 會員詳情／歷程獨立頁

日期：2026-10-08。延續 R01–R06，不重設展示資料。原始 index.html 規格書仍未取得，不宣稱原始規格全部完成；本批未改寫 Excel 計畫表。

## 完成內容

- 列表 /members 保留搜尋與分頁，移除內嵌歷程及狀態操作對話框，改成導向會員詳情／操作紀錄。
- 新增 /members/:id 與 /members/:id/history；直接開啟與重整會依 ID 查後端，不依賴列表暫存。
- 新增 GET /api/admin/members/{id}，唯讀交易，沿用安全 AdminMemberResponse。欄位只有 id、email、name、phone、role、status、statusVersion、createdAt、updatedAt；null 欄位沿用既有 Jackson 設定省略，不回傳 Entity、密碼雜湊、token、工作階段或重設資訊。
- 非法 ID 400、找不到 404；未登入 401、一般會員 403，仍由既有 Admin 權限保護。
- 停用／恢復移到詳情頁；仍使用 PATCH /api/admin/members/{id}/status，提交 status/version/reason，必須確認並填 1 至 200 字原因。
- 成功更新詳情與新 statusVersion，不自動重送衝突操作；409 保留原因並提示取消後重新載入，再以最新版本操作。
- 管理員／自己帳號仍受後端保護，畫面無停用管理員按鈕。不新增密碼修改、會員資料修改、升降權限或帳號刪除。
- 停用沿用工作階段／重設連結撤銷，保留訂單、購物車、收藏；恢復仍須重新登入。既有交易鎖、版本檢查、稽核記錄與回滾行為不變。
- 歷程沿用 GET /api/admin/members/{id}/history，分頁顯示原因、操作者、前後狀態、版本與時間；沒有歷程時不補建。
- 列表的 keyword/status/role/page 保留在詳情與歷程網址。歷程使用獨立 historyPage，返回列表不混入歷程頁碼；瀏覽器前後頁可回到原頁。
- 舊 /members?memberId=...&historyPage=... 轉成獨立歷程頁；正式 Admin 的舊 /admin/members/:id 及 /admin/members/:id/history 轉址保留 query/hash。
- 會員子頁標題／主選單選取狀態同步。390px 詳情、表格區及確認框維持可用，表格區可橫向捲動。
- 未提交原因離開／beforeunload 提醒；儲存中阻止重送與站內導覽；登入身分改變仍能回到登入頁，舊回應不覆蓋新頁。
- 讀取失敗時不顯示舊會員操作，可重新載入；非法 ID 或歷程頁碼不發 API。

## 網址

Admin 主機維持 http://127.0.0.1:5174：

| 用途 | 路徑 |
|---|---|
| 會員列表 | /members |
| 會員詳情／狀態操作 | /members/:id |
| 狀態操作紀錄 | /members/:id/history |

前台 5173 的 /account 不變；不提供前台讀取其他會員資料的 API。

## 主要程式碼依據

| 檔案 | 依據 |
|---|---|
| frontend/src/views/AdminMemberView.vue | 列表專頁、搜尋／分頁、詳情與歷程入口、舊 query 轉址 |
| frontend/src/views/AdminMemberDetailView.vue | 直接讀取、詳情與歷程、狀態操作、原因／版本／草稿防護、序號防慢回應 |
| frontend/src/router/admin.js | Admin 受保護路由、memberHistory meta、舊址相容 |
| frontend/src/AdminApp.vue | 頁面標題與會員主選單 |
| frontend/src/api/admin.js | member(id) |
| backend/src/main/java/com/catshop/controller/admin/AdminMemberController.java | 單筆 GET、既有 Admin 權限 |
| backend/src/main/java/com/catshop/service/admin/AdminMemberService.java | detail 唯讀方法、既有狀態交易與稽核 |
| backend/src/main/java/com/catshop/dto/admin/member/AdminMemberResponse.java | 沿用既有安全欄位白名單，未新增敏感欄位 |
| frontend/scripts/admin-members.cjs | 15 項會員頁與操作回歸 |
| frontend/scripts/e2e.cjs | 真實會員詳情直開／重整／歷程／返回列表 |
| backend/src/test/java/com/catshop/AdminMemberIntegrationTest.java | 單筆 GET 401/403/400/404、安全欄位與讀取不新增稽核 |
| backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java | 真實 HTTP 單筆權限／安全欄位／最新狀態與版本、既有停用與資料保留 |

Entity、Repository、DTO 欄位、JWT／Security 與 Flyway 均未修改；沒有資料庫遷移。詳情 DTO 重用既有安全 DTO，不建立內容重複的第二份型別。

## 驗證

- npm run test:admin-members：15 項通過。涵蓋列表條件、原因／版本／稽核、取消不寫入、衝突、手機、會員拒絕、登入 epoch 切換、詳情重整、列表返回、歷程分頁、舊址、404／失敗重試、非法 ID／頁碼、未提交原因離開、儲存中阻擋、管理員保護及慢回應。
- npm run test:coupons：14 項通過。
- npm run test:category-routes：11 項通過。
- npm run test:product-routes：10 項通過，獨立重跑通過。
- npm run build：前台與 Admin 通過。
- npm run test:shipments：12 項通過。
- backend/scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser：最終 BUILD SUCCESS，133 項後端測試、17 項真實 SQL Server 整合測試與真實瀏覽器流程通過，含會員詳情直開／重整／歷程／返回列表。
- SQL Server 實測為本機 Express 17.x，不是規格指定的 SQL Server 2022；2022 仍須在目標環境重跑。唯一命名 catshop_it_* 測試資料庫與登入均已清理，未操作展示資料庫。

本批 SQL 驗證初次因測試假設固定九個 JSON 欄位而失敗；phone 為 null 時依既有設定省略。修正為欄位白名單與必要欄位檢查，不改動序列化設定。隨後修正測試編輯誤套到優惠券斷言的位置並重跑；沒有放寬優惠券原有驗證。

## 下一步

R08：會員中心總覽、個人資料 /account/profile、密碼 /account/password 拆頁；沿用現有 API，改密碼後撤銷舊工作階段並重新登入，維持跨分頁同步。R09 再接收件地址列表／新增／編輯。

本批未提交 Git 或上傳、未啟動／重啟展示服務，也沒有清空展示資料。Desktop 原始專案未變動。
