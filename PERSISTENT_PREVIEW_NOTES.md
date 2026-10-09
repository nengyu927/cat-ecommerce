# 展示資料持久化

日期：2026-10-08。已取得使用者同意切換，舊暫存展示資料庫與帳號已清除，新展示網站改用專用持久資料庫。前台 5173、Admin 5174、API 8080 不變。桌面原始專案未修改，未提交／上傳。

## 本批改動

- `backend/scripts/start-persistent-preview.ps1`：第一次產生專用 SQL 資料庫／登入；往後使用同一組設定。正常退出、Ctrl+C 或啟動失敗都不刪除資料庫／帳號。
- `.local/preview-state.xml`：資料庫名稱、端點，以及 Windows DPAPI 加密的密碼和 JWT 金鑰。僅原 Windows 使用者／電腦能解密；不顯示密碼、不放進命令列參數，SQL 帳密透過標準輸入傳遞。
- 資料庫標記 `CatshopPersistentPreview` 必須符合保存的識別碼；既有但未標記的資料庫、錯誤名稱、改變端點皆拒絕，不接管或重建未知資料庫。狀態檔鎖避免同一份設定被同時啟動。
- `.gitignore` 排除 `.local/`，更新專案壓縮檔也明確排除它、uploads、target、dist、node_modules。壓縮檔不包含展示資料／帳密／圖片上傳檔。
- `cat-ecommerce.code-workspace`：提供 VS Code 的持久化後端、前台 5173、Admin 5174 三個獨立啟動工作。
- 專案外 `work/start-preview.ps1` 改為呼叫新啟動器，不再清除展示資料。

Java 業務邏輯、API 路徑和 V1～V6 遷移未改動。原本商品／展示會員 seed 是 Flyway V2；同一資料庫已套用過就不會重跑。本批不是改成每次啟動重新寫入種子資料，也不是修改會員密碼。JWT 金鑰固定保存，仍遵守原有到期、撤銷、refresh 與密碼變更規則，不代表永久登入。

## 啟動

需求：Windows、SQL Server 在本機 TCP 1433、sqlcmd、Java／Maven；Windows SQL 管理權限足以建立 DB／登入。本機開發帳號使用 db_owner 以讓 Flyway 執行，不是正式環境最小權限部署方案。

在專案根目錄：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/scripts/start-persistent-preview.ps1
```

或用 VS Code 開啟 `cat-ecommerce.code-workspace`，選「終端機 → 執行工作」，分別啟動持久化後端、前台、Admin。前端第一次先在 frontend 執行 `npm.cmd ci`。已有服務時不要重複佔用相同連接埠；同一個狀態檔也不能同時啟動兩個後端。

只有準備資料庫、不啟動 Java：加上 `-ProvisionOnly`。預設保存位置在專案 `.local`；`-StatePath` 可明確指定另一路徑，但一般展示請沿用預設，不要隨意換檔案建立多套 DB。Server/JdbcHost/JdbcPort 必須符合保存設定。

## 驗證

- 同一份設定連續準備兩次，資料庫／登入不重建。
- 獨立連接埠 18081 真實啟動後，建立唯一測試會員、待付款訂單、購物車、收藏與商品售價修改；停止 Java，重新啟動同一資料庫。
- 重啟後上述資料與重啟前 JWT 均通過真實 HTTP 驗證，Flyway 顯示 V6 已是最新，無重跑種子資料。
- 只清除此測試會員／訂單／購物車／收藏／登入，取消訂單還庫存並還原售價；資料庫仍保留。
- 安全防護 4 項通過：帳密不以明文保存、非法名稱拒絕、端點不符拒絕、未標記資料庫不接管且原測試表不變；只清除此次唯一防護測試 DB。
- 展示切換後，前後台 HTTP 200，商品 API 正常回傳 10 個種子商品。
- 切換後重跑 84 項一般後端測試、10 項獨立 SQL 整合及真實瀏覽器收藏／結帳／取消／授權流程，均通過；清除測試 DB 後持久展示 DB 仍存在並正常提供 API。

測試腳本：`backend/scripts/verify-persistent-guards.ps1`。重啟驗證腳本為 `verify-persistent-preview.ps1 -Mode Prepare`，停止／重啟 18081 後執行 `-Mode VerifyAndClean`。後者會建立並刪除測試資料、暫改商品 1 售價，只在受控的開發驗證環境使用，不能拿來測正式網站；不接受展示連接埠 8080。

本機 SQL Server 是 Express 17.x；規格指定的 SQL Server 2022 仍須在目標環境重跑。既有 `verify-app-mssql.ps1` 仍使用唯一 `catshop_it_*` 測試 DB 並在結束清除，不引用展示狀態檔。

## 保留資料與備份注意事項

- 不要刪除 `.local/preview-state.xml` 或 SQL Server 的持久 DB／登入；刪掉設定再啟動會另建新 DB，不會自動找回舊資料。
- Windows 加密設定不能直接搬到另一台電腦／另一個 Windows 帳號；搬機需另行規劃資料庫備份還原與帳密重新配置。本批沒有自動備份、災難復原或匯入舊暫存資料功能。
- 上傳圖片仍保存在 `backend/uploads/`，請連同資料庫規劃備份。持久化只解決正常重啟不清資料，不能防止手動刪檔、資料庫損壞或電腦故障。
- 啟動失敗會保留設定及資料庫供檢查；不要用刪除設定／DB 的方式猜測修復。新 DB 建立中途失敗且沒有所有權標記時，會安全拒絕，需要人工檢查。

下一步：前台品牌／價格範圍篩選，包含條件清除、URL 保留、分頁與合法值驗證。
