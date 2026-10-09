# 第四批：前端穩定性補齊

## 範圍

沿用前三批的後端、資料表、API 與付款規則。本批補上前端錯誤處理及登入狀態競態防護；未修改桌面原專案，未提交或上傳。

## 已實作

- 結帳、購物車、會員頁載入失敗時顯示錯誤及重試，不將失敗當成空資料；結帳地址讀取失敗可手填並重試地址。
- 登入、註冊、會員資料、密碼、地址及結帳顯示對應欄位錯誤，支援後端 `errors` 與舊式 `error.details`；加入 aria-invalid、aria-describedby 與錯誤提示。
- 送出、購物車異動及取消訂單加入等待旗標與按鈕停用；取消失敗仍保留訂單內容，允許重試。
- 改密碼成功後清除登入與私人資料，導回登入並顯示重新登入原因；其他分頁同步登出。
- 新增原子登入快照 `catshop_session`，保留既有 token/member 儲存欄位相容性；不同登入世代以 epoch 區分。
- 登出／換帳號後，舊會員、購物車及 refresh 成功回應不能恢復舊登入或私人資料；舊 401 不能清除新登入；重送請求不能跨登入世代使用新帳號權限。
- 私人頁面換帳號時重新建立，登入狀態跨分頁同步；公開商品等查詢仍可正常顯示。
- 同分頁購物車讀取共用進行中的請求，避免頁面與導覽列重複讀取。

## 主要程式依據

以下路徑相對於本專案根目錄：

| 位置 | 作用 |
|---|---|
| `frontend/src/api/errors.js` | 保留 API 狀態、代碼與欄位錯誤 |
| `frontend/src/composables/useFormErrors.js` | 表單錯誤狀態與捕捉 |
| `frontend/src/components/FieldError.vue` | 共用欄位提示 |
| `frontend/src/api/http.js` | 請求世代、refresh 共用／跨分頁鎖、舊回應防護 |
| `frontend/src/stores/auth.js` | 完整登入快照、storage 同步、登出與登入世代 |
| `frontend/src/stores/cart.js` | 共用讀取與跨登入世代資料防護 |
| `frontend/src/App.vue` | 私人頁面重建、權限變化導頁、私人購物車清除 |
| `frontend/src/views/CheckoutView.vue` | 載入／地址重試、欄位錯誤、防連點 |
| `frontend/src/views/AccountView.vue` | 個人資料／地址錯誤、改密碼導頁與等待旗標 |
| `frontend/src/views/CartView.vue` | 載入重試、數量驗證與異動等待旗標 |
| `frontend/src/views/OrderDetailView.vue` | 路由切換載入防競態、取消失敗可重試 |
| `frontend/src/views/LoginView.vue`、`RegisterView.vue` | 欄位提示、防連點、重新登入原因 |
| `frontend/scripts/stability.cjs` | 11 個可重現的錯誤／競態瀏覽器情境 |
| `frontend/scripts/e2e.cjs` | 真實 API 改密碼、跨分頁登出及新密碼登入 |

## 驗證方式與邊界

1. 啟動前端於 127.0.0.1:5175，再執行 `npm run test:stability`：11 個情境（載入重試、欄位錯誤、防連點、改密碼跨分頁、晚到回應、舊 401、取消重試、換帳號、晚到 refresh／購物車）；此組使用替身 API，不能取代真實後端驗證。
2. `npm run test:smoke`：分類、搜尋、商品資訊、後台新增商品、COD 出貨、分頁、付款選項及手機版面。
3. `npm run build`：正式建置。
4. 後端 `scripts/verify-app-mssql.ps1 -AllowOtherVersion -Browser`：34 個單元／H2 測試、1 個 SQL Server 整合測試；真實 Edge 執行註冊、HttpOnly refresh cookie、購物車重載、MOCK 結帳、扣庫存、取消回補、refresh、改密碼跨分頁登出、新密碼登入、登出撤銷。使用唯一命名的暫存資料庫與專用 SQL 帳號，結束後清除。

本機是 SQL Server Express 17.x，不是規格的 SQL Server 2022；2022 尚待目標環境驗收。最新實際結果見交付的 frontend-stability-verification.md。

## 尚未處理／限制

- 本批防連點屬前端保護，不等同後端訂單冪等鍵；跨裝置重複送單仍需另訂設計。
- Access JWT 仍存在 localStorage，未消除 XSS 風險；此批未改成完整 BFF／cookie-only 架構。
- 跨分頁 refresh 依賴瀏覽器 Web Locks；不支援時只有同分頁共用，不能宣稱所有瀏覽器的跨分頁輪替均有鎖。
- 離線登出先清本地資料，無法保證伺服器撤銷成功；伺服器端仍受既有 session／token 規則約束。
- 訪客結帳、後端模組分層與 N+1 查詢優化、過期 AuthSession 清理、孤兒圖片清理／物件儲存、多節點限流及 SQL Server 2022 驗收仍未完成。
- 真實支付、物流、優惠券、評論、收藏不是本批實作範圍。
