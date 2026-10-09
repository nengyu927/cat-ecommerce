# 第六批：過期登入紀錄自動清理

## 行為

新增後端定期清理，不需要前端操作，也不新增公開刪除 API。現有前後台一鍵展示登入保留。

- 使用與 AuthSessionService 相同的 UTC 時間基準，只刪除 `expiresAt <= cutoff` 的登入紀錄。
- 尚未到期的有效登入不刪除；已撤銷但尚未到期的紀錄仍保留至原到期日，不改變 JWT／refresh 授權及重放防護。
- 每輪使用固定截止時間，先依到期時間與 ID 排序取得有限筆 ID，再刪除符合 ID 且仍已到期的紀錄；刪除時重新檢查到期條件。
- 每批獨立交易提交，預設每批 200 筆，每輪最多 10 批；剩餘資料下次處理，不使用無上限 deleteAll。
- 預設啟動 60 秒後第一次執行；前一輪結束後間隔 1 小時再執行。
- 遇到資料庫錯誤記錄已完成筆數及錯誤，下次排程重試；已提交批次不回滾。
- 清理只有登入工作階段紀錄，不刪除會員、商品、購物車、訂單或圖片。

## 設定

位於 `backend/src/main/resources/application.yml`：

| 環境變數 | 預設 | 限制／作用 |
|---|---|---|
| `APP_AUTH_CLEANUP_ENABLED` | true | false 停用此清理排程 |
| `APP_AUTH_CLEANUP_BATCH_SIZE` | 200 | 每批 1～1000 筆 |
| `APP_AUTH_CLEANUP_MAX_BATCHES` | 10 | 每輪 1～100 批 |
| `APP_AUTH_CLEANUP_DELAY_MS` | 3600000 | 執行間隔至少 60000 毫秒 |
| `APP_AUTH_CLEANUP_INITIAL_DELAY_MS` | 60000 | 啟動延遲至少 1000 毫秒 |

啟用時不合法設定會使啟動失敗，避免意外高頻或大量刪除。若有保留登入紀錄的稽核需求，請先停用並另訂保留政策；目前不是完整登入稽核系統，沒有刪除後復原 API。

## 程式依據

- `backend/src/main/java/com/catshop/repository/AuthSessionRepository.java`：有界到期 ID 查詢與刪除時再次檢查。
- `backend/src/main/java/com/catshop/service/AuthSessionCleanupService.java`：每批 REQUIRES_NEW 交易、批次上限與空集合保護。
- `backend/src/main/java/com/catshop/scheduling/AuthSessionCleanupJob.java`：固定 UTC 截止時間、批次迴圈、失敗記錄與下次重試。
- `backend/src/main/java/com/catshop/config/AuthSessionCleanupConfig.java`：條件啟用排程。
- `backend/src/main/java/com/catshop/config/AuthSessionCleanupProperties.java`：預設值與設定驗證。

無 Entity／DTO／API 契約變更，未修改 Flyway V1～V5，沿用 V5 的到期索引。

## 測試

- `CheckoutIntegrationTest`：2 個新清理情境，驗證分批上限、已到期／撤銷紀錄刪除、尚未到期紀錄保留、授權狀態、精確到期邊界及刪除前到期條件重新檢查。
- `AuthSessionCleanupJobTest`：8 個情境，驗證部分批次／空批次停止、最大批數、固定截止時間、失敗後可再執行、關閉排程、預設值綁定、設定驗證及無效設定啟動失敗。測試刻意模擬資料庫錯誤，該錯誤日誌不代表測試失敗。
- `SqlServerCheckoutIT`：新增真實 SQL Server 清理情境，測試資料使用與登入流程相同的 Hibernate 時間綁定；獨立於完整 HTTP／Edge 購物與展示登入情境。測試時關閉背景排程，直接呼叫實際交易服務，避免非同步刪除干擾斷言。
- 完整驗證仍使用唯一暫存資料庫與專用 SQL 登入帳號，結束後清除；詳細最新結果見交付驗證紀錄。

## 限制與尚未完成

目前是單機排程，沒有分散式排程鎖。多個節點可能重複選取同批資料；刪除條件再次檢查，不會因此擴大到未到期紀錄，但可能額外競爭鎖或需要下次再處理。尚未做多節點／大量資料壓力測試與清理監控面板。

本機 SQL Server Express 17.x，不等於規格的 SQL Server 2022 驗收。訪客結帳、N+1／模組整理、孤兒圖片清理、多節點限流及 SQL Server 2022 驗收仍未完成。

工作只修改本對話的專案副本；桌面原專案未修改，未 Git 提交或上傳。
