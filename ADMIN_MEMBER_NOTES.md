# Admin 會員管理

本批在工作副本實作，桌面原始專案未修改；未提交或上傳。

## 使用

獨立後台 http://127.0.0.1:5174/members，側邊選單「會員管理」。整合開發模式是 /admin/members；前台不註冊這個路由。

- 搜尋姓名／電子郵件／電話，搭配狀態與角色篩選。每頁 20 位，固定會員 ID 新到舊；總筆數是符合條件的全部會員，不是当頁數量。
- 查詢條件、頁碼、選取的會員紀錄與紀錄頁碼保留於 URL。重新搜尋同條件也會重新讀取；清除條件回第一頁。
- 一般會員可停用或恢復，先開啟確認視窗，必填 1～200 字原因。視窗說明撤銷登入／保留訂單等影響，取消不送寫入請求。
- 所有管理員帳號受保護，無論自己、其他或最後一位管理員都不能在這個功能變更狀態。這是本批較保守的權限範圍，不是完整管理員生命週期管理。
- 每位會員可查看唯讀、分頁的狀態紀錄：原狀態、新狀態、原因、操作者會員 ID、時間及狀態版本。啟用功能前的狀態不補成歷史。
- 手機表格在區塊內橫向捲動、確認視窗不超出畫面；查詢錯誤可重試。版本衝突明確要求重新載入，不自動重送狀態变更。

## API 與資料

僅啟用中的 ADMIN 可使用：

- GET /api/admin/members：keyword、status、role、page、size（1～100）。
- GET /api/admin/members/{id}/history：page、size（1～100；前端每頁 10 筆）。
- PATCH /api/admin/members/{id}/status：status=ACTIVE/DISABLED、version=目前 statusVersion、reason。

列表 DTO 只含會員 ID、姓名、email、電話、角色、狀態、狀態版本、註冊／更新時間；不回傳密碼雜湊、登入 token、重設憑證或登入 session。搜尋 %, _, [, ! 視為字面，不可藉 LIKE 萬用字元擴大搜尋。keyword 最多 200 字，非法角色／狀態／分頁拒絕。

V9 新增會員 status_version（既有會員初值 0）與 member_status_audit。這是狀態操作專用版本，不是對所有個資更改遞增的 JPA @Version。

## 安全與交易

- 先檢查操作者為啟用中的 ADMIN，再用既有會員共用鎖鎖住目標。管理員帳號一律受保護，因此不會出現兩位管理員互相停用、或同時停用最後管理員的競態；本批沒有更改角色的 API。
- 必須匹配目前狀態版本才能修改；ACTIVE→DISABLED→ACTIVE 後舊版本仍拒絕（防 ABA）。同版本、同狀態的請求是 no-op，不新增稽核／遞增版本。
- 停用同交易撤銷會員所有登入 session 與未使用的密碼重設憑證；會員的商品收藏、購物車、地址與訂單不刪除。恢復不解除既有 session 的 revoked，舊 JWT／refresh 不會復活，須重新登入。
- 狀態、版本、憑證撤銷與稽核同一交易，任何一步失敗全部回滾。沿用會員→session 的鎖順序，SQL Server 驗證停用與 refresh 同時發生。
- 已通過授權、正在執行的請求不強制中斷；停用對後續授權／refresh 生效，不声稱能取消所有正在執行的交易。
- 稽核保存歷史 ID，不設連帶刪除外鍵；沒有修改／刪除稽核 API。不是防資料庫管理者竄改的稽核系統，目前沒有留存／清理政策。

## 程式碼

- backend/src/main/resources/db/migration/V9__member_admin_audit.sql
- backend/src/main/java/com/catshop/entity/Member.java、MemberStatusAudit.java
- backend/src/main/java/com/catshop/repository/MemberRepository.java、MemberStatusAuditRepository.java
- backend/src/main/java/com/catshop/dto/admin/member/：AdminMemberResponse、ChangeMemberStatusRequest、MemberStatusAuditResponse
- backend/src/main/java/com/catshop/service/admin/AdminMemberService.java
- backend/src/main/java/com/catshop/controller/admin/AdminMemberController.java
- frontend/src/views/AdminMemberView.vue、src/api/admin.js、src/router/index.js、src/AdminApp.vue
- backend/src/test/java/com/catshop/AdminMemberIntegrationTest.java、integration/SqlServerCheckoutIT.java
- frontend/scripts/admin-members.cjs

## 測試與限制

後端專項 8 項：字面搜尋／篩選／分頁、停用／登入與重設撤銷、恢復／紀錄、所有管理員保護、客戶角色拒絕、版本與 ABA、no-op、稽核失敗回滾、並行只能一次、JSON 不洩漏憑證、非法參數与 HTTP 授權。

SQL Server 整合以唯一命名的測試會員，真實註冊／下單／取消、保留訂單／購物車／收藏；停用、refresh 與兩次同版本寫入並行，驗證一筆成功一筆衝突、恢復不能復活舊登入、重設憑證移除與稽核分頁。會員列表 size=1／20 最多兩條 SQL。測試 refresh 使用不共享管理員 cookie 的獨立用戶端，避免把管理員登入誤當作會員恢復成功。

瀏覽器专項 7 項使用隔離 API 替身，驗證 URL／分頁／清除／更新、確認與取消、原因及版本送出、紀錄、衝突不重送、失敗重試、慢回應、非法條件、390px 版面、一般會員拒絕／前台排除、登入切換後忽略舊操作回應；另有真實購物與會員頁載入回歸。

展示更新只增加欄位與稽核表，不停用任何現有展示會員、不改密碼或角色，不重建持久資料。尚未提供管理員建立／升降權限、会员密碼修改、帳號刪除、個資匯出、會員詳細訂單頁、批次停用、跨節點通知或稽核匯出。

本機 SQL Server Express 17.x；仍需在規格指定的 SQL Server 2022 驗證。

最終回歸：後端 126 項、SQL Server 整合 14 項、新頁面 7 項全部通過；既有 Admin 篩選、忘記密碼頁回歸與前後台建置通過。展示升至 V9，兩位原會員皆 ACTIVE、狀態版本 0；會員 2、訂單 0、購物車明細 0、收藏 0、商品 10、庫存異動 0、重設憑證 0 與更新前一致，狀態稽核 0（未補建）。展示只驗證唯讀查詢、登入與拒絕，不修改會員狀態。

下一步建議：物流公司／單號／出貨時間及訂單出貨歷程；真實金物流或寄信服務需另確認供應商與授權。
