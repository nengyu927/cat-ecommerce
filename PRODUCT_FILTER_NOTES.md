# 前台品牌與價格篩選

日期：2026-10-08。已實作並套用展示網站；前台／Admin 網址不變。持久展示後端重啟前後會員、訂單、購物車項目、收藏與商品筆數相同，未重建資料。未提交或上傳；桌面原始專案未修改。

## 功能與規則

- 所有商品、搜尋結果與分類商品頁增加品牌、最低／最高價格輸入，沿用分類、關鍵字與排序，可組合查詢。
- 品牌採完整名稱比對，忽略大小寫及輸入前後空白；不是名稱包含搜尋。無此品牌回空清單，不回傳其他品牌。
- GET `/api/products/brands` 回傳前台可見商品的全站品牌，不是依當前分類／關鍵字動態計算的品牌。排除未啟用商品、非 ON_SALE 商品及直接分類未啟用商品；不暴露隱藏商品的品牌。沿用既有直接分類可見性規則，未新增所有祖先分類判斷。
- GET `/api/products` 新增 `brand`、`minPrice`、`maxPrice`；價格依商品卡的 `basePrice`，上下界皆包含。範例：`/api/products?brand=Royal%20Canin&minPrice=890&maxPrice=890`。
- 可單獨指定任一價格界線。價格須 0～99999999.99、最多兩位小數；最低不可高於最高。非法數字、負數、超界、非法排序／分類編號回 400。
- 下拉品牌／分類／排序會套用條件；價格輸入後按「搜尋」或 Enter。套用新條件回第一頁，下一頁保留已套用的 URL 查詢，不把未送出的價格草稿套入分頁。
- 條件寫入 URL，重新整理仍保留；顯示已套用品牌與價格摘要。「清除條件」移除關鍵字、分類、品牌、價格與頁碼，回所有商品／最新排序。
- 選單失敗顯示重試入口；非法價格保留可編輯的輸入。商品查詢版本防止慢的舊回應覆蓋新結果，包含分類 slug 查詢及離開頁面。

本批沒有加入現貨／SKU 可購買狀態篩選、運費／優惠券或修改結帳計價。商品基本售價與 SKU 售價可能不同，不能把此篩選宣稱為最便宜 SKU 的價格篩選。

## 程式碼依據

| 檔案 | 關鍵實作 |
|---|---|
| `backend/src/main/java/com/catshop/controller/ProductController.java` | 新增品牌清單 API 與列表品牌／價格參數，維持原有 URL。 |
| `backend/src/main/java/com/catshop/service/ProductService.java` | 驗證價格、完整品牌比對、沿用分類下層與穩定排序；保留原 search 方法供既有程式呼叫。 |
| `backend/src/main/java/com/catshop/repository/ProductRepository.java` | SQL 層組合品牌、價格、可見性查詢；不是先抓當頁資料再於前端篩選。 |
| `frontend/src/views/ProductListView.vue` | 篩選控制、URL 讀寫、套用／清除／分頁、錯誤重試與查詢版本。 |
| `frontend/src/stores/product.js` | 傳遞篩選參數，requestVersion 拒絕舊結果及舊 loading 清除。 |
| `frontend/src/api/index.js` | 品牌清單 API。 |

不改 Entity／DTO／資料表，不新增 Flyway 遷移；既有 Security 公開商品 GET 規則涵蓋新 brands API。商品摘要依然批次查主圖，沒有逐筆抓圖片。

## 驗證結果

- 新增 `ProductFilterIntegrationTest` 4 項：品牌完整比對、上下界包含、分類下層、組合條件／分頁、可見品牌、匿名 HTTP、非法價格／排序／分類。
- 完整後端 88 項，零失敗／錯誤。
- 獨立 SQL Server HTTP 整合 10 項通過；真實 Vue 流程新增品牌／相同價格上下界篩選，再完成收藏、結帳、取消還庫存與登入授權流程。
- `npm run test:product-filters` 5 項通過：網址／分頁／重新整理／清除、非法價格阻止查詢與零價格、分類保留、選單重試、舊慢查詢不覆蓋新條件。無未處理頁面錯誤。
- 前台、Admin 正式建置均通過。
- 展示網站匿名品牌 API 正常；Royal Canin、890～890 返回 1 件；倒置價格界線回 400；前後台 HTTP 200。
- 本機 SQL Server Express 17.x 實測通過；SQL Server 2022 目標環境仍需重跑。只清除此次唯一命名測試 DB，未刪持久展示 DB。

下一步建議補現貨篩選，先明確定義「至少一個啟用 SKU 有庫存」與售價基準；忘記密碼則需另行配置郵件／測試信箱。
