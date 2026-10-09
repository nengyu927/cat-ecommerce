# M01 第七批：分類／搜尋路由與商品返回

日期：2026-10-09。延續 SEARCH_RULE_NOTES.md。原始 index.html 尚未取得，本批不修改 Excel 計畫表，也不宣稱 M01 或原規格全部完成。

## 完成內容

- 在 /category/:slug 內套用品牌、關鍵字、價格、現貨或排序，若仍選擇原分類，維持具名 category-products 路由與 slug。頁碼重置為 0，保留每頁筆數；分類名稱仍作為頁面標題。
- 分類 API 成功後保存 routeCategoryId，用字串比較選單所選 ID，避免數字／字串型別不同誤判；失敗或尚未解析不當成有效分類。
- 切換成其他分類或全部分類時，沿用既有一般商品／搜尋路由，API 使用新的 categoryId；不強制猜測新分類 slug。
- 清除條件仍代表清除所有條件、回到 /products，未更改既有語意。
- 商品詳情與相關商品既有 returnTo 保留規則不變；新增測試驗證分類／搜尋的關鍵字、品牌、價格、現貨條件、頁碼、筆數、排序與 hash 都能返回原來源。
- 新增 test:catalog-routes，分別啟動 storefront 與 development（combined）模式，先確認實際 VITE_APP_SURFACE，再跑相同 5 組案例。
- 更新既有商品篩選測試：改驗證分類 slug 保留、頁面標題不變及實際 API categoryId，而非期待篩選後跳離分類頁。

## 測試範圍

| 每個模式的案例 | 斷言 |
|---|---|
| 分類篩選與換頁 | 品牌／排序／關鍵字不離開分類 slug，換頁與重新整理保留條件，切換分類實際查詢新 ID |
| 分類來源詳情／相關商品／返回 | 詳情重新整理後仍可經相關商品返回完整分類來源，包含 hash、size、page 與零元價格 |
| 搜尋來源詳情／相關商品／返回 | 完整搜尋 URL 保留，不退回全部商品或遺失條件 |
| 分類／搜尋超界末頁 | page=99 成功校正至有效末頁，保留具名路由、查詢與 hash |
| 不存在分類 | 顯示 404 錯誤且不發商品列表 API；清除條件可恢復全部商品 |

inStock=false 在來源 URL 保留；product store 仍省略 false 的 API 參數，使用後端既有預設 false。未為了測試改變 API 契約。

## 主要檔案

- frontend/src/views/ProductListView.vue：routeCategoryId、分類解析及 applyFilters 具名路由分支。
- frontend/scripts/catalog-routes.cjs：兩種模式共 10 組案例，含手機 390px 返回列表無整頁橫向溢出及無未處理頁面錯誤。
- frontend/scripts/product-filters.cjs：分類篩選回歸更新。
- frontend/package.json：test:catalog-routes 指令。
- frontend/src/components/ProductCard.vue、views/ProductDetailView.vue、utils/listQuery.js：既有來源與安全返回機制，本批僅驗證，未更動。

## 驗證

- test:catalog-routes：兩種模式共 10 組通過；新增模式環境斷言後完整重跑亦通過。
- test:product-filters：7 組通過。
- test:list-state：8 組通過，保留收藏／訂單／商品的既有來源返回與安全返回回歸。
- test:pagination-ui：12 組通過，包含 Admin 分頁介面。
- 前台與 Admin 正式建置通過；共 37 組瀏覽器案例通過。
- 新測試首次將 inStock=false 誤認為 API 必須送 false；檢查 store 與後端預設後，改為同時斷言來源 URL 保留 false、API 省略參數。未刪除條件保留檢查，未修改 API。

本批只有前端變動，使用隔離瀏覽器與模擬 API；未重跑 SQL Server 或後端測試，不將上一批 SQL Server 21 項／後端 142 項當成本批新驗證。

## 後續順序

1. 依既有規則表確認 API 與 URL 數字格式、大小寫相容及未知 query 策略，避免默默改變既有使用者的 API 契約。
2. 擴充真實 SQL Server 下的分類／搜尋來源返回驗證，尤其商品下架或刪除造成來源末頁縮減的情境。
3. 取得原始規格書並更新計畫表，再進行 M01 整體驗收與其他功能開發。

## 交付界線

只更新工作區及建立本機 ZIP；未提交 Git、未上傳、未重啟展示網站、未修改 Desktop 原始專案或展示持久資料。本批未建立或刪除 SQL 資料庫。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。
