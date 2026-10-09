# 忘記／重設密碼

本批在工作副本實作，沒有修改桌面原始專案，未提交或上傳。

## 展示操作

前台與 Admin 登入页皆有「忘記密碼？」：

- 前台 http://127.0.0.1:5173/forgot-password
- Admin http://127.0.0.1:5174/forgot-password
- 輸入註冊的 email，等待通用回覆。同會員 60 秒內重複申請不再產生新信件，原連結仍有效。
- 本機沒有 SMTP，不會真的寄出郵件。使用 VS Code 開啟工作專案的 `backend/.local/password-reset-mails/`，找最新測試信件，開啟其完整連結。
- 前台申請的連結前往前台，Admin 申請的連結前往 Admin；兩者都使用可信任設定，不接受任意跳轉網址。
- 連結 15 分鐘內有效，成功後只能使用一次；使用新密碼重新登入，不會自動建立登入。帳號共用同一密碼，因此重設會使该會員前後台的既有登入都失效。
- 開啟時立即移除網址片段，憑證只保留於此頁記憶體，不存 localStorage／sessionStorage。重新整理後必須重新開啟信件中的完整連結。
- 操作後可刪除自己產生的測試信件。信件含敏感憑證，勿貼到對話、提交、打包或分享；網站沒有公開信箱／讀取信件 API。

## API 與安全規則

- POST `/api/auth/forgot-password`：email，optional surface=storefront/admin。正常情況對存在、不存在及停用帳號回傳同一通用訊息，不回傳 token 或會員 ID；未知／停用會員不產生信件。這不是固定耗時的防枚舉實作。
- POST `/api/auth/reset-password`：token、newPassword（8～64 字）。無效、到期、已使用、帳號停用均拒絕；成功撤銷此會員所有 access JWT 對應的 session 與 refresh session，清除此介面的 refresh cookie。
- SecureRandom 32 bytes 憑證，Base64 URL 編碼；資料庫僅存 SHA-256 雜湊與會員、申請／到期時間。V8 建立 `password_reset`，每會員最多一筆，可重新申請覆蓋；會員刪除會連帶刪除重設憑證。
- 登入、refresh、個人資料更新、新／舊密碼變更及申請採共用會員鎖；重設取得會員鎖後再次查詢資料庫純量狀態，防止同一憑證並行成功兩次。登入驗證也在鎖內，避免舊密碼驗證完後跨越重設建立新登入。個人資料更新也使用同一鎖，避免全列更新把舊密碼雜湊覆寫回去。refresh 採會員→session 的相同鎖順序，避免與重設撤銷 session 形成鎖循環。
- 登入後一般修改密碼也會刪除未使用重設憑證；新申請成功建立連結後舊連結失效（60 秒冷卻內的重送不更換連結）。
- 沿用來源／跨站檢查。IP＋端點限流沿用設定：預設各 10 次／60 秒，429 附 Retry-After；忽略未信任轉送 IP 標頭。會員冷卻另由持久資料與交易鎖控制。
- reset URL 使用 fragment，因此不會在首次 HTTP 路徑或 Referer 中傳送 token；API 以 POST body 傳送，不把 token 放進查詢參數。前端使用獨立匿名請求，不帶無關 JWT、不觸發 refresh；新登入在等待期間出現時不被舊成功回覆清除。
- 成功回應設定 Cache-Control=no-store；不在日誌印出原始憑證／連結。

## 郵件設定與限制

`PasswordResetMailer` 是可替換介面；目前只有操作端本機檔案實作，未接 SMTP 或外部郵件服務。local profile 啟用本機模式；其他 profile 預設停用，尚未設定時申請回 503，不假稱成功寄信。上線必須明確選擇 production profile／關閉本機模式，不可沿用預設 local profile。

- `app.password-reset.local-mail-enabled`：本機檔案模式開關。
- `app.password-reset.mail-directory`：預設後端工作目錄下 .local/password-reset-mails。
- `app.password-reset.frontend-url`、`app.password-reset.admin-url`：可信任前後台網址（可用對應 APP_PASSWORD_RESET_* 環境變數覆蓋）。

信件僅在資料交易提交後產生；交易回滾不送出。提交後若磁碟寫入失敗，API 回 503，但已提交的憑證仍會留在資料庫；待 60 秒冷卻後可重新申請。尚無持久郵件 outbox／自動重試、SMTP、寄信到達監控或信件自動清理。信箱資料夾沿用本機使用者檔案權限，未另設定専用 ACL；共用電腦須由操作端加強權限。

expired row 每會員一筆、重新申請覆蓋；尚無過期憑證清理工作。IP 限流是單節點記憶體，不是分散式限流。此批不是 MFA、Email 驗證或登入風險評分。

## 程式碼與驗證

主要檔案：V8__password_reset.sql、PasswordReset.java、PasswordResetRepository.java、PasswordResetService.java、PasswordResetMailer.java、LocalPasswordResetMailer.java、PasswordResetDelivery.java；AuthController／AuthService／MemberService／MemberRepository／LoginRateLimitFilter；前端 PasswordResetView.vue、api/passwordReset.js、router、LoginView。

測試包含：存在／未知／停用帳號通用回覆、hash-only、冷卻／輪換、到期／重放、同時使用只能一次、修改密碼撤銷、舊 access／refresh 失效、新密碼登入、跨站／Host／surface 驗證、回滾不送信、本機信件與關閉模式。SQL Server 真實 HTTP 驗證 V8 與重設流程；既有購物／收藏／庫存異動完整回歸。

瀏覽器專項使用隔離 API 替身驗證前後台入口與代理、密碼確認、移除片段、不儲存 token、成功不自動登入、失敗回饋與重新申請、遲到回覆不清除新登入、390px 版面；另有真實購物回歸。測試沒有重設展示管理員或會員的密碼。

本機 SQL Server Express 17.x；仍需在規格指定的 SQL Server 2022 驗證。重啟展示沿用持久資料庫、不重建資料；.local 與信件不包含於交付 ZIP。

最終驗證：後端 118 項、SQL Server 整合 13 項、新頁面瀏覽器 5 項均通過，前後台建置通過。SQL 實測 refresh 與重設並行、舊憑證仍無效；真實購物回歸通過。展示唯讀核對會員 2、訂單 0、購物車明細 0、收藏 0、商品 10、庫存異動 0 均與更新前相同，Flyway V8；只用不存在的帳號驗證申請，沒有替展示會員產生重設 token、測試信件或改密碼。

下一步建議：Admin 會員管理（查詢／停用與權限稽核），或物流單號與出貨歷程；SMTP／外部郵件供應商需另確認設定與授權。
