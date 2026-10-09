# 第七批：列表查詢最佳化與首次購物車建立競態修正

## 本批完成

- 前台商品列表：同一次商品查詢取得分類，主圖按當頁商品 ID 批次讀取，不逐筆查圖片。
- 相關商品：最多 4 筆的既有限制不變，圖片改成批次讀取。
- 後台商品列表：分類與商品同查詢；SKU 數與總庫存透過 GROUP BY 批次彙總，主圖批次讀取，不初始化每個商品的圖片／SKU 集合。
- 後台訂單列表：會員與訂單同查詢；明細筆數透過 GROUP BY 批次計算，不逐筆載入全部訂單明細。
- 大型後台列表的 IN 查詢分成每批最多 500 個 ID，不新增分頁大小限制，避免單一查詢超過 SQL Server 參數上限；一般頁面只需一批。
- 空列表不執行圖片／SKU／明細批次查詢。

所有 Entity、DTO 與 HTTP API 路徑保留，不改動結帳價格快照、扣庫存、取消回補、付款或登入授權規則。Flyway V1～V5 未修改。

## 保留的資料語意

- 商品列表選 sortOrder、ID 排序下的第一張 isPrimary 圖；沒有主圖仍為 null，不將第一張非主圖當主圖。商品詳情原有的圖片回退規則不變。
- 後台 SKU 數與庫存合計仍包含 inactive SKU；無 SKU 時回傳 0。
- 訂單 itemCount 是明細列數，不是商品數量合計；空訂單明細回傳 0。
- 公開商品仍排除非啟用分類，後台仍可查看非啟用商品／分類。
- 列表篩選、排序、總筆數與分頁內容由資料庫處理，不使用 collection fetch join 進行分頁。

## 回歸測試抓到並修正的問題

首次建立購物車原本是「查不到就新增」，不同分頁可能同時查不到，第二個 insert 違反 uq_cart_member 而回傳 500；先前瀏覽器只檢查頁面錯誤，導覽列的背景 API 錯誤可能被忽略。

修正方式：僅在查不到購物車時鎖定該會員資料列，取得鎖後再次查詢，確認仍不存在才建立。鎖在既有服務交易結束後釋放；已存在購物車的讀取不新增此鎖。沒有重設會員資料、移除唯一約束或吞掉資料庫例外。

新增 H2 與 SQL Server 的 8 個同時首次讀取測試：都須成功且回傳同一個 cart ID，資料庫只有一筆購物車。真實瀏覽器測試亦新增整個瀏覽器 context 的 API 500 檢查，包含其他分頁，不再只看 JavaScript 頁面錯誤。

此修正只處理首次建立競態，不等於所有購物車異動都已序列化，亦不等於後端訂單冪等鍵。

## 程式依據

| 相對專案路徑 | 作用 |
|---|---|
| `backend/src/main/java/com/catshop/repository/ProductRepository.java` | 商品列表／相關商品只 fetch 分類這個單值關聯 |
| `backend/src/main/java/com/catshop/repository/ProductImageRepository.java` | 主圖純欄位 projection 與穩定排序 |
| `backend/src/main/java/com/catshop/repository/ProductSkuRepository.java` | 按商品 ID 彙總 SKU 數與庫存 |
| `backend/src/main/java/com/catshop/repository/OrderRepository.java` | 後台訂單列表單值會員關聯 fetch |
| `backend/src/main/java/com/catshop/repository/OrderItemRepository.java` | 按訂單 ID 彙總明細列數 |
| `backend/src/main/java/com/catshop/common/IdBatches.java` | 每批 500 個 ID |
| `backend/src/main/java/com/catshop/service/CatalogSummaryLoader.java` | 共用主圖／庫存彙總載入器 |
| `backend/src/main/java/com/catshop/service/ProductService.java` | 前台列表與相關商品批次組 DTO |
| `backend/src/main/java/com/catshop/service/admin/AdminProductService.java` | 後台商品批次組 DTO |
| `backend/src/main/java/com/catshop/service/admin/AdminOrderService.java` | 後台訂單批次組 DTO |
| `backend/src/main/java/com/catshop/repository/MemberRepository.java` | 首次建立購物車的會員資料列鎖 |
| `backend/src/main/java/com/catshop/service/CartService.java` | 取得鎖後重新確認購物車是否存在 |
| `backend/src/test/java/com/catshop/ListQueryIntegrationTest.java` | 6 個冷 persistence context SQL 次數／資料語意情境 |
| `backend/src/test/java/com/catshop/common/IdBatchesTest.java` | 空集合及 2101 個 ID 分批驗證 |
| `backend/src/test/java/com/catshop/CheckoutIntegrationTest.java` | 首次購物車 8 請求併發 |
| `backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java` | 真實 SQL 的查詢數量及 HTTP 購物車併發驗證 |
| `frontend/scripts/e2e.cjs` | 真實流程所有分頁背景 API 500 檢查 |

## 查詢量與驗證邊界

實測比較每頁 1 與 6 筆，商品前台 3 次、商品後台 4 次、訂單後台 3 次，數量不隨逐筆增加。這是服務層 SQL 次數，不包含 JWT／session 驗證、導覽列購物車等額外 API；未測出可宣稱的毫秒加速比例。

未滿頁時 Spring Data 可省略 count 查詢；分類篩選還有原有分類階層查詢。超過 500 筆的後台頁面會按批次增加彙總查詢，並非任何頁面大小都只有 3／4 次。只在測試開啟 Hibernate statistics，正式環境沒有新增統計開銷。

測試禁止對集合 fetch join 進行記憶體分頁；使用不同分類與會員並清除 persistence context，避免快取掩蓋逐筆查詢。尚未做大量資料壓測、執行計畫調校或新增效能索引。

本機實測環境仍是 SQL Server Express 17.x，SQL Server 2022 驗收尚未完成。詳細結果見交付驗證紀錄。

## 剩餘工作

後台分類列表仍有逐分類計數及記憶體分頁；購物車多商品查詢仍可再優化。訪客結帳、功能模組整理、孤兒圖片清理、多節點限流、SQL Server 2022 驗收仍未完成。本批不宣稱整個專案的 N+1 已完全消除。

桌面原專案未修改，未 Git 提交或上傳，交付包含前六批成果及前後台一鍵展示登入。
