# 計畫表 R08：會員中心／個人資料／密碼拆頁

日期：2026-10-08。依計畫表 R08 拆頁，不擴充商業功能。原始 index.html 規格書仍未取得，不宣稱原始規格全部完成；本批未改寫 Excel 計畫表。

## 完成內容

- /account 會員中心改為功能入口：個人資料、變更密碼、我的訂單、我的收藏。
- 新增 /account/profile 與 /account/password，兩者都須登入。直接開啟或重整透過既有 GET /api/members/me 載入。
- 個人資料表單只提交 name、phone，沿用 PUT /api/members/me；成功後使用安全 MemberResponse 更新登入顯示與本機 session，不附帶 memberId、角色、密碼或地址。
- 姓名不可空白、最大 100 字；電話最大 30 字。伺服器欄位錯誤仍使用 FieldError、aria-invalid、aria-describedby 顯示並保留草稿。
- 密碼表單只提交 currentPassword、newPassword，沿用 PUT /api/members/me/password；新密碼 8–64 字元、密碼輸入遮罩、autocomplete 提示。
- 改密碼失敗保留表單並顯示欄位錯誤；成功清空密碼、以 password-changed 清除 session，再到登入頁。
- 沿用 JWT／refresh 撤銷及 storage 跨分頁同步。另一分頁有未儲存草稿時，強制登出仍可導向登入，不被草稿離開確認擋住。
- 密碼只在當頁記憶體中保留；離開／切換表單／重新載入清空，不寫入 localStorage。
- 未儲存離開／beforeunload 提醒，重新載入清除草稿前確認；儲存中防重送並阻擋站內導覽。
- 讀取失敗不顯示可提交表單，有重新載入入口。頁面讀取序號與登入 epoch 保護，舊回應不能恢復已登出會員。
- 會員中心入口與獨立表單在 390px 無整頁橫向溢出，取代原本固定雙欄個人資料／密碼表單。
- 地址列表／新增／編輯／刪除仍保留在 /account；本批只移除原個人資料與密碼表單，不改地址 CRUD。地址獨立路由留給 R09，不宣稱已完成。

## 網址

前台主機維持 http://127.0.0.1:5173：

| 用途 | 路徑 |
|---|---|
| 會員中心總覽及既有地址管理 | /account |
| 個人資料 | /account/profile |
| 變更密碼 | /account/password |

Admin 主機 5174 與後台會員詳情／歷程不變。

## 程式碼依據

| 檔案 | 內容 |
|---|---|
| frontend/src/views/AccountView.vue | 總覽入口、移除個人資料／密碼內嵌表單、保留既有地址操作 |
| frontend/src/views/AccountSettingsView.vue | 資料／密碼獨立表單、DTO 白名單、欄位錯誤、草稿／儲存／epoch 防護 |
| frontend/src/router/storefront.js | 新增受保護的 account-profile／account-password 路由 |
| frontend/scripts/account-routes.cjs | 10 項會員拆頁測試 |
| frontend/scripts/stability.cjs | 舊表單測試改用新路由；預設自行啟動／關閉測試網站，補出貨讀取 mock |
| frontend/scripts/e2e.cjs | 真實 API 個人資料直開／重整／返回／密碼入口，以及改密碼跨分頁登出 |
| frontend/package.json | test:account-routes |
| frontend/src/stores/auth.js | 沿用 refreshProfile、persist、clearSession、storage 同步；本批未修改 |
| backend/src/main/java/com/catshop/controller/MemberController.java | 沿用 me、profile 更新與 password API；本批未修改 |

後端 Entity／DTO／Service／Repository、Security／JWT 與 Flyway 均未修改，不需要資料庫遷移。沒有新增帳號刪除、會員角色變更或地址單筆 API。

## 驗證

- npm run test:account-routes：10 項通過。入口／手機、直開重整、白名單儲存、欄位錯誤、草稿重載／離開、密碼失敗與雙分頁登出、防重送、載入失敗、慢儲存登出保護、未登入拒絕、密碼草稿清除。
- npm run test:stability：12 項通過；既有結帳、改密碼、refresh、登入 epoch、資料與購物車慢回應回歸，無未處理頁面錯誤。
- npm run build：前台與 Admin 通過。
- backend/scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser：BUILD SUCCESS，133 項後端測試、17 項真實 SQL Server 整合測試與真實瀏覽器流程通過。
- SQL 實測為本機 Express 17.x，不是規格指定 SQL Server 2022；2022 須在目標環境重跑。唯一命名 catshop_it_* 測試資料庫與登入已清理，未操作展示資料庫。

舊 stability 測試首次缺少 5175 測試網站，後續發現漏列 /orders/1/shipment mock；已補自動啟閉與空出貨回應再完整重跑，不修改實際出貨流程。

## 下一步

R09：地址列表 /account/addresses、新增 /account/addresses/new、編輯 /account/addresses/:id/edit。若缺少單筆地址 API，補擁有者驗證 GET；不能讀寫其他會員地址，維持預設地址一致性。

本批未提交 Git 或上傳、未啟動／重啟展示服務，也未清空展示資料。工作區為更新來源，Desktop 原始專案未變動。

