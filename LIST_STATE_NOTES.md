# 計畫表 M01 第一批：前台列表狀態與詳情返回

日期：2026-10-09。延續 R01–R09；本批只完成 M01 的前台部分，不宣稱全部 M01 完成。原始 index.html 規格書仍未取得，本批未修改 Excel 計畫表。

## 完成範圍

- 我的訂單與收藏列表以 URL page／size 作為分頁狀態，支援重整、直開、上一頁／下一頁與瀏覽器返回。page 從 0 起算，size 為 1–100；訂單前端預設 20、收藏預設 12。
- 訂單詳情新增返回列表入口，保留原頁碼與每頁筆數。商品卡帶入來源列表，商品詳情及相關商品保留商品／搜尋／分類／收藏來源網址與既有查詢條件。
- returnTo 僅接受本機路由中的允許列表名稱，拒絕外部網址、協定相對網址、反斜線、換行與其他模組／詳情頁；無合法來源則返回預設商品或訂單列表，不做任意網址跳轉。
- 共用 query 驗證拒絕重複參數陣列、非整數／負數／超範圍頁碼、非法 size、排序白名單之外的值與非正整數分類 ID。分類 ID 以字串保留並限制在 Java Long 正整數範圍。
- 商品列表沿用關鍵字、分類、品牌、價格、現貨條件；新增 size 驗證，允許既有後端支援的建立時間／售價／名稱正反向排序。basePrice 排序別名正規化為 price；UI 增加最早商品、名稱反向排序選項。
- 前端關鍵字上限 200 字元、品牌上限 100；價格沿用 0–99999999.99 與最多兩位小數驗證。沒有新增訂單狀態篩選或收藏排序 API。
- 套用商品條件保留 size 並重置 page；清除條件回到無 query 商品列表，能移除非法網址參數。
- 訂單／收藏讀取失敗不顯示假空列表或舊資料，提供重試與清除查詢。若頁碼超出現有訂單／收藏總頁數，直接 replace 到有效末頁，避免逐頁退回；收藏刪掉末頁最後一筆也會調整。
- 訂單／收藏／商品詳情的舊讀取回應不能覆蓋較新的頁面資料；商品列表沿用並加強既有 store requestVersion 防護。訂單表格行動版橫向捲動限制在卡片內。
- 後端本人訂單列表新增 page 不可為負數、size 1–100 防護。沒有更動 Entity、資料庫結構、JWT、付款或物流契約。

## 主要檔案與依據

| 檔案 | 依據 |
|---|---|
| frontend/src/utils/listQuery.js | queryInteger、queryChoice、queryId、safeListReturn 共用驗證 |
| frontend/src/views/OrderListView.vue | watch(route.fullPath)、分頁 push、越界 replace、讀取序號 |
| frontend/src/views/WishlistView.vue | page／size 驗證、收藏異動後重讀及末頁校正 |
| frontend/src/views/ProductListView.vue | query 白名單、清空舊結果、size 保留與清除 |
| frontend/src/components/ProductCard.vue | productTarget 加入來源列表 returnTo |
| frontend/src/views/ProductDetailView.vue | 安全 returnTarget、相關商品沿用來源、loadSequence |
| frontend/src/views/OrderDetailView.vue | 僅允許 orders 來源的返回入口 |
| backend/src/main/java/com/catshop/service/OrderService.java | listMyOrders 分頁範圍檢查 |
| backend/src/test/java/com/catshop/CheckoutIntegrationTest.java | memberOrderPagingIsBounded 非法分頁與合法請求測試 |
| frontend/scripts/list-state.cjs | 新增 8 組網址狀態／返回／失敗／慢回應案例 |
| frontend/scripts/e2e.cjs | 真實訂單列表 size=1 → 詳情 → 返回保留查詢 |

## 驗證結果

- 前台與 Admin 正式建置通過。
- 新增列表狀態瀏覽器測試 8 組通過：訂單重整／歷史／行動版、收藏詳情／相關商品返回、末頁刪除、商品條件返回、非法參數、錯誤返回目標、失敗重試、慢回應防護。
- 商品篩選既有測試 7 組、前端穩定性 12 組、收藏狀態 6 組皆通過。
- 後端測試報告共 134 個，失敗／錯誤皆為 0；其中真實 SQL Server 列表整合測試 18 個。
- 真實 SQL Server + Edge 購物流程通過，包含本批訂單詳情返回檢查。使用隔離測試資料庫，測試後移除該測試庫與測試登入，不重設展示資料。
- 瀏覽器測試在沙箱內啟動 Edge 遭限制，改在已核准的沙箱外執行通過；模擬 API 測試不等於真實後端測試，兩者分開驗證。
- 本機 SQL Server 為 Express 17.x，透過既有 AllowOtherVersion 驗證入口執行；不宣稱已在 SQL Server 2022 驗證。

## 尚待實作／下一批順序

1. M01 第二批：盤點並統一 Admin 商品、訂單、分類、優惠券、會員的 page／size／sort／filter 驗證，保留已有拆頁返回功能，補非法與重複網址參數案例。
2. 接續低庫存與庫存異動列表 URL 狀態及安全返回，確認重新查詢、重整與歷史返回語意一致。
3. 彙整前後台查詢規則與 API 對照，再更新計畫表狀態；目前 M01 仍為部分完成。

## 交付／資料

只在工作區修改並建立本機交付 ZIP；未提交 Git、未上傳、未改 Desktop 原始專案、未重啟或套用展示網站。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌，不包含展示資料庫設定與實際上傳檔。
