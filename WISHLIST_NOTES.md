# 會員商品收藏實作紀錄

更新日期：2026-10-08。本批延續商城功能清單，新增會員收藏；不是對原始規格書重新逐項稽核，也不包含優惠券、忘記密碼或前台品牌／價格篩選。

## 使用方式與範圍

- 首頁、商品列表、搜尋結果的商品卡新增主題綠愛心。未登入會導向登入頁，登入後可收藏／取消收藏。
- 登入後導覽列新增「我的收藏」，網址 `/wishlist`，每頁 12 件，最新收藏優先；空清單、載入中、失敗重試、上一頁／下一頁皆有處理。
- 收藏存於資料庫，以商品為單位，每位會員最多 500 件。不是僅存在瀏覽器；重新整理不會消失，但目前展示啟動器重啟仍會重建整個暫存 DB。
- 商品後來停用／下架，或其直接分類停用時，仍保留既有收藏，標示不可購買並允許移除；不能新收藏此類商品。此判斷不代表 SKU 有庫存，也未新增上層分類可用性規則。
- 未新增商品詳情收藏入口、Admin 收藏統計、跨分頁即時同步或缺貨通知。

## 後端依據

| 檔案 | 作用 |
|---|---|
| `backend/src/main/resources/db/migration/V6__wishlist.sql` | 新增 wishlist_item、會員與商品外鍵、member+product 唯一約束及會員列表索引；不修改 V1～V5。 |
| `backend/src/main/java/com/catshop/entity/WishlistItem.java` | 收藏 Entity，關聯 Member／Product、建立時間。 |
| `backend/src/main/java/com/catshop/dto/wishlist/WishlistItemResponse.java` | 回傳商品摘要、目前可用性與收藏時間；id 是商品 ID。 |
| `backend/src/main/java/com/catshop/repository/WishlistRepository.java` | 會員範圍的分頁、ID 清單、數量與刪除；載入商品／分類避免逐筆延遲查詢。 |
| `backend/src/main/java/com/catshop/repository/MemberRepository.java` | lockForWishlistMutation 會員列悲觀鎖。 |
| `backend/src/main/java/com/catshop/service/WishlistService.java` | 會員寫入序列化，冪等增刪、500 件上限、分頁合法性、主圖批次查詢。 |
| `backend/src/main/java/com/catshop/controller/WishlistController.java` | 從 JWT 取得本人 ID，不接受外部指定會員。沿用既有 Security 登入保護。 |

API：GET `/api/wishlist?page=0&size=12`、GET `/api/wishlist/ids`、PUT `/api/wishlist/{productId}`、DELETE `/api/wishlist/{productId}`。分頁 size 1～100；未登入 401。新增／刪除回傳本人完整商品 ID 清單；重複新增及不存在的刪除仍成功。其他會員對同一商品的操作不會讀寫本人的資料。

## 前端依據

- `frontend/src/stores/wishlist.js`：請求依序執行、初始讀取去重、同商品防連點、失敗後佇列恢復、登入 epoch 與 reset 拒絕舊回應。
- `frontend/src/components/ProductCard.vue`：卡片連結與愛心按鈕分開，避免按收藏同時跳商品頁；aria-label／aria-pressed、忙碌停用與錯誤提示。
- `frontend/src/views/WishlistView.vue`：分頁清單，移除當頁最後一筆時回到前一頁；讀取版本防舊查詢覆蓋。
- `frontend/src/App.vue`、`frontend/src/router/index.js`、`frontend/src/api/index.js`：登入後載入、切換會員清空、前台限定受保護路由與 API。
- `frontend/src/assets/main.css`：沿用商城主題色的愛心樣式。

## 驗證結果

- Maven 一般測試：84 項，零失敗／錯誤；新增 WishlistIntegrationTest 7 項。
- 獨立 SQL Server 真實 HTTP 整合：10 項，零失敗／錯誤；驗證 Flyway V6、同時 8 次收藏只存一筆、會員隔離、下架商品、非法分頁與冪等移除。
- 真實 Vue → Spring Boot → SQL Server 瀏覽器流程：註冊、收藏、收藏頁、重新整理、取消收藏、購物車、結帳、取消還庫存、登入撤銷與 Admin 權限均通過。
- `npm run test:wishlist-state`：6 項通過；`npm run test:cart-state`：8 項通過；`npm run test:stability`：12 項通過，無未處理頁面錯誤。
- 前台與 Admin 正式建置均通過。實測資料庫是本機 SQL Server Express 17.x，規格指定 SQL Server 2022 仍應在目標環境重跑。
- SQL 整合只建立／清除此次唯一命名測試 DB／登入，不操作展示資料。

## 展示套用與下一步

已取得使用者同意重建暫存展示資料，重啟後端並套用 V6；舊展示會員／訂單／商品修改／購物車已清除，新資料不可由網站復原。前台仍是 http://127.0.0.1:5173/，Admin 仍是 http://127.0.0.1:5174/。舊登入失效，請重新一鍵登入。

本機調整在工作副本，不修改桌面原始專案；未提交 Git、未上傳。下一步優先處理展示資料持久化，再做前台品牌／價格篩選。
