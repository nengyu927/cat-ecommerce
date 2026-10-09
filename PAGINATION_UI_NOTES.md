# M01 第五批：分頁操作介面與 Admin 排序

日期：2026-10-09。延續 QUERY_GUARD_NOTES.md。M01 仍未全部完成；原始 index.html 規格書尚未取得，本批未修改 Excel 計畫表。

## 完成內容

- 新增 ListPagination.vue，共用每頁筆數選擇、上一頁／下一頁、目前頁數與直接跳頁。套用前台商品／訂單／收藏，以及 Admin 商品／訂單／分類／會員／優惠券／低庫存／庫存異動與會員歷程。
- 每頁筆數常用選項為 10、12、20、50、100；若網址已有合法的其他大小（1–100），也顯示該值，不將既有 size=1 等網址強制改寫。
- 切換筆數重置相對應頁碼為 0；保留已套用篩選、排序、路由 params、來源 returnTo 及 hash。不修改預設：商品／收藏 12、訂單及主要 Admin 列表 20、會員歷程 10。
- 介面頁碼從 1 起算，API／網址仍從 0 起算。跳頁只能輸入目前總頁數內的正整數；0、負數、小數、指數格式及文字顯示錯誤，不改網址、不發列表 API。
- 讀取中停用分頁操作；沒有任何頁面時停用跳頁與前後頁，仍可切換每頁筆數。網址直開仍由各列表驗證，非法 query 不會被元件默默當成有效查詢。
- 成功換頁／重整後更新頁碼輸入與選單，使用 route.name／params 保留分類等具名路由；分頁操作只修改已套用的 URL，不提交未送出的篩選草稿。
- 會員歷程使用 historyPage／historySize，操作名稱保留「紀錄上一頁／下一頁」，與原會員列表 page／size 相互獨立。
- Admin 商品／訂單新增「列表排序」選單，涵蓋後端已允許的 5 種欄位及升降冪共 10 個選項。商品欄位為 createdAt／name／basePrice／status／id；訂單為 createdAt／orderNo／totalAmount／status／id。
- 排序切換沿用 URL 已套用的條件、size 與返回來源，重置 page=0，不把尚未搜尋的品牌或訂單編號寫進查詢。分類／會員／優惠券／庫存仍維持固定排序，沒有新增不支援的排序 API。
- 前台 ProductListView 接上 emptyPageReplacement：商品／搜尋／分類共用畫面在成功查詢空的超界頁時 replace 到有效末頁；零頁回 0，保留原查詢。失敗或舊回應不冒充校正成功。
- product store 的 fetchProducts 成功且 requestVersion 仍有效時返回當次 PageResult，供 View 判定末頁；舊回應仍不更新 store／不返回可校正資料。
- 移除各 View 已不使用的舊分頁 click handler，保留原讀取／寫入流程；優惠券「重新載入」入口仍存在。共用元件提供可讀標籤、錯誤提示及手機換行布局。

## 檔案與程式碼依據

| 檔案 | 依據 |
|---|---|
| frontend/src/components/ListPagination.vue | sizes、navigate、changeSize、jump、pageKey／sizeKey、行動版 CSS |
| frontend/src/views/ProductListView.vue | 共用元件與商品末頁校正 |
| frontend/src/stores/product.js | 成功有效回應 return data，仍有 requestVersion 防護 |
| frontend/src/views/OrderListView.vue、WishlistView.vue | 前台訂單／收藏共用分頁 |
| frontend/src/views/AdminView.vue | 商品／訂單／分類分頁與 sortOptions／changeSort |
| frontend/src/views/AdminMemberView.vue、AdminCouponView.vue | 會員／優惠券共用分頁 |
| frontend/src/views/LowStockView.vue、StockMovementView.vue | 庫存共用分頁 |
| frontend/src/views/AdminMemberDetailView.vue | historyPage／historySize 專用分頁 |
| frontend/scripts/pagination-ui.cjs | 11 種列表／歷程介面與空商品列表共 12 組案例 |
| frontend/scripts/page-bounds.cjs | 歷程翻頁後明確等待回應，避免把上一個請求計入下一組非法 query |

## 本次驗證

- 前台與 Admin 正式建置通過。
- test:pagination-ui 12 組通過：筆數切換／頁碼跳轉、非法輸入不發 API、重整、保留條件／來源、排序不提交草稿、390px 無整頁溢出；空商品列表校正至 0 並停用跳頁。
- test:admin-query 91 項純參數斷言通過；test:page-bounds、test:product-filters、test:list-state、test:admin-members、test:admin-list-state、test:low-stock、test:stock-movements、test:coupons、test:admin-filters 回歸通過。
- 測試改為等候實際讀取完成，再檢查非法操作沒有新請求，避免分類選單或先前歷程請求造成誤判；空列表使用含圖示的提示區定位，保留頁面錯誤與 API 數量斷言。
- 本批只更動前端，測試用本機 Vite／隔離 Edge 與模擬 API；未重跑真實 SQL Server／後端測試，也不以先前批次 140／20 的結果當成本批新驗證。

## 剩餘順序

1. 前後端搜尋字詞／品牌長度及 query 規則對照，補尚未一致的後端規則和 HTTP 測試。
2. combined 路由模式的返回與分頁操作回歸，補前台分類／搜尋頁和實際 SQL Server 的新操作流程驗證。
3. 彙整規則表、取得原始規格書並更新計畫表，才判定 M01 是否完成。跳頁目前為輸入框，不包含首末頁快捷鈕、頁碼列或游標式分頁。

## 交付

只修改工作區並建立本機 ZIP；未提交 Git、未上傳、未重啟展示服務、未修改 Desktop 原始專案或展示資料。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌，不含展示設定或實際上傳檔。
