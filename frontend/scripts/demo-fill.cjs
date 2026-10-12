const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const quote = require('./checkout-quote-fixture.cjs')

async function main() {
  const { createServer } = await import('vite')
  const server = await createServer({ mode: 'development', server: { host: '127.0.0.1', port: 5231, strictPort: true } })
  let browser
  const errors = []
  const product = { id: 1, name: '鮮雞成貓主食', slug: 'chicken-food', categoryId: 8, brand: '喵日常', basePrice: 680, status: 'ON_SALE', isActive: true,
    images: [{ id: 4, url: '/product-images/orijen-cat-dry.png', altText: '', isPrimary: true, sortOrder: 0 }],
    skus: [{ id: 11, skuCode: 'CHICKEN-1500', specName: '雞肉口味 / 1.5 公斤', price: 680, stock: 10, safetyStock: 2, isActive: true, version: 3 }] }
  const member = { id: 2, name: '林芷晴', email: 'zhiching.lin@example.com', phone: '0916283745', role: 'CUSTOMER', status: 'ACTIVE', statusVersion: 2 }
  const cart = { items: [{ id: 1, skuId: 11, productName: product.name, specName: product.skus[0].specName, unitPrice: 680, quantity: 1, subtotal: 680, stock: 10 }], totalAmount: 680, totalQuantity: 1 }
  const pageData = content => ({ content, page: 0, size: 20, totalPages: 1, totalElements: content.length, last: true })
  try {
    await server.listen()
    browser = await chromium.launch({ channel: 'msedge', headless: true })
    async function scenario(name, path, test, role = 'ADMIN', width = 390) {
      const context = await browser.newContext({ viewport: { width, height: 900 } })
      if (role) await context.addInitScript(role => localStorage.setItem('catshop_session', JSON.stringify({ token: 'fixture', member: { id: 1, name: '管理員', role }, epoch: 'demo-fill' })), role)
      const writes = []
      await context.route(url => !['localhost', '127.0.0.1'].includes(url.hostname), r => r.abort())
      await context.route(url => /^\/(api|admin-api)\//.test(url.pathname), r => {
        const url = new URL(r.request().url()), path = url.pathname.replace(/^\/admin-api/, '/api'), method = r.request().method()
        const ok = data => r.fulfill({ json: { success: true, data } })
        if (method !== 'GET') {
          writes.push({ path, method, body: r.request().postDataJSON() })
          if (path.endsWith('/orders/quote')) return ok(quote(cart, 100, 'MEOW100'))
          return r.fulfill({ status: 400, json: { success: false, error: { message: '演示驗證停止送出' } } })
        }
        if (path === '/api/members/me') return ok({ ...member, name: '陳品妤' })
        if (path.endsWith('/wishlist/ids')) return ok([])
        if (path.endsWith('/members/me/addresses')) return ok([])
        if (path === '/api/cart') return ok(cart)
        if (path.endsWith('/orders/checkout-quote')) return ok(quote(cart))
        if (path.endsWith('/products/brands')) return ok(['喵日常'])
        if (path.endsWith('/products/1/related')) return ok([])
        if (path.endsWith('/products/1')) return ok(product)
        if (path === '/api/products' || path.endsWith('/admin/products')) return ok(pageData([product]))
        if (path === '/api/categories' || path.endsWith('/categories/tree')) return ok([{ id: 8, name: '貓咪乾糧', slug: 'dry-food', isActive: true, children: [] }])
        if (path.endsWith('/admin/categories')) return ok(pageData([{ id: 8, name: '貓咪乾糧', slug: 'dry-food', isActive: true, parentId: null }]))
        if (path.endsWith('/admin/coupons/1')) return ok({ id: 1, code: 'SPRING100', discountAmount: 100, minimumAmount: 500, maxUses: 200, active: true, version: 4, startsAt: '2026-01-01T00:00:00', endsAt: '2026-12-31T00:00:00' })
        if (path.endsWith('/admin/members/2')) return ok(member)
        if (path.endsWith('/admin/members')) return ok(pageData([member]))
        if (path.endsWith('/admin/orders/1/shipment')) return ok(null)
        if (path.endsWith('/admin/orders/1')) return ok({ id: 1, orderNo: 'MD202610120001', status: 'PENDING', paymentMethod: 'COD', items: [], memberName: member.name })
        if (path.endsWith('/admin/inventory/low-stock')) return ok({ items: pageData([{ skuId: 11, skuCode: 'CHICKEN-1500', productName: product.name, stock: 2 }]) })
        if (path.endsWith('/admin/inventory/movements')) return ok(pageData([{ id: 1, skuId: 11, reason: 'ADMIN_ADJUSTMENT', productName: product.name, skuCode: 'CHICKEN-1500' }]))
        return ok(pageData([]))
      })
      const page = await context.newPage()
      page.setDefaultTimeout(10000)
      page.on('pageerror', e => errors.push(name + ': ' + e.message))
      try {
        await page.goto('http://127.0.0.1:5231' + path)
        await test(page, writes)
        console.log('PASS ' + name)
      } finally { await context.close() }
    }
    const fill = p => p.getByRole('button', { name: '一鍵填入完整資訊', exact: true }).click()
    await scenario('Registration creates complete realistic and unique drafts without sending', '/register', async (p, writes) => {
      await fill(p)
      assert.equal(await p.locator('#register-name').inputValue(), member.name)
      assert.equal(await p.locator('#register-phone').inputValue(), member.phone)
      const email = await p.locator('#register-email').inputValue()
      await fill(p)
      assert.notEqual(await p.locator('#register-email').inputValue(), email)
      assert.equal(await p.locator('form').last().evaluate(f => f.checkValidity()), true)
      assert.equal(writes.length, 0)
    }, null)
    await scenario('Login fills the seeded member account', '/login', async (p, writes) => {
      await fill(p); assert.equal(await p.locator('#login-email').inputValue(), 'customer@catshop.local'); assert.equal(writes.length, 0)
    }, null)
    await scenario('Login fills the seeded administrator account', '/admin/login', async (p, writes) => {
      await fill(p); assert.equal(await p.locator('#login-email').inputValue(), 'admin@catshop.local'); assert.equal(writes.length, 0)
    }, null)
    await scenario('Forgot password fills an existing account without requesting mail', '/forgot-password', async (p, writes) => {
      await fill(p); assert.equal(await p.locator('#reset-email').inputValue(), 'customer@catshop.local'); assert.equal(writes.length, 0)
    }, null)
    await scenario('Reset fills matching passwords while retaining token validation', '/reset-password', async (p, writes) => {
      await fill(p); assert.equal(await p.locator('#reset-password').inputValue(), 'MeowDaily!2026')
      assert.equal(await p.locator('#reset-confirm').inputValue(), 'MeowDaily!2026')
      assert.equal(await p.getByRole('button', { name: '重設密碼', exact: true }).isDisabled(), true); assert.equal(writes.length, 0)
    }, null)
    for (const route of ['profile', 'password']) await scenario('Account ' + route + ' fills without saving', '/account/' + route, async (p, writes) => {
      await fill(p)
      assert.equal(await p.locator(route === 'profile' ? '#account-name' : '#password-new').inputValue(), route === 'profile' ? member.name : 'MeowDaily!2026')
      assert.equal(writes.length, 0)
      await p.getByRole('link', { name: '返回會員中心', exact: true }).click()
      await p.getByRole('button', { name: '繼續編輯', exact: true }).click()
      assert.ok(p.url().endsWith(route))
    })
    await scenario('Address fills every input and becomes a valid unsaved draft', '/account/addresses/new', async (p, writes) => {
      await fill(p)
      assert.equal(await p.getByLabel('縣市', { exact: true }).inputValue(), '臺北市')
      assert.equal(await p.getByLabel('郵遞區號', { exact: true }).inputValue(), '106')
      assert.equal(await p.getByRole('form', { name: '收件地址編輯' }).evaluate(f => f.checkValidity()), true)
      assert.equal(writes.length, 0)
    })
    await scenario('Checkout fills recipient, address, remarks and payment without ordering', '/checkout', async (p, writes) => {
      await fill(p)
      assert.equal(await p.locator('#checkout-name').inputValue(), member.name)
      assert.ok((await p.locator('#checkout-address').inputValue()).includes('大安區'))
      assert.equal(await p.locator('#payment-method').inputValue(), 'COD')
      await p.getByRole('button', { name: '一鍵填入優惠碼', exact: true }).click()
      assert.equal(await p.locator('#coupon-code').inputValue(), 'MEOW100')
      assert.equal(writes.length, 0)
    })
    await scenario('Product draft contains description, ingredients, image and first SKU', '/admin/products/new', async (p, writes) => {
      await fill(p)
      assert.equal(await p.getByLabel('品牌', { exact: true }).inputValue(), '喵日常')
      assert.ok((await p.getByLabel('成分', { exact: true }).inputValue()).includes('牛磺酸'))
      const first = await p.getByPlaceholder('SKU 編號', { exact: true }).inputValue()
      await fill(p); assert.notEqual(await p.getByPlaceholder('SKU 編號', { exact: true }).inputValue(), first)
      assert.equal(await p.locator('form').last().evaluate(f => f.checkValidity()), true)
      assert.equal(writes.length, 0)
      await p.getByRole('button', { name: '儲存', exact: true }).click()
      assert.equal(writes[0].body.skus[0].stock, 48)
      assert.equal(writes[0].body.images.length, 1)
    })
    await scenario('Category fills valid slug and complete fields', '/admin/categories/new', async (p, writes) => {
      await fill(p); assert.equal(await p.getByRole('form', { name: '分類編輯' }).evaluate(f => f.checkValidity()), true); assert.equal(writes.length, 0)
    })
    for (const path of ['/admin/coupons/new', '/admin/coupons/1/edit']) await scenario('Coupon draft respects dates and immutable existing code: ' + path, path, async (p, writes) => {
      await fill(p)
      const code = await p.getByLabel('優惠碼', { exact: true }).inputValue()
      assert.equal(path.endsWith('edit') ? code === 'SPRING100' : /^MEOW-/.test(code), true)
      assert.ok((await p.getByLabel('結束時間（本地時間）', { exact: true }).inputValue()) > (await p.getByLabel('開始時間（本地時間）', { exact: true }).inputValue()))
      assert.equal(writes.length, 0)
      if (path.endsWith('new')) {
        await p.getByRole('button', { name: '一鍵填入前台演示優惠券（MEOW100）', exact: true }).click()
        assert.equal(await p.getByLabel('優惠碼', { exact: true }).inputValue(), 'MEOW100')
      }
    })
    await scenario('SKU row keeps version and code; new SKU gets unique draft', '/admin/products/1/skus', async (p, writes) => {
      const buttons = p.getByRole('button', { name: '一鍵填入完整資訊', exact: true })
      await buttons.first().click()
      assert.equal(await p.getByLabel('CHICKEN-1500 庫存', { exact: true }).inputValue(), '48')
      await buttons.last().click()
      assert.ok((await p.getByLabel('新 SKU 編號').inputValue()).startsWith('MD-CHICKEN-'))
      assert.equal(writes.length, 0)
      await p.getByRole('button', { name: '儲存', exact: true }).click()
      assert.equal(writes[0].body.version, 3)
      assert.equal(writes[0].body.skuCode, 'CHICKEN-1500')
    })
    await scenario('Images fill URLs and alternative text without upload', '/admin/products/1/images', async (p, writes) => {
      await fill(p); assert.equal(await p.getByLabel('圖片網址', { exact: true }).inputValue(), product.images[0].url)
      assert.ok((await p.getByLabel('圖片 1 替代文字').inputValue()).includes(product.name)); assert.equal(writes.length, 0)
    })
    await scenario('Shipping fills carrier and tracking number without shipping', '/admin/orders/1/shipment', async (p, writes) => {
      await fill(p); assert.equal(await p.getByLabel('物流公司', { exact: true }).inputValue(), '黑貓宅急便'); assert.match(await p.getByLabel('物流單號', { exact: true }).inputValue(), /^\d{12}$/); assert.equal(writes.length, 0)
    })
    await scenario('Member status reason matches intended action without submitting', '/admin/members/2', async (p, writes) => {
      await p.getByRole('button', { name: '停用會員 #2', exact: true }).click(); await fill(p)
      assert.ok((await p.locator('#member-status-reason').inputValue()).includes('異常登入')); assert.equal(writes.length, 0)
    })
    for (const path of ['/products', '/admin/products', '/admin/members', '/admin/inventory', '/admin/movements']) await scenario('Search fills existing data: ' + path, path, async (p, writes) => {
      const button = p.getByRole('button', { name: '一鍵填入搜尋條件', exact: true })
      await button.waitFor(); await p.waitForFunction(() => !document.querySelector('.demo-fill')?.disabled)
      await button.click(); assert.equal(writes.length, 0)
    }, 'ADMIN', 1440)
    await scenario('Quantity draft respects stock without adding to cart', '/products/1', async (p, writes) => {
      await p.getByRole('button', { name: '一鍵填入規格與數量', exact: true }).click(); assert.equal(await p.getByLabel('數量', { exact: true }).inputValue(), '2'); assert.equal(writes.length, 0)
    })
    assert.deepEqual(errors, [])
  } finally { await browser?.close(); await server.close() }
}
main().catch(e => { console.error(e); process.exitCode = 1 })
