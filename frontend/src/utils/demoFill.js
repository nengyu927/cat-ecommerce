// Fictional presentation data. These helpers only create drafts; they never call APIs.
export const demoPerson = Object.freeze({ name: '林芷晴', phone: '0916283745' })
export const demoPassword = 'MeowDaily!2026'
export function uniqueDemoId() {
  const suffix = Array.from(crypto.getRandomValues(new Uint8Array(4)), byte => byte.toString(16).padStart(2, '0')).join('')
  return `${Date.now().toString(36)}-${suffix}`
}
export function registrationDraft() {
  return { ...demoPerson, email: `zhiching.lin.${uniqueDemoId()}@example.com`, password: demoPassword }
}
export function addressDraft() {
  return { receiverName: demoPerson.name, phone: demoPerson.phone, city: '臺北市', district: '大安區', line1: '和平東路二段 86 號', line2: '6 樓之 2，請按門鈴', postalCode: '106', isDefault: true }
}
export function checkoutDraft() {
  const address = addressDraft()
  return { receiverName: address.receiverName, receiverPhone: address.phone, shippingAddress: `${address.city}${address.district}${address.line1} ${address.line2}`, remark: '請於平日下午配送，抵達前請先電話聯繫。', paymentMethod: 'COD' }
}
export function skuDraft(price = 680) {
  return { skuCode: `MD-CHICKEN-${uniqueDemoId().toUpperCase()}`, specName: '雞肉口味 / 1.5 公斤', price: Number(price) || 680, stock: 48, safetyStock: 10, isActive: true }
}
export function productDraft(categoryId) {
  return { name: '喵日常 鮮雞無穀成貓配方', slug: `meow-daily-chicken-${uniqueDemoId()}`, categoryId, brand: '喵日常', description: '以鮮雞肉搭配南瓜與魚油製作，顆粒大小適中，適合成貓日常主食。建議依體重調整餵食量，並提供充足飲水。', ingredients: '雞肉、雞肉粉、豌豆、南瓜、雞脂、鮭魚油、牛磺酸、維生素與礦物質', suitableAge: '1 歲以上成貓', origin: '臺灣', storageInstructions: '請置於陰涼乾燥處，開封後密封保存，避免陽光直射。', basePrice: 680, status: 'ON_SALE', isActive: true }
}
export function couponDraft() {
  const local = date => {
    const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    return shifted.toISOString().slice(0, 19)
  }
  return { code: `MEOW-${uniqueDemoId().toUpperCase()}`, discountAmount: 100, minimumAmount: 500, maxUses: 200, startsAt: local(new Date(Date.now() - 86400000)), endsAt: local(new Date(Date.now() + 30 * 86400000)), active: true }
}
