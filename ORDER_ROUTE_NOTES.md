# 計畫表 R01／R02：Router 分模組與 Admin 訂單拆頁

更新日期：2026-10-08。本批只修改前端與測試／文件，未修改後端、API 契約、Flyway 或展示資料庫。

## 計畫對照

| 項目 | 本批結果 | 尚未涵蓋 |
|---|---|---|
| R01 Router 基礎整理 | 前台與 Admin 路由分成獨立模組，共用入口保留登入／Admin 權限檢查；正式兩站仍分開 | 後續各業務編輯頁依 R03～R09 逐批拆分 |
| R02 訂單詳情／出貨 | 已完成獨立網址、直開／重整、出貨成功回詳情、舊後台網址相容 | 不新增物流即時追蹤、部分出貨或狀態歷史 |
| M01 查詢與返回 | 已完成 Admin 訂單列表篩選／頁碼網址同步與詳情往返保留 | 其他管理列表待各模組拆頁時補上 |
| M02 未儲存／錯誤 | 已完成出貨表單離開確認、瀏覽器離開提醒、重試確認、操作防連點及慢回應防護 | 其他表單尚未全面套用 |
| M03 手機版 | 新訂單頁表格區內捲動，Admin 主選單可換行；390px 測試通過 | 不代表全站所有頁面已完成手機版驗收 |

Excel 計畫表本批未改寫。原始 `/mnt/data/index.html` 規格書仍未取得，本批是依最近整理的計畫表工作，不宣稱原始規格全數完成。

## 網址與操作

- 前台維持 `http://127.0.0.1:5173`。
- Admin 訂單列表：`http://127.0.0.1:5174/orders`。
- Admin 訂單詳情：`http://127.0.0.1:5174/orders/:id`。
- Admin 出貨登錄：`http://127.0.0.1:5174/orders/:id/shipment`。
- 列表篩選 `orderNo/status/memberId/from/to/page` 會保留於網址；詳情與出貨頁返回列表仍帶原條件。`page` 與 API 相同，從 0 起算。
- 未登入／一般會員不能進入 Admin 操作頁；錯誤訂單 ID 不送 API，找不到資料顯示錯誤與重新載入。
- 出貨表單有內容時離開會確認；重新載入會確認清除未儲存內容。409 出貨衝突顯示錯誤，保留填寫內容供修正，不假裝成功。
- 出貨仍走既有專用 POST，沒有恢復以一般狀態 PATCH 直接改 SHIPPED 的方式；PAID 或 PENDING＋COD 才顯示登錄入口。已取消／已出貨訂單不可重複出貨。
- 人工出貨紀錄仍非物流即時追蹤，也不補造舊訂單紀錄。
- 測試用 combined 模式仍支援 `/admin/orders/:id` 與 `/admin/orders/:id/shipment`；正式 Admin 的舊 `/admin/...` 網址轉到新的 `/...` 並保留查詢條件。

## 主要檔案依據

| 檔案 | 變更 |
|---|---|
| `frontend/src/router/index.js` | 共用登入守衛、依應用模式載入路由 |
| `frontend/src/router/storefront.js` | 前台專用路由 |
| `frontend/src/router/admin.js` | Admin 路由、訂單詳情／出貨與相容轉址 |
| `frontend/src/views/AdminView.vue` | 訂單詳情改 RouterLink，移除內嵌出貨表單，查詢 URL 同步與驗證 |
| `frontend/src/views/AdminOrderView.vue` | 獨立訂單／出貨頁，請求序號防舊回應覆蓋、未儲存提醒與錯誤重試 |
| `frontend/src/AdminApp.vue` | 新頁面標題與訂單主選單選取狀態 |
| `frontend/src/assets/main.css` | 手機 Admin 主選單換行，避免頁面橫向溢出 |
| `frontend/scripts/shipments.cjs` | 12 個前後台訂單／出貨瀏覽器情境 |
| `frontend/scripts/smoke.cjs` | 調整既有核心回歸流程，使用新訂單詳情／出貨入口 |

## 本批驗證

- `npm run test:shipments`：12 項通過，涵蓋出貨成功／衝突、會員紀錄、前後台慢回應、直接網址與重整、篩選返回與歷史導覽、未儲存阻擋／允許離開、舊網址、不可出貨狀態、錯誤 ID／404 重試、一般會員拒絕、非法列表查詢，以及手機寬度。
- `npm run test:admin-filters`：通過，既有商品／分類／訂單篩選及競態防護保留。
- `npm run test:coupons`：5 項通過，優惠碼折抵、過期回應、拒絕訊息、Admin 新增／修改與版本衝突。
- `npm run test:smoke`：通過，商品分類／搜尋、商品新增、人工出貨、會員訂單分頁、付款選擇與手機結帳。
- `npm run build`：前台／Admin 正式建置通過。

以上瀏覽器回歸使用攔截的測試 API，不寫展示資料。本批未重新執行後端單元／真實 SQL Server 整合測試，也沒有重啟後端或清空資料。正式部署至非 Vite 環境時仍需設定 history fallback，讓深層網址回到各站 index.html。

## 下一批

R03：Admin 商品新增／編輯拆頁，規劃 `/products/new` 與 `/products/:id/edit`，沿用商品 API；補上直接網址、未儲存提醒、讀取失敗／衝突處理及返回列表条件保留。R04 的 SKU／圖片獨立操作接續處理，不提前宣稱完成。
