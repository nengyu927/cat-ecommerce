# M01 第十批：商品頁購物操作錯誤分離

日期：2026-10-09。延續 CATALOG_ASYNC_NOTES.md。原始 index.html 尚未取得，未修改 Excel 計畫表；M01 不在本批直接標記全部完成。

## 完成內容

- 主體讀取 error 與加入購物車 actionError 分開；數量錯誤或 API 409 不再隱藏商品詳情／推薦區，不要求重新載入商品才能更正數量。
- 操作錯誤顯示在數量與加入購物車按鈕附近，具 role=alert；成功訊息具 role=status。
- 改變規格或數量即清除舊操作錯誤與成功訊息；下一次送出先清除前次訊息。失敗後保留當次規格與數量，使用者可自行調整或重試，不自動重送寫入。
- 送出中停用規格、數量與按鈕，addToCart 另有 adding 防重入，避免單次操作期間更改送出值。
- 以 loadSequence + addSequence 保護 UI：切換商品使舊操作失效並恢復新商品操作；舊成功／失敗／finally 不得改寫新商品訊息或忙碌狀態。
- 已送出的請求不取消或假稱撤回。購物車 store 原有序列化／登入 epoch 防護保持不變，舊商品已成功加入時仍正常更新購物車總件數，只不把成功訊息帶到新商品。
- 新增規格／數量可讀標籤，方便鍵盤與自動化定位；不修改購物車 API、後端業務、庫存扣減或登入規則。

## 測試

新增 test:product-cart-ui，storefront／combined 各 4 組：

1. 0、超庫存、小數與空數量顯示操作錯誤、不發 POST，商品保留；修正後能成功。
2. API 409 後商品、SKU 與數量保持，原加入按鈕可重試，不顯示主體讀取重試。
3. 成功請求未完成就切換商品：原操作控制項停用；新商品可操作，舊成功訊息不出現於新商品，購物車總件數仍正確。
4. 失敗請求未完成就切換商品：舊錯誤不影響新商品，數量仍為新商品預設值。

延遲回應使用明確閘門與真實 Pinia 購物車佇列讀取同步，不用固定睡眠推測完成。初次測試假設 header 必定重新 GET，等待逾時；改為等候實際 store.fetch() 佇列，再檢查訊息、件數及只有一次 POST，沒有放寬核心斷言。

## 檔案依據

- frontend/src/views/ProductDetailView.vue：actionError、addSequence、current、輸入變動 watch、操作中 disabled。
- frontend/scripts/product-cart-ui.cjs：數量驗證、重試、延遲寫入與總件數斷言。
- frontend/package.json：test:product-cart-ui。
- frontend/src/stores/cart.js：原有 enqueue／epoch，同步測試使用實際 store；本批未修改。

## 驗證結果

- test:product-cart-ui 8 組、test:cart-state 8 組、test:catalog-routes 24 組，共 40 項測試通過。
- 前台與 Admin 正式建置通過。
- 本批僅前端變動、使用隔離 Edge 和模擬 API，未重新跑後端／SQL Server；不將舊批次結果當成本批新驗證。

## 下一步

改善購物車頁的重新同步流程：遇到庫存／價格變動或操作失敗，提供手動重新讀取入口，成功時同步商品數量、庫存與金額；讀取失敗保持原資料並提供清楚重試狀態。再補失敗與恢復測試。

之後再確認查詢格式相容策略、取得原始規格書與更新計畫表，才完成 M01 整體驗收。

## 交付界線

只修改工作區並建立本機 ZIP；未提交、未上傳、未重啟展示網站，未修改 Desktop 原始專案或展示資料。本批未建立或刪除 SQL 資料庫。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。
