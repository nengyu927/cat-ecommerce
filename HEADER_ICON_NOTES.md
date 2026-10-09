# 前台頁首小圖示調整

2026-10-08，依使用者三則畫面標註修改。

- 會員姓名按鈕改成人頭線條 SVG；連結仍為 `/account`，保留姓名的 accessible name 及「會員中心：姓名」滑鼠提示。
- 購物車文字改為購物車線條 SVG，連結仍為 `/cart`；保留數量 badge，無障礙名稱也包含件數。
- 右上操作區改為紅色 `#b73535`，SVG 為 20px，桌機圖示區 34px、手機 36px，登出按鈕高度 32px。未登入的登入／註冊亦沿用同組紅色。
- CSS 全部限定 `.storefront-shell`，未修改 Admin；不更改登入、購物車、會員 API 或資料。

修改檔案：`frontend/src/App.vue`、`frontend/src/assets/main.css`、`frontend/scripts/stability.cjs`。

驗證：前台／Admin 正式建置通過；12 個畫面穩定性案例通過，其中新增圖示、顏色、尺寸、badge、會員／購物車導覽及手機無整頁橫向溢出測試。另目視確認 898px 與 390px 的真實會員頁。

開發站已熱更新，不需重啟後端；展示資料保留。未提交、未上傳；桌面原專案未修改。完整 ZIP 包含前幾批原始碼，原 ZIP 保留不覆蓋。
