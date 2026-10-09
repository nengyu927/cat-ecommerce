# 計畫表 R05：Admin 分類新增／編輯獨立頁

日期：2026-10-08。依規格計畫表 R05 實作，不新增商業功能或重設展示資料。原始 index.html 規格書仍未取得，不宣稱原始規格全數完成。本批未改寫 Excel 計畫表。

## 完成內容

- 新增 /categories/new、/categories/:id/edit。列表「新增／編輯」改導向獨立頁，不再於列表下方展開表單。
- 直接網址或重整，透過既有 Admin 分類分頁 API 載入完整分類清單，再依 ID 取得編輯資料；不依賴先前列表的記憶體資料。
- 上層選單排除自己與所有子孫，包括位在後續 API 分頁或已停用的子孫；遍歷使用已訪問集合，避免遇到既有不良階層時無窮迴圈。
- 儲存仍走既有分類建立／更新 API，由後端交易鎖與 validateParent 再檢查最新階層。讀取後被別頁重新設定階層的情境，不以畫面選單作為唯一防線。
- 新增成功改到新分類編輯網址；更新成功標記為已儲存，提交只含 name/slug/parentId/sortOrder/isActive，不夾帶 id 或 productCount。
- 名稱最多 100 字、slug 最多 120 字且維持小寫英數與連字號；排序沿用後端 Integer 範圍，允許負數，不擅自新增非負限制。
- 未儲存離開／瀏覽器離開提醒，儲存中防重送與阻擋導覽；錯誤保留草稿，重新載入清除草稿須確認。
- 非法 ID 不送 API；找不到分類或任一階層分頁失敗時不顯示舊表單，可重試。
- 分類列表 keyword/isActive/page 同步網址，返回列表與瀏覽器前後頁保留已套用條件；false 不遺失。列表只載入當頁，完整階層移至編輯頁載入。
- 正式 Admin 的舊 /admin/categories/new 與 /admin/categories/:id/edit 相容轉址並保留 query/hash；新增／編輯頁仍需 Admin 權限。
- 手機 390px 分類編輯頁無整頁橫向溢出。

## 網址

| 用途 | 網址 |
|---|---|
| 分類列表 | http://127.0.0.1:5174/categories |
| 新增分類 | http://127.0.0.1:5174/categories/new |
| 編輯分類 | http://127.0.0.1:5174/categories/:id/edit |

前台 /categories 分類瀏覽與 5173 網址保持不變。切換離開確認代表放棄該頁未提交草稿，不是自動儲存。

## 主要檔案依據

| 檔案 | 內容 |
|---|---|
| frontend/src/views/AdminCategoryView.vue | 完整分頁階層、子孫排除、依 ID 載入、DTO 白名單、未儲存／beforeunload、讀取序號防舊回應、重試與儲存 |
| frontend/src/views/AdminView.vue | 移除分類內嵌表單，改為 Router 導覽，分類查詢網址同步及合法頁碼／布林值檢查 |
| frontend/src/router/admin.js | 分類新增／編輯路由、權限 meta、舊址轉換 |
| frontend/src/AdminApp.vue | 頁面標題與分類主選單選取狀態 |
| frontend/scripts/category-routes.cjs | 11 項分類拆頁測試 |
| frontend/scripts/category-hierarchy.cjs | 舊階層防護測試改用獨立頁返回導覽 |
| frontend/scripts/admin-filters.cjs | 驗證分類列表只查分頁，商品分類選單仍讀無篩選清單 |
| frontend/scripts/e2e.cjs | 真實隔離資料庫分類編輯直開／重整／返回列表 |
| frontend/package.json | 新增 test:category-routes |

後端 AdminCategoryService、Controller、Repository、DTO、Entity、Security 與 Flyway 均未修改；沿用現有 create/update/deactivate 與階層交易鎖。本批沒有新增 GET 單筆分類端點。

## 本批驗證

- npm run test:category-routes：11 項通過。直開／重整、跨頁停用子孫、手機、新增後轉編輯、主資料白名單、衝突草稿／重試確認、離開確認、篩選與瀏覽歷史、非法 ID／找不到分類／讀取失敗、慢回應、舊址、一般會員拒絕、非法查詢與空分類清單。
- npm run test:category-hierarchy：通過，既有子孫排除及衝突表單保留。
- npm run test:admin-filters：通過。
- npm run test:product-routes：10 項通過。
- npm run test:product-assets：11 項通過。
- npm run build：前台與 Admin 通過。
- backend/scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser：BUILD SUCCESS，133 項後端測試、17 項真實 SQL 整合測試及真實瀏覽器流程通過；新增分類直開／重整／返回的瀏覽器回歸。

SQL 實測環境為本機 Express 17.x，不是規格指定的 SQL Server 2022；2022 仍需在目標環境重跑。本次唯一命名的 catshop_it_* 測試資料庫／登入已清理，未修改或清空展示資料。

## 限制與下一步

- 分類表單沒有 version：後端階層鎖防止循環與並行互移，不代表兩人同時改名稱／排序已具舊版本衝突提醒。
- 完整分類階層讀取仍是依序分頁，不是單一原子快照；儲存一定依後端最新狀態檢查。大量分類的專用輕量階層 API 可另行規劃。
- 沿用既有啟用／停用語意；本批不重新設計停用傳播、商品可見性或分類刪除。
- 檢查時 5173／5174／8080 未運行，本批未啟動展示服務。持久化啟動方式見 PERSISTENT_PREVIEW_NOTES.md。
- 下一批 R06：優惠券新增／編輯獨立頁與 GET 單筆 DTO，保留 version／409、優惠碼不可更名、UTC 時間轉換與舊訂單折扣快照。

未 Git 提交或上傳。更新 ZIP 排除 .local、uploads、node_modules、target、dist、日誌與資料庫機密。
