# 喵喵商城 · 後台 ADMIN CRUD API 參考

> 本文件對應「補齊 ADMIN CRUD 端點（商品／分類／訂單），目標 38 個 API」任務。
> 新增 14 個端點後，全站 API 由 24 個達到 **38 個**。

## 1. 權限模型

| 角色 | 說明 |
|---|---|
| PUBLIC | 免登入（`/api/auth/**`、`GET /api/products/**`、`GET /api/categories/**`） |
| AUTHENTICATED | 需登入（CUSTOMER 或 ADMIN） |
| ADMIN | 需登入且 `role=ADMIN` |

**SecurityConfig 關鍵設定**（置於 `anyRequest().authenticated()` 之前）：

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

- JWT 已含 `role` claim，`JwtAuthenticationFilter` 轉為 `ROLE_<role>` authority，對應 `hasRole('ADMIN')`。
- 三個 Admin Controller 類別層級加上 `@PreAuthorize("hasRole('ADMIN')")`（`@EnableMethodSecurity` 已開啟），形成雙層防護。
- 401 / 403 皆回傳統一 `ApiResponse` JSON 信封。

## 2. 端點清單（14 個新增）

### 2.1 分類 AdminCategory（AC1–AC4）

| ID | 方法 | 路徑 | 說明 |
|---|---|---|---|
| AC1 | GET | `/api/admin/categories` | 後台分類列表（含停用、含商品數），支援 `keyword`、`isActive`、`page`、`size` |
| AC2 | POST | `/api/admin/categories` | 新增分類（201） |
| AC3 | PUT | `/api/admin/categories/{id}` | 更新分類 |
| AC4 | DELETE | `/api/admin/categories/{id}` | 停用分類（軟刪除 `is_active=false`） |

**CreateCategoryRequest / UpdateCategoryRequest**

| 欄位 | 型別 | 驗證 |
|---|---|---|
| name | String | `@NotBlank` `@Size(max=100)` |
| slug | String | `@NotBlank` `@Pattern(^[a-z0-9-]+$)` `@Size(max=120)` |
| parentId | Long? | 可空；不存在回 404 `PARENT_NOT_FOUND` |
| sortOrder | Integer | 預設 0 |
| isActive | Boolean | 預設 true |

**AdminCategoryResponse**：`id, name, slug, parentId, parentName, sortOrder, isActive, productCount, createdAt`

錯誤碼：`400 VALIDATION_ERROR`、`404 CATEGORY_NOT_FOUND`、`404 PARENT_NOT_FOUND`、`409 SLUG_DUPLICATED`、`409 CATEGORY_HAS_CHILDREN`、`409 CATEGORY_HAS_ACTIVE_PRODUCTS`。

### 2.2 商品 AdminProduct（AP1–AP7）

| ID | 方法 | 路徑 | 說明 |
|---|---|---|---|
| AP1 | GET | `/api/admin/products` | 後台商品列表（含下架），支援 `categoryId`、`keyword`、`brand`、`status`、`isActive`、`page`、`size`、`sort` |
| AP2 | GET | `/api/admin/products/{id}` | 後台商品詳情（含全部 SKU／圖片，含停用） |
| AP3 | POST | `/api/admin/products` | 新增商品（可同時帶入 SKU 與圖片，201） |
| AP4 | PUT | `/api/admin/products/{id}` | 更新商品主檔（SKU 走 AP6/AP7） |
| AP5 | DELETE | `/api/admin/products/{id}` | 下架商品（軟刪除 `status=OFF_SHELF, is_active=false`） |
| AP6 | POST | `/api/admin/products/{id}/skus` | 新增商品規格 SKU（201） |
| AP7 | PUT | `/api/admin/skus/{skuId}` | 更新 SKU（價格／庫存／安全庫存／啟用） |

`brand` 為選填的完整品牌名稱比對，去除輸入前後空白，不分大小寫，最多 100 字；空白代表不限品牌。`%`、`_` 等字元不是品牌篩選萬用字元。`categoryId` 比對該分類本身，不自動包含下層分類。

**CreateProductRequest**：`name`(`@NotBlank`,`@Size(max=200)`)、`slug`(`@Pattern(^[a-z0-9-]+$)`)、`description`、`categoryId`(`@NotNull`)、`brand`(`@Size(max=100)`)、`basePrice`(`@NotNull`,`@DecimalMin(0)`)、`status`(ON_SALE|OFF_SHELF|SOLD_OUT)、`isActive`、`images[]`、`skus[]`。

**AdminProductSummaryResponse**：`id, name, slug, categoryId, categoryName, brand, basePrice, status, isActive, skuCount, totalStock, primaryImageUrl, updatedAt`

