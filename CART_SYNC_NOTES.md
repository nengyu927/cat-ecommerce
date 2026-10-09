# M01 第十一批：購物車重新同步與失敗復原

日期：2026-10-09。延續 PRODUCT_CART_ACTION_NOTES.md。原始 index.html 尚未取得，本批未修改 Excel 計畫表，不標記 M01 全部完成。

## 完成內容

- 購物車新增「重新同步購物車」，沿用現有 cart.fetch() 讀取與 Pinia 佇列，不新增 API 或自動重送寫入。
- 同步成功更新列表資料、庫存上限、數量、單價、小計及訂單摘要，顯示成功訊息。庫存資訊另以文字顯示。
- 有成功資料後再次同步，不先清除 loaded 或列表；同步失敗仍顯示上次成功資料、明確提醒重新同步，並提供「重試同步」。
- 初次讀取失敗不冒充空購物車，使用「重試載入」；同步成功後若真正沒有商品，才顯示空購物車。
- busy 共用狀態在同步或商品變更期間停用數量、移除與結帳，讀取入口亦有防重入。
- needsSync 防止在同步／寫入失敗後直接用未確認資料結帳；成功讀取或成功變更取得完整 cart 回應後才清除。
- 庫存為 0 或目前數量超過 stock 時顯示提示並停用結帳，使用者可調整有效數量或移除。此處不推論商品上下架狀態，後端仍作最終業務檢查。
- 同步使用版本與離頁防護，不把離頁後的讀取結果回填頁面。store 原有登入 epoch 與佇列防護不改。

## 程式碼依據

- frontend/src/views/CartView.vue：syncing／needsSync／busy／stockIssue、load 保留已載入內容、重試入口與控制項停用。
- frontend/scripts/cart-sync-ui.cjs：snapshot、受控 gate、寫入次數與失敗後重讀斷言。
- frontend/package.json：test:cart-sync-ui。
- frontend/src/stores/cart.js：既有 fetch／enqueue／epoch；本批未修改。

## 本次驗證

- test:cart-sync-ui：storefront／combined 共 10 組通過，涵蓋價格與庫存更新、同步失敗保留與重試、讀取期間防重入與真正空列表、更新被拒後重讀不重送，以及首次讀取失敗。
- test:cart-state 8 組、test:product-cart-ui 8 組、test:stability 12 組回歸通過。
- 共 38 項測試通過，前台及 Admin 正式建置通過。
- 本批只修改前端，測試使用隔離 Edge 與模擬 API；未重跑後端／SQL Server，不引用舊 142／21 作為本批新結果。

## 下一步

改善結帳頁庫存不足提示與復原流程：重新載入或下單失敗時保留使用者已填的收件人、電話、地址與備註；重新同步購物車後檢查庫存／金額，避免重試覆蓋手動輸入。補載入、下單與恢復測試。

仍需確認查詢格式相容策略、取得原規格書及更新計畫表，才完成 M01 整體驗收。

## 交付界線

只更新工作區並建立本機 ZIP；不提交、不上傳、不重啟展示服務、不修改 Desktop 原始專案與展示持久資料。本批沒有 SQL 建立／刪除操作。ZIP 排除 node_modules、target、dist、.local、uploads、.git 與日誌。
