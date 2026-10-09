# SKU／圖片管理草稿確認

## 本批範圍

- `frontend/src/views/AdminProductAssetView.vue`：SKU／圖片頁離開、切換商品／管理模式、重新載入時，以主題確認視窗提示尚未儲存的修改。
- `frontend/src/composables/useAdminDraftGuard.js`：支援動態頁面名稱，沿用商品、分類與優惠券頁的相同確認與登入身分防護。
- 確認視窗預設聚焦「繼續編輯」；Esc 保留草稿。確認期間停用欄位、儲存、上傳及圖片排序操作；直接觸發 submit 也不會送出。
- 放棄並重新載入才清除草稿；離開仍保留商品列表查詢條件。登出會取消待決確認並解除背景捲動鎖定。
- 瀏覽器重新整理／關閉分頁仍使用原生 beforeunload 提醒。

## 保留的資料規則

- SKU 仍獨立儲存，保留 version、防止負庫存與安全庫存驗證，成功儲存一列不清除其他列草稿。
- 圖片仍使用 expectedImageIds 比對，主圖、排序、最多 100 張與 PNG／JPEG 5 MB 限制不變。
- 上傳只取得網址，必須儲存圖片清單才更新商品關聯。清空關聯不會刪除上傳檔案。
- 後端、API 路徑、持久資料庫與展示資料未修改；不需重啟後端。

## 驗證

- `node scripts/product-assets.cjs`：15 組，包含原有 11 組與桌機／手機兩種管理模式的 4 組草稿確認案例。
- `node scripts/admin-draft-confirmation.cjs`：商品／分類／優惠券共 12 組回歸。
- `node scripts/confirmation-dialog.cjs`：共用視窗 2 組回歸。
- `npm run build`：前台與 Admin 建置。
- `node scripts/images-preview.cjs`：展示網站唯讀檢查，不新增／變更商品、購物車或訂單。

## 下一步

將訂單出貨表單的未儲存提醒統一成主題確認，保留出貨狀態、物流欄位與重複操作防護。