**AdminProductDetailResponse**：`id, name, slug, description, categoryId, categoryName, brand, basePrice, status, isActive, images[], skus[], createdAt, updatedAt`

**AdminSkuResponse**：`id, skuCode, specName, price, stock, safetyStock, isActive, version`

更新 SKU 必須附上讀取時的 `version`；版本過期回傳 409，防止舊表單覆蓋新庫存。

商品 DTO 新增 `ingredients/suitableAge/origin/storageInstructions`；商品更新可帶 `images[]` 取代圖片清單。

圖片上傳：`POST /api/admin/uploads/images`，使用 multipart 的 `file` 欄位；僅接受 PNG/JPEG、5 MB、1600 萬像素，回傳 `{url}`。

錯誤碼：`400 VALIDATION_ERROR`、`404 PRODUCT_NOT_FOUND`、`404 CATEGORY_NOT_FOUND`、`404 SKU_NOT_FOUND`、`409 SLUG_DUPLICATED`、`409 SKU_CODE_DUPLICATED`。

### 2.3 訂單 AdminOrder（AO1–AO3）

| ID | 方法 | 路徑 | 說明 |
|---|---|---|---|
| AO1 | GET | `/api/admin/orders` | 後台訂單列表，支援 `status`、`memberId`、`orderNo`、`from`、`to`、`page`、`size`、`sort` |
| AO2 | GET | `/api/admin/orders/{id}` | 後台訂單詳情（含明細、收件快照、會員資訊） |
| AO3 | PATCH | `/api/admin/orders/{id}/status` | 更新訂單狀態（出貨／完成／取消） |

`from`／`to` 使用 `YYYY-MM-DD`，開始與結束日期皆包含當天；結束邊界以小於隔天零點處理，避免漏掉當天最後一毫秒。可只提供單邊日期，日期依後端儲存的建立時間判斷。開始晚於結束、memberId 非正整數或非法分頁回 400。

**UpdateOrderStatusRequest**：`status`(`@NotBlank`)、`remark`(`@Size(max=500)`)

**AdminOrderSummaryResponse**：`id, orderNo, memberId, memberName, memberEmail, status, totalAmount, shippingFee, itemCount, receiverName, createdAt`

**AdminOrderDetailResponse**：`id, orderNo, memberId, memberName, memberEmail, status, paymentMethod, totalAmount, shippingFee, receiverName, receiverPhone, shippingAddress, remark, items[], createdAt, updatedAt`

**訂單狀態機**（集中於 `AdminOrderService`，禁止任意跳轉）：

```
PENDING   -> PAID | CANCELLED | SHIPPED（只有 COD 可直接出貨）
PAID      -> SHIPPED | CANCELLED
SHIPPED   -> COMPLETED
COMPLETED -> (終態)
CANCELLED -> (終態)
```

- 取消訂單（CANCELLED）於同一交易內回補 SKU 庫存。
- 非法轉移回 `400 INVALID_STATUS_TRANSITION`。

## 3. 統一信封與分頁

```json
// 成功
{ "success": true, "data": { }, "timestamp": "2026-10-07T10:00:00" }
// 錯誤
{ "success": false, "error": { "code": "VALIDATION_ERROR", "message": "請求參數驗證失敗",
  "details": [{ "field": "name", "message": "商品名稱不可為空" }] }, "timestamp": "..." }
```

分頁回應：`PageResult<T> { content, page, size, totalElements, totalPages, last }`

## 4. 驗證方式

```bash
# 取得 ADMIN JWT
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@catshop.local","password":"password123"}'

# 呼叫後台端點
curl http://localhost:8080/api/admin/products \
  -H "Authorization: Bearer <ADMIN_JWT>"

# 以 CUSTOMER token 呼叫 -> 403 FORBIDDEN
```

## 5. 檔案清單

```
config/SecurityConfig.java                       (強化：/api/admin/** hasRole('ADMIN') + 401/403 JSON)
repository/CategoryRepository.java               (admin 查詢)
repository/ProductRepository.java                (adminSearch)
repository/ProductSkuRepository.java             (admin 查詢)
repository/OrderRepository.java                  (adminSearch)
dto/admin/category/*.java                        (3 檔)
dto/admin/product/*.java                         (7 檔)
dto/admin/order/*.java                           (3 檔)
service/admin/AdminCategoryService.java
service/admin/AdminProductService.java
service/admin/AdminOrderService.java
controller/admin/AdminCategoryController.java
controller/admin/AdminProductController.java
controller/admin/AdminOrderController.java
```
