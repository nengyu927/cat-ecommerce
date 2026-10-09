# 前後台獨立入口

本機已拆成兩個獨立網站入口，不再把後台塞在前台頁面或導覽列。

| 網站 | 本機網址 | 畫面 |
|---|---|---|
| 前台 | http://127.0.0.1:5173/ | 首頁、商品、分類、會員、購物車、結帳、我的訂單 |
| 後台 | http://127.0.0.1:5174/ | 管理員登入；商品、分類與訂單管理 |

前台登入：http://127.0.0.1:5173/login。
後台登入：http://127.0.0.1:5174/login。

前台不註冊後台路由、不顯示後台入口；後台使用獨立 AdminApp 版面，不顯示商城導覽列、購物車、會員註冊或結帳。後台登入後預設進入 `/products`，選單可切換 `/categories`、`/orders`。伺服器仍強制 ADMIN 權限，不以隱藏畫面代替授權。

目前共用 Vue 原始碼與 Spring Boot API，沒有拆成兩個 Git 專案或兩套後端。建置產物已分開為 `frontend/dist/storefront` 與 `frontend/dist/admin`。共用程式碼不代表共用頁面；只會顯示對應入口的路由與版面。

## VS Code 啟動

開啟最新版專案資料夾，使用三個終端機：

後端（Windows 本機 SQL Server，最新持久展示啟動器）：

```powershell
cd backend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-persistent-preview.ps1
```

前台：

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

後台（與前台共用同一份 node_modules，不需重複安裝）：

```powershell
cd frontend
npm.cmd run dev:admin
```

最新展示已切換專用持久資料庫，不是 README 的固定 catshop 資料庫。正常重啟不再清除會員、訂單、商品修改、購物車與收藏；請保存 `.local/preview-state.xml`。詳見 `PERSISTENT_PREVIEW_NOTES.md`。也可在 VS Code 開啟 `cat-ecommerce.code-workspace` 並執行三個獨立工作。不要在目前服務已運行時重複佔用 8080、5173、5174。

## 登入隔離

不同 port 的 localStorage 本來就分開，但 Cookie 不按 port 區分。因此後台 API 改由 `/admin-api` 代理到後端 `/api`，並將 refresh Cookie 的 Path 從 `/api/auth` 改寫成 `/admin-api/auth`；前台仍使用 `/api`。登入、refresh、登出都使用各自對應入口。

已用同一個瀏覽器 context 驗證：會員與管理員同時登入、Cookie 均 HttpOnly 且 Path 不同、各自 refresh 成功、管理員登出後會員仍可 refresh，會員登入後台仍被拒絕。背景 API 沒有 500，未捕捉到頁面錯誤。測試位於 `frontend/scripts/split-apps.cjs`，需先啟動上述三個服務，執行 `npm run test:split`。

此為登入狀態與畫面分離，不是完整安全邊界：仍共用後端、同一瀏覽器同站 Cookie 環境及來源允許設定。正式部署應使用分開的網站／反向代理設定，並評估更嚴格的 admin session、CSRF 與來源政策；不能宣稱此改動防止所有跨站或 XSS 攻擊。

## 建置與部署

```powershell
npm.cmd run build
```

分別建置 storefront 與 admin。正式版本預設關閉一鍵展示登入；僅展示環境可設定 `VITE_DEMO_LOGIN=true` 後重新建置。

本機正式預覽：前台 `npm run preview`，後台 `npm run preview:admin`（先停用同 port 的 dev 服務）。Vite dev／preview 已設定代理及 Cookie Path 改寫。正式靜態託管需要自行設定兩個網站的 history fallback、API proxy 與後台 Set-Cookie Path 改寫，不能直接把兩個 dist 資料夾上傳就宣稱 API 可用。

既有 combined 模式保留給舊版整合／替身回歸測試；不是目前展示入口，也不是上述預設啟動／建置方式。

桌面舊專案未修改，未提交或上傳；VS Code 開啟的是本對話最新版工作副本。
