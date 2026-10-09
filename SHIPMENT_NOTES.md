# 人工出貨登錄

2026-10-08。這批新增物流公司、單號、出貨登錄時間與操作者紀錄，不串接物流供應商。

## 完成範圍

- Admin 訂單詳情出貨表單與確認提示；必填物流公司（80 字內）／單號（100 字內），去除前後空白、拒絕控制字元。登錄後不可修改、重複出貨或取消訂單。
- PENDING 貨到付款與 PAID 訂單可出貨；未付款非貨到付款、已取消、已完成或已出貨拒絕。一般狀態 PATCH 的 SHIPPED 現在返回 400，呼叫方必須改用出貨 API，不能略過物流資料。
- 出貨與訂單 SHIPPED 狀態在同一交易提交；先 flush 訂單 optimistic version，競爭取消／出貨只允許一方成功。唯一 order_id 防止重複紀錄。
- 會員訂單詳情顯示人工登錄資料；僅可查自己的訂單，其他會員訂單回 404。公共回應不暴露操作者會員編號。
- Flyway V10 新增 order_shipment，不更動舊遷移或補造舊訂單資料；列表仍維持原查詢預算。
- 前台手機版訂單詳情改善欄位配置；Admin 詳情與前台換單避免較慢舊回應覆蓋新訂單。

## API 與程式依據

- POST /api/admin/orders/{id}/shipment：ADMIN，CreateShipmentRequest，成功 201。
- GET /api/admin/orders/{id}/shipment：ADMIN，未登錄回 data:null。
- GET /api/orders/{id}/shipment：登入且為訂單擁有者，未登錄回 data:null。
- backend/src/main/java/com/catshop/service/ShipmentService.java：狀態檢查、交易、擁有者驗證。
- backend/src/main/java/com/catshop/entity/OrderShipment.java、repository/OrderShipmentRepository.java、dto/order/ShipmentResponse.java。
- backend/src/main/resources/db/migration/V10__order_shipment.sql：SQL Server 唯一約束及訂單外鍵。
- frontend/src/views/AdminView.vue、OrderDetailView.vue：表單、確認、出貨紀錄與非即時追蹤提示。

## 驗證

- 後端 128 項測試通過；新增資料必填、去空白、擁有者隔離、重複出貨、禁止出貨後取消、未付款非 COD 拒絕等案例。
- 真實 SQL Server 15 項整合測試通過：V10 遷移、JWT／角色權限、單號保存、兩次同時出貨只成功一次、出貨／取消競爭只成功一方、完成訂單後仍保留紀錄。隔離資料庫已清理。
- 真實前後端瀏覽器購物流程回歸通過。另有 4 項出貨畫面測試與後台篩選回歸通過；前台／Admin 建置通過。
- npm run test:shipments（frontend）可重跑畫面測試。
- 本機 SQL Server 為 17.x，驗證使用 AllowOtherVersion；不宣稱已在 SQL Server 2022 驗證。
- 展示後端已沿用原持久 DB 遷移至 V10；原有會員 2、商品 10，訂單／購物車／收藏 0，重啟前後一致。沒有建立假展示訂單。

## 尚未涵蓋

每張訂單目前只支援一次完整出貨，不是多包裹／部分出貨。沒有物流即時查詢、外部查詢連結、物流回傳、配送中／簽收事件、修改單號歷程、出貨通知或退貨退款。COMPLETED 仍是 Admin 人工設定，並非物流公司回報已簽收。既有 SHIPPED 訂單可能沒有出貨紀錄，介面會誠實顯示無資料。

原規格書 index.html 尚未在本機找到，此批是依產品待辦與既有訂單架構實作，不宣稱原規格全部完成。未提交或上傳程式碼。
