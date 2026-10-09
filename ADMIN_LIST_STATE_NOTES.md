# M01 第二批：主要 Admin 列表參數與返回統一

日期：2026-10-09。延續前台 LIST_STATE_NOTES.md；M01 仍為部分完成。原始 index.html 規格書仍未取得，本批未修改 Excel 計畫表。

## 本批完成

- 商品、訂單、分類、會員、優惠券列表使用 parseAdminListQuery 共用驗證；page 預設 0、size 預設 20，page 為 0–2147483647、size 為 1–100。
- 拒絕已支援查詢欄位的重複值陣列、null、非法頁碼、超範圍 size、不支援狀態／角色／布林值、過長搜尋字詞／品牌、非正整數或超 Java Long 上限的分類／會員 ID。未支援的其他 query 不作為 API 條件。
- 商品、訂單接受後端現有欄位的明確 asc／desc 排序；其他三種列表維持既有固定排序，帶入 sort 會提示不支援，而非默默忽略。
- 商品排序欄位：createdAt、name、basePrice、status、id；訂單：createdAt、orderNo、totalAmount、status、id。沒有新增排序 API 或 UI 排序選單，合法 sort 可透過網址使用。
- 訂單日期採真實年月日檢查，2026-02-30 等不存在日期不發查詢；開始不可晚於結束。前端日期範圍 0001-01-01 至 9998-12-31，避免後端結束日期加一日上界問題。
- 關鍵字／訂單編號前端上限 200 字元，商品品牌 100；false 啟用狀態保留，不變成未篩選。
- 五種列表 size 由網址帶到 API，換頁／重整／歷史返回保留。商品、訂單重新搜尋保留 size／sort 並重置 page；會員保留 size 並重置 page；清除條件移除 query 並恢復預設。
- adminReturnQuery 統一各模組允許的返回欄位。商品主檔／SKU／圖片、訂單詳情／出貨、分類編輯、會員詳情／歷程及優惠券編輯保留原列表 size；商品／訂單也保留 sort。
- 商品／訂單／分類原本分散的網址解析改為單一 restoreQuery，watch fullPath；卸載後忽略舊列表回應。會員、優惠券沿用讀取序號防護，優惠券改監看完整網址而非只有 page。
- 列表載入時清空舊資料；查詢失敗不顯示假空清單。共用 Admin 列表新增重試入口，優惠券沿用重新載入與清除入口。
- 會員舊 memberId 查詢導向操作紀錄的相容功能保留，編號仍須驗證；historyPage 獨立於原會員列表 page，歷程每頁仍為 10。

## 程式碼依據

| 檔案 | 核心內容 |
|---|---|
| frontend/src/utils/adminListQuery.js | adminListKeys、parseAdminListQuery、dateValue、adminReturnQuery |
| frontend/src/views/AdminView.vue | restoreQuery、fullPath 監看、搜尋保留 size／sort、卸載 loadVersion |
| frontend/src/views/AdminMemberView.vue | 共用查詢、size 保留、memberId 相容導向 |
| frontend/src/views/AdminCouponView.vue | 共用查詢、完整網址監看、編輯／換頁保留 size、失敗不顯示空資料 |
| frontend/src/views/AdminProductView.vue | 商品主檔返回白名單 |
| frontend/src/views/AdminProductAssetView.vue | SKU／圖片返回白名單 |
| frontend/src/views/AdminOrderView.vue | 訂單／出貨返回白名單 |
| frontend/src/views/AdminCategoryView.vue | 分類新增／編輯返回 size |
| frontend/src/views/AdminMemberDetailView.vue | 會員詳情／紀錄返回 size |
| frontend/src/views/AdminCouponEditView.vue | 優惠券新增／編輯返回 size |
| frontend/scripts/admin-query.mjs | 純解析邊界驗證 68 項 |
| frontend/scripts/admin-list-state.cjs | 五種列表重整／歷史／詳情返回、非法 query 不發 API、優惠券失敗重試 |

## 本次驗證

- 前台與 Admin 正式建置皆通過。
- 新增參數解析測試：68 項斷言通過；新增 Admin 列表瀏覽器測試：7 組通過。
- 既有 test:admin-filters、test:product-routes、test:product-assets、test:category-routes、test:coupons、test:admin-members、test:shipments、test:list-state 全數通過。
- 更新三處既有測試的錯誤文案選取，對應新的具體參數錯誤，仍保留非法查詢不發 API 與清除後復原斷言。
- 瀏覽器測試使用本機 Vite、隔離瀏覽器與模擬 API，非真實 SQL Server 測試。本批未更動後端，未重跑後端／SQL 整合測試；上一批後端結果不是本批新驗證。

## 尚待完成

1. 低庫存與庫存異動列表的 URL 狀態、非法參數、返回與歷史動線。
2. 後端 Admin 商品／訂單／分類的分頁上限及排序格式規則統一；目前前端 1–100 限制不是後端安全保障，不宣稱直接 API 有相同保護。
3. 後端搜尋長度、重複參數與排序穩定次序的規則對照及 SQL Server 實測；不以本批前端白名單代替後端驗證。
4. Admin 超出末頁的網址修正、頁碼／size 操作介面與會員 historyPage 共用化，可於後續批次評估，尚未宣稱完成。
5. 最後彙整 M01 狀態及更新規格計畫表。原始規格書仍需取得才能完整對照。

## 交付與資料保留

本機工作區與 ZIP 交付；未提交 Git、未上傳、未重啟展示服務、未修改 Desktop 原始專案或展示資料。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌，不含私有資料庫設定或實際上傳內容。
