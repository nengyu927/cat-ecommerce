# 第二批：登入更新與撤銷

本文件保留第二批驗證紀錄；第三批已補 MapStruct 與真實 SQL／瀏覽器實連，最新結果見 `MAPPING_INTEGRATION_NOTES.md`。

## 完成內容

- Access JWT 預設 15 分鐘，含唯一 jti、工作階段 sid；舊 JWT 不含 sid，部署更新後需重新登入。
- Refresh Token 為 256-bit 隨機秘密，使用 HttpOnly、SameSite=Strict、Path=/api/auth Cookie，不回傳 JSON、不存 localStorage；資料庫只存 SHA-256 雜湊。
- 登入工作階段固定 7 天，更新不延長伺服器期限。
- 新增 POST /api/auth/refresh：以 Cookie 換發 JWT、輪替秘密；POST /api/auth/logout：撤銷目前工作階段並清除 Cookie。
- 最近一枚已消耗 Token 重放會撤銷該工作階段。更早或偽造 Token 也不能更新，但不觸發撤銷，避免僅知道公開 sid 就能強制他人登出。
- 悲觀資料庫鎖確保同一秘密併發更新最多一次成功；重複更新會觸發撤銷。登出也接受剛輪替的上一枚秘密以處理競爭。
- 每個認證請求檢查工作階段與最新會員狀態／角色；登出讓尚未過期 JWT 失效，改密碼撤銷全部裝置登入。
- 登入／註冊／更新／登出拒絕非白名單 Origin 與 Sec-Fetch-Site: cross-site；無 Origin 的非瀏覽器客戶端仍可呼叫。
- axios 對非 auth API 的 401 最多更新並重送一次；單分頁共用 Promise，支援 Web Locks 時跨分頁互斥。失敗清除登入並導回登入頁，登入失敗不會啟動更新。
- 前端登出立即清除本機狀態；若離線，無法保證伺服器撤銷，必須恢復連線撤銷或等待到期。

## 程式依據（專案相對路徑）

- Entity：backend/src/main/java/com/catshop/entity/AuthSession.java
- Repository／鎖：backend/src/main/java/com/catshop/repository/AuthSessionRepository.java
- 輪替／撤銷：backend/src/main/java/com/catshop/service/AuthSessionService.java
- API／Cookie／來源驗證：backend/src/main/java/com/catshop/controller/AuthController.java
- JWT：backend/src/main/java/com/catshop/security/JwtTokenProvider.java、JwtAuthenticationFilter.java
- 改密碼：backend/src/main/java/com/catshop/service/MemberService.java
- 遷移：backend/src/main/resources/db/migration/V5__auth_sessions.sql
- 自動更新與登出：frontend/src/api/http.js、frontend/src/stores/auth.js
- 測試：backend/src/test/java/com/catshop/CheckoutIntegrationTest.java、frontend/scripts/smoke.cjs

## 驗證

- 後端 23 項測試，新增輪替、重放、偽造秘密、Cookie 屬性、登出、改密碼、期限、跨站與併發更新測試。
- 前端正式建置與 Edge 無介面測試通過，包含同時兩個 401 只更新一次並成功重送，及第一批購物／後台頁面回歸。
- SQL Server Express 17.0.1000.7 執行 V1–V5、Unicode、庫存 CHECK、版本更新及新資料表欄位檢查通過。使用唯一隔離測試資料庫，已清除。
- 後端行為測試用 H2，瀏覽器測試用攔截 API；尚未完成 SQL Server 2022 + Spring Boot/Flyway + 前端真實整合驗收。

## 部署與限制

1. 保留 V1–V4，讓 Flyway 套用 V5；更新後使用者重新登入。
2. 正式環境 HTTPS、APP_AUTH_COOKIE_SECURE=true；mssql profile 預設 Secure。建議前後端同源，以 /api 反向代理。
3. 依實際網址設定 APP_CORS_ALLOWED_ORIGINS，配置 APP_JWT_SECRET、資料庫帳密及可信憑證；不要沿用開發密鑰。
4. 不支援 Web Locks 時只保證單分頁互斥，多分頁同時更新可能觸發撤銷，需要重新登入。
5. Access Token 仍存 localStorage，需防範 XSS、配合 CSP 與避免不可信 HTML。尚未新增過期工作階段清理排程。

第三批已完成 MapStruct 及 Express 17.x 的 Flyway／前後端實連；仍需 SQL Server 2022 驗收。訪客結帳、模組重構、真實金流與物件儲存尚未完成，訪客身分模型需先釐清規格矛盾。
