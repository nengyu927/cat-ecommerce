const KEY = 'catshop_checkout_attempt'
const TTL = 2 * 60 * 60 * 1000
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const limits = { receiverName: 100, receiverPhone: 30, shippingAddress: 500, remark: 500, paymentMethod: 4, couponCode: 32 }
const amountKeys = ['goodsTotal','discountAmount','shippingFee','totalAmount','priceSnapshot']
export function validCheckoutAmounts(a) {
  return a && Object.keys(a).length === 5 && Object.keys(a).every(k=>amountKeys.includes(k))
    && amountKeys.slice(0,4).every(k=>/^\d{1,14}(\.\d{1,2})?$/.test(String(a[k])) && Number.isFinite(Number(a[k])))
    && typeof a.priceSnapshot === 'string' && a.priceSnapshot.length > 0 && a.priceSnapshot.length <= 48000
    && Math.abs(Number(a.totalAmount)-(Number(a.goodsTotal)-Number(a.discountAmount)+Number(a.shippingFee)))<0.005
    && Number(a.discountAmount)<=Number(a.goodsTotal)
}
export function quoteAmounts(quote, items) {
  const a = Object.fromEntries(amountKeys.map(k=>[k,quote?.[k]]))
  const prices = [...items].sort((a,b)=>Number(a.skuId)-Number(b.skuId)).map(i=>Number(i.skuId)+':'+Number(i.quantity)+':'+Number(i.unitPrice)).join('|')
  if (!validCheckoutAmounts(a) || quote.cartSnapshot !== checkoutCartSignature(items) || quote.priceSnapshot !== prices)
    throw new Error('報價與顯示的商品內容不一致，請重新同步結帳資料')
  return a
}
export function clearCheckoutAttempt() { sessionStorage.removeItem(KEY) }
export function readCheckoutAttempt(auth) {
  const raw = sessionStorage.getItem(KEY)
  if (!raw) return null
  let record
  try { record = JSON.parse(raw) } catch { throw new Error('下單暫存無法辨識，請先查詢我的訂單，勿直接重送') }
  if (record?.memberId !== auth.member?.id || record?.epoch !== auth.epoch) {
    clearCheckoutAttempt()
    return null
  }
  if (!uuid.test(record.requestId) || !Number.isFinite(record.createdAt)) throw new Error('下單暫存格式不正確，請先查詢我的訂單')
  if (record.payload) {
    const p = record.payload
    if (p.requestId !== record.requestId || (p.expectedAmounts !== undefined && !validCheckoutAmounts(p.expectedAmounts)) || (p.cartSnapshot !== undefined && (typeof p.cartSnapshot !== 'string' || p.cartSnapshot.length > 24000 || p.cartSnapshot !== record.cartSignature)) || Object.keys(p).some(k => k !== 'requestId' && k !== 'cartSnapshot' && k !== 'expectedAmounts' && !Object.hasOwn(limits,k)) ||
        Object.entries(limits).some(([k,max]) => !((k === 'remark' || k === 'couponCode') && p[k] === null) && (typeof p[k] !== 'string' || p[k].length > max)) ||
        !['COD','MOCK'].includes(p.paymentMethod)) throw new Error('原下單內容無法辨識，請先查詢我的訂單')
    if (Date.now() - record.createdAt >= TTL) {
      delete record.payload // Keep the identity; expiry is not evidence that no order was created.
      sessionStorage.setItem(KEY, JSON.stringify(record))
    }
  }
  return record
}
export function checkoutCartSignature(items) {
  return JSON.stringify(items.map(item => [Number(item.skuId), Number(item.quantity)]).sort((a,b) => a[0]-b[0]))
}
export function saveCheckoutAttempt(auth, payload, items) {
  if (readCheckoutAttempt(auth)) throw new Error('已有尚未確認的下單請求，請先查回結果')
  const requestId = crypto.randomUUID()
  const cartSnapshot = checkoutCartSignature(items)
  if (cartSnapshot.length > 24000) throw new Error('購物車內容過多，請分批結帳')
  const record = { memberId: auth.member.id, epoch: auth.epoch, requestId, createdAt: Date.now(), cartSignature: cartSnapshot, payload: { ...payload, requestId, cartSnapshot } }
  sessionStorage.setItem(KEY, JSON.stringify(record)) // Do not POST if preservation fails.
  return record
}
export function settleCheckoutAttempt(auth, requestId) {
  const current = readCheckoutAttempt(auth)
  if (current?.requestId === requestId) clearCheckoutAttempt()
}
