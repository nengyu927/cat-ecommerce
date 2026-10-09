export class ApiError extends Error {
  constructor(body, status = 0, fallback = '請求失敗') {
    super(body?.error?.message || body?.message || fallback)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.error?.code || body?.code || 'NETWORK_ERROR'
    this.fields = {}
    for (const detail of body?.errors || body?.error?.details || []) {
      if (typeof detail.field === 'string' && !Object.hasOwn(this.fields, detail.field)) {
        this.fields[detail.field] = detail.reason || detail.message || '欄位內容不正確'
      }
    }
  }
}
