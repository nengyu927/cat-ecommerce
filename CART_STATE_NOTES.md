# 前端購物車回應順序與登入切換防護

驗證日期：2026-10-08。本批延續後端購物車列鎖，補強前端狀態一致性；沒有改動後端、資料表、API 或前後台網址。

## 完成內容

原本 fetch 有登入 epoch 檢查，但新增、修改、移除與清空各自送出並直接套用回應。同一分頁的較舊回應可能最後抵達並覆蓋較新的購物車。

現在每個購物車 Store 使用 WeakMap 維護操作佇列：

- 查詢、加購、修改、移除、清空依呼叫順序執行，前一請求完成並套用結果後才開始下一個。
- 沒有插入寫入操作時，同一登入的同時查詢仍共用 pending fetch，避免頁首與購物車頁重複查詢。
- 寫入會使既有 fetch 去重紀錄失效；寫入之後的 fetch 排在寫入後，不會誤用寫入前的讀取結果。
- 排入佇列時記錄登入 epoch，送出前及套用回應前都同步檢查。登入切換後，尚未送出的舊操作被拒絕，不會使用新會員身分執行。
- reset 使舊佇列失效並清除畫面與 loading。登出、登入切換或既有結帳完成流程呼叫 reset 後，舊回應不得恢復項目；新操作不必等待舊網路請求。
- 失敗請求仍向呼叫端回報錯誤，但不會阻塞後續操作。舊 fetch 的 finally 不能清掉新 fetch 的 loading。

## 程式碼依據

| 檔案 | 關鍵依據 |
|---|---|
| `frontend/src/stores/cart.js` | `enqueue` 使用 `queue.tail.then` 排序；`current()` 檢查登入 epoch 與佇列身分；`queue.tail = promise.catch(...)` 保留錯誤回報但恢復佇列。 |
| `frontend/src/stores/cart.js` | `invalidateFetch` 在四個寫入 action 中取消舊讀取去重；`reset` 刪除佇列、pending fetch 並清除狀態。 |
| `frontend/scripts/cart-state-harness.js` | 使用真實 Pinia Store 與可手動完成的 API 替身，精確控制請求完成順序。測試專用，沒有被正式應用入口引用。 |
| `frontend/scripts/cart-state.cjs` | 建立獨立 Vite 測試站 5178，使用 Edge 執行 Store 回歸，結束關閉瀏覽器與測試站。 |
| `frontend/package.json` | 新增 `npm run test:cart-state`。 |

既有 `App.vue` 的登入切換 reset 與 `CheckoutView.vue` 的結帳後 reset 不需要改動；axios 的登入 epoch 與 refresh 防護保留。

## 驗證

- 新增 8 個前端 Store 案例通過：讀取先於修改、讀取去重、寫入後讀取、四種寫入排序、失敗後繼續、登出與登入切換、reset 後舊回應／loading、防止未登入寫入。
- 原有畫面穩定性測試 11 個案例通過，無未處理的頁面錯誤。
- 後台篩選回歸通過；前台及 Admin 正式建置通過。
- 本批重新執行後端完整驗證：77 個一般測試、9 個 SQL Server 整合測試通過，0 失敗／錯誤。
- 真實 Edge → Vue → Spring Boot → SQL Server 購物流程通過：註冊、加購、重新載入持續存在、結帳扣庫存、取消還庫存、refresh、修改密碼／跨分頁登出及展示登入權限。
- 真實驗證使用唯一隔離資料庫 `catshop_it_c2ba8f2595094c85aaf49b163dd4c5f4`，結束已清除資料庫及測試帳號；未操作展示資料庫。
- 本機 SQL Server Express 17.x 實測；規格 SQL Server 2022 仍須在目標環境重跑。

執行方式：在 frontend 執行 `npm run test:cart-state`、`npm run test:stability`（需獨立 combined 測試站 5175）、`npm run test:admin-filters`、`npm run build`；在 backend 執行 `scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser`，瀏覽器通道為 msedge。

第一次隔離 SQL 驗證受限於執行環境的 Windows／ODBC 認證，未能連線；取得本機連線執行權限後重新驗證成功，沒有為此變更安全設定或應用程式。

## 限制與交付狀態

- 佇列僅協調同一個前端 Store，不提供跨分頁／跨裝置購物車即時同步。後端列鎖仍負責伺服器端一致性。
- reset 及登出會拒絕舊回應，但不能撤銷已送到伺服器的交易。沒有加入網路取消、操作冪等鍵或離線重送。
- 請求排序可能使後續操作等候既有請求；仍使用既有 axios 逾時與錯誤 UI，不增加無限重試。
- 結帳 API 本身沒有加入購物車 Store 佇列；結帳與修改的資料一致性由上一批後端鎖處理。沒有新增整個前端的結帳／加購全域互斥。
- 展示後端沒有重啟，原資料保留；先前後台篩選及後端購物車鎖仍需經使用者同意重啟才會套用。前端開發站可透過熱更新載入本批變更。
- ZIP 包含前幾批完整原始碼，不含 node_modules、target、dist、uploads 或 Git 資料。未提交、未上傳，桌面原專案未修改。

建議下一批處理展示資料持久化，讓後續重啟更新可以保留會員、訂單與商品修改。
