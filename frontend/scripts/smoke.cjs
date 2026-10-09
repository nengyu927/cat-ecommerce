// Browser smoke tests with a stub API, including simultaneous 401 refresh/retry. Backend behavior is covered separately by Maven tests.
// Run with PLAYWRIGHT_MODULE pointing to a local Playwright installation, and SMOKE_URL to the Vite server.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')

async function main() {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  try {
    const page = await browser.newPage()
    const errors = []
    const mutations = []
    let expireRequests = false
    let refreshCount = 0
    let shipment = null
    page.on('dialog', dialog => dialog.accept())
    page.on('pageerror', error => errors.push(error.message))
    const category = { id: 1, name: '飼料', slug: 'food', isActive: true, sortOrder: 0, parentId: null, children: [] }
    let products = [{ id: 1, name: '測試飼料', slug: 'food-test', categoryId: 1, categoryName: '飼料', basePrice: 200, brand: 'Test', description: '商品描述', ingredients: '雞肉', suitableAge: '成貓', origin: '台灣', storageInstructions: '陰涼乾燥處', status: 'ON_SALE', isActive: true, images: [], skus: [{ id: 1, skuCode: 'TEST-1', specName: '1kg', price: 200, stock: 10, safetyStock: 1, isActive: true, version: 0 }], totalStock: 10 }]
    let order = { id: 1, orderNo: 'TESTORDER', memberId: 1, memberName: '管理員', memberEmail: 'admin@test.local', status: 'PENDING', paymentMethod: 'COD', totalAmount: 480, shippingFee: 80, receiverName: '收件人', receiverPhone: '0912345678', shippingAddress: '台北市', items: [{ id: 1, productName: '測試飼料', skuSpec: '1kg', unitPrice: 200, quantity: 2, subtotal: 400 }], createdAt: '2026-10-07T10:00:00' }
    const paging = content => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, last: true })
    await page.addInitScript(() => {
      localStorage.setItem('catshop_token', 'test-token')
      localStorage.setItem('catshop_member', JSON.stringify({ id: 1, role: 'ADMIN', name: '管理員', email: 'admin@test.local' }))
    })
    await page.route(url => !['127.0.0.1', 'localhost'].includes(url.hostname), route => route.abort())
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const request = route.request()
      const url = new URL(request.url())
      const path = url.pathname
      let data
      if (expireRequests && path === '/api/members/me' && request.headers().authorization === 'Bearer test-token') {
        await route.fulfill({ status: 401, json: { success: false, error: { message: 'expired' } } }); return
      }
      if (request.method() !== 'GET') mutations.push({ path, method: request.method(), body: request.postDataJSON() })
      if (path === '/api/auth/refresh') {
        refreshCount++
        await new Promise(resolve => setTimeout(resolve, 100))
        data = { token: 'rotated-token', member: { id: 1, role: 'ADMIN', name: '管理員' } }
      }
      else if (path === '/api/auth/logout') data = null
      else if (path === '/api/cart') data = { items: [], totalQuantity: 0, totalAmount: 0 }
      else if (path === '/api/wishlist/ids') data = []
      else if (path === '/api/products/brands') data = ['Test']
      else if (path === '/api/categories' || path === '/api/categories/tree') data = [category]
      else if (path === '/api/categories/slug/food') data = category
      else if (path === '/api/admin/categories') data = paging([category])
      else if (path === '/api/admin/products' && request.method() === 'POST') {
        const body = request.postDataJSON()
        products = [...products, { ...body, id: 2, totalStock: body.skus[0].stock }]
        data = products[1]
      } else if (path === '/api/admin/products' || path === '/api/products') data = paging(products)
      else if (path === '/api/admin/products/1' || path === '/api/products/1') data = products[0]
      else if (path === '/api/admin/products/2') data = products[1]
      else if (path === '/api/products/1/related') data = []
      else if (path === '/api/admin/orders') data = paging([order])
      else if (path === '/api/orders') data = { ...paging([order]), page: Number(url.searchParams.get('page') || 0), totalPages: 2, last: url.searchParams.get('page') === '1' }
      else if (path === '/api/admin/orders/1') data = order
      else if (path === '/api/admin/orders/1/shipment') {
        if (request.method() === 'POST') { shipment = { ...request.postDataJSON(), shippedAt: '2026-10-08T12:00:00' }; order = { ...order, status: 'SHIPPED' } }
        data = shipment
      }
      else if (path === '/api/admin/orders/1/status') { order = { ...order, status: request.postDataJSON().status }; data = order }
      else if (path === '/api/members/me') data = { id: 1, name: '管理員', email: 'admin@test.local', role: 'ADMIN' }
      else if (path === '/api/members/me/addresses') data = []
      else throw new Error(`Unhandled stub request ${request.method()} ${path}`)
      await route.fulfill({ json: { success: true, code: 200, message: 'OK', data } })
    })
    const base = process.env.SMOKE_URL || 'http://127.0.0.1:5175'
    await page.goto(`${base}/category/food`)
    await page.getByRole('heading', { name: '飼料', exact: true }).waitFor()
    expireRequests = true
    await page.evaluate(async () => {
      const { default: http } = await import('/src/api/http.js')
      await Promise.all([http.get('/members/me'), http.get('/members/me')])
    })
    assert.equal(refreshCount, 1, 'Concurrent 401s must rotate only once')
    assert.equal(await page.evaluate(() => localStorage.getItem('catshop_token')), 'rotated-token')
    await page.getByPlaceholder('搜尋商品名稱…').fill('飼料')
    await page.getByRole('button', { name: '搜尋', exact: true }).click()
    await page.waitForURL('**/search?**')
    assert.equal(new URL(page.url()).searchParams.get('q'), '飼料')
    await page.getByText('測試飼料', { exact: true }).click()
    await page.getByRole('heading', { name: '測試飼料', exact: true }).waitFor()
    await page.getByText('雞肉', { exact: true }).waitFor()

    await page.goto(`${base}/admin/products`)
    await page.getByRole('button', { name: '新增', exact: true }).click()
    await page.getByLabel('名稱', { exact: true }).fill('新商品')
    await page.getByLabel('網址代稱（小寫英數字與連字號）').fill('new-product')
    await page.getByPlaceholder('SKU 編號', { exact: true }).fill('NEW-1')
    await page.getByPlaceholder('規格名稱', { exact: true }).fill('2kg')
    await page.getByRole('button', { name: '儲存', exact: true }).click()
    await page.getByText('已儲存', { exact: true }).waitFor()
    const created = mutations.find(item => item.path === '/api/admin/products' && item.method === 'POST')
    assert.equal(created.body.skus[0].skuCode, 'NEW-1')
    assert.equal(created.body.name, '新商品')

    await page.goto(`${base}/admin/orders`)
    await page.getByRole('link', { name: '詳情', exact: true }).click()
    await page.getByRole('link', { name: '登錄出貨', exact: true }).click()
    await page.getByLabel('物流公司', { exact: true }).fill('測試物流')
    await page.getByLabel('物流單號', { exact: true }).fill('TEST123')
    await page.getByRole('button', { name: '確認出貨', exact: true }).click()
    await page.getByRole('button', { name: '已完成', exact: true }).waitFor()
    assert.equal(mutations.find(item => item.path.endsWith('/shipment')).body.trackingNumber, 'TEST123')

    await page.goto(`${base}/orders`)
    await page.getByRole('link', { name: '詳情', exact: true }).waitFor()
    await page.getByRole('button', { name: '下一頁', exact: true }).click()
    await page.getByText('2 / 2', { exact: true }).waitFor()
    await page.getByRole('link', { name: '詳情', exact: true }).waitFor()

    await page.goto(`${base}/checkout`)
    await page.getByLabel('付款方式').selectOption('MOCK')
    assert.equal(await page.getByLabel('付款方式').inputValue(), 'MOCK')
    await page.setViewportSize({ width: 390, height: 844 })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'Mobile checkout overflows viewport')
    assert.deepEqual(errors, [])
    console.log('PASS: category route, search, product information, admin product creation, COD shipment, order pagination, payment selector, mobile layout; no browser errors.')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
