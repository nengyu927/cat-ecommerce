# 購物車多商品查詢優化（2026-10-08）

## 本批完成

- 購物車項目列表透過 EntityGraph 同時取得 `sku` 和 `sku.product`，不再逐項讀取規格與商品。只載入單值關聯，不 fetch 商品 SKU、圖片或分類 children 集合。
- `getCart` 只查一次項目，更新目前 SKU 售價後直接使用同一份項目組回應，移除原本再次查詢項目的流程。
- 回應的商品主圖改用既有 CatalogSummaryLoader 批次 projection，只取商品 ID 和主圖 URL，不再為每一個購物車項目查整份圖片。
- 商品 ID 先去重，同商品不同 SKU 不重複要求圖片；沿用 IdBatches 每批 500 個 ID，避免單個 IN 查詢超出 SQL Server 參數上限。空購物車不查圖片。
- 保留主圖規則：只使用 isPrimary=true，依 sortOrder、圖片 ID 選第一張；沒有主圖回 null，不改用第一張次要圖片。
- 新增、更新、刪除項目的回應也使用批次主圖，保留完整剩餘購物車項目。

API、DTO、Entity、Flyway V1～V5、前端畫面及金額規則未修改。重新讀取購物車仍更新所有項目的目前價格；新增／修改仍只更新受操作項目的單價。結帳仍採目前 SKU 售價，已建立的訂單維持歷史快照。

## 程式依據

- `backend/src/main/java/com/catshop/repository/CartItemRepository.java`：`findByCartIdOrderByIdAsc` 新增 `EntityGraph(attributePaths = {"sku", "sku.product"})`。
- `backend/src/main/java/com/catshop/service/CartService.java`：`getCart` 重用列表；`toResponse(Cart, List<CartItem>)` 對商品 ID 去重並使用批次主圖；刪除原本每項呼叫 primaryImage 的查詢。
- `backend/src/main/java/com/catshop/service/CatalogSummaryLoader.java`：沿用既有批次查詢與第一張主圖規則，本批未修改。
- `backend/src/test/java/com/catshop/ListQueryIntegrationTest.java`：新增 5 個購物車測試，含冷 persistence context 的 1 件／6 件不同商品、金額／庫存／排序、空購物車、多 SKU 無主圖、重新定價持久化及新增／更新／移除／清空回應。
- `backend/src/test/java/com/catshop/integration/SqlServerCheckoutIT.java`：新增真實 SQL Server 購物車查詢數、總數量、金額、主圖、庫存及重新定價持久化測試；僅清理自行建立的購物車／會員。

共用的購物車項目 Repository 也被結帳使用，因此另行回歸結帳、庫存並行操作、訂單快照與取消回補，不改變原有交易和樂觀鎖規則。

## 驗證結果

全部 67 個後端單元／H2 測試、7 個 SQL Server 整合測試通過，0 失敗、0 錯誤。

真實 SQL Server 的服務層結果：

| 情境 | SQL 數量 |
|---|---:|
| 已有購物車，1 件不同商品，售價未變 | 3 |
| 已有購物車，6 件不同商品，售價未變 | 3 |
| 已有空購物車 | 2 |
| 1 項售價改變，含持久化寫入 | 4 |

一般讀取是購物車、項目含規格／商品、批次主圖各 1 次。售價未變的案例已包含交易 flush 檢查。售價變動仍有每個受影響項目的必要 UPDATE，不宣稱所有情境固定 3 次；首次建立購物車也有會員鎖、重查及 INSERT。超過 500 個不同商品的圖片查詢會增加批次次數，尚未以大型購物車實測。上述數字不含 HTTP JWT／session 驗證，不代表已量測毫秒或加速百分比。

- 真實 Edge 購物流程通過：註冊、HttpOnly Cookie、購物車重新載入持久化、結帳扣庫存、取消回補、更新登入、改密碼跨分頁登出、新密碼登入與展示登入／會員禁止進後台。
- `npm run build` 前台及 Admin 後台建置通過。
- 重啟後 `npm run test:split` 通過：前後台角色、refresh Cookie、更新登入和登出彼此分開，未出現 API 500 或頁面錯誤。
- 8080 健康 API 為 UP。

SQL 實測環境是本機 Express 17.x，不是規格指定的 SQL Server 2022；2022 驗收仍待完成。整合測試使用唯一命名的 catshop_it 暫存資料庫與專用登入，測試結束均已移除，未操作展示資料庫。

完整驗證（PowerShell，backend 目錄）：

```powershell
$env:E2E_BROWSER_CHANNEL = 'msedge'
.\scripts\verify-app-mssql.ps1 -AllowOtherVersion -Browser
```

## 展示與交付

經使用者明確同意，展示後端已重啟並套用本批版本；舊暫存展示資料庫與專用登入已移除，沒有留下備份。展示會員、訂單和修改紀錄不保留，需重新登入。

- 「喵喵商城」：`http://127.0.0.1:5173/`
- 「喵喵商城 Admin」：`http://127.0.0.1:5174/`

保留前後台獨立網址、名稱、一鍵展示登入、分類階層防護及先前各批成果。桌面原始專案未修改，未提交或上傳。更新 ZIP 不含 node_modules、target、dist、uploads 或 .git。

## 尚未完成／接續建議

1. 補齊後台已有 API 支援但畫面尚未提供的進階篩選，例如訂單會員／日期及分類啟用狀態。
2. 分類樹批次查詢、既有錯誤階層檢查與深層樹驗證。
3. 孤兒圖片清理、功能模組整理及大量資料壓力測試。
4. 持久展示資料啟動方式、正式反向代理／Cookie 部署設定及 SQL Server 2022 驗收。

這批未修正同時新增同一 SKU 的購物車競態，未新增訪客結帳、正式金流或通知功能；沒有宣稱全部規格已完成。
