// Focused UI/race regression with deterministic stub API, separate from real SQL E2E.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const quoteFixture = require('./checkout-quote-fixture.cjs')
const base = process.env.SMOKE_URL || 'http://127.0.0.1:5175'
const member = { id: 1, name: '測試會員', role: 'CUSTOMER', email: 'member@example.com' }
const cart = { id: 1, items: [{ id: 1, skuId: 1, productName: '測試商品', specName: '1kg', unitPrice: 100, quantity: 1, subtotal: 100, stock: 10 }], totalQuantity: 1, totalAmount: 100 }
const order = { id: 1, orderNo: 'TESTORDER', status: 'PAID', paymentMethod: 'MOCK', totalAmount: 180, shippingFee: 80,
  receiverName: '收件人', receiverPhone: '0912345678', shippingAddress: '台北市', items: [], createdAt: '2026-10-07T10:00:00' }
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))

async function main() {
  let server
  if(!process.env.SMOKE_URL){const {createServer}=await import('vite');server=await createServer({server:{host:'127.0.0.1',port:5175,strictPort:true}});await server.listen()}
  const browser = await chromium.launch({ channel: 'msedge', headless: true }).catch(async error=>{await server?.close();throw error})
  let passed = 0
  const pageErrors = []
  async function scenario(name, test, loggedIn = true) {
    const context = await browser.newContext()
    const page = await context.newPage()
    page.on('pageerror', error => pageErrors.push(`${name}: ${error.message}`))
    const state = { cartFailure: false, failCheckout: false, writes: [], refreshCount: 0, pendingMember: false,
      refreshDelay: 0, requestStarted: false, lateStatus: 200, failCancel: false, cartDelay: 0 }
    await page.route(url => !['localhost', '127.0.0.1'].includes(url.hostname), route => route.abort())
    await context.route(url => url.pathname.startsWith('/api/'), async route => {
      const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
      const success = data => route.fulfill({ json: { success: true, code: method === 'POST' ? 201 : 200, data } })
      const failure = (status, message, errors = []) => route.fulfill({ status, json: { success: false, code: status, message,
        error: { code: 'TEST_ERROR', message }, errors } })
      if (method !== 'GET') state.writes.push({ path, method })
      if (path === '/api/wishlist/ids') return success([])
      if (path === '/api/orders/request-identity') return success({version:3})
      if (path === '/api/orders/checkout-quote') return success(quoteFixture(cart))
      if (path === '/api/cart') {
        if (state.cartDelay) { state.requestStarted = true; await delay(state.cartDelay) }
        if (state.cartFailure) return failure(503, '購物車暫時無法載入')
        return success(cart)
      }
      if (path === '/api/members/me' && state.pendingMember && request.headers().authorization === 'Bearer initial-token') {
        state.requestStarted = true
        await delay(350)
        if (state.lateStatus !== 200) return failure(state.lateStatus, '舊登入失效')
        return success({ ...member, name: '不應恢復的舊會員' })
      }
      if (path === '/api/members/me' && method === 'PUT') {
        await delay(200)
        return failure(400, '請檢查個人資料', [{ field: 'name', reason: '姓名不能空白' }])
      }
      if (path === '/api/members/me') {
        if (request.headers().authorization === 'Bearer expired') return failure(401, 'Token 過期')
        return success(request.headers().authorization === 'Bearer new-token'
          ? { id: 2, name: '新會員', role: 'CUSTOMER', email: 'new@example.com' } : member)
      }
      if (path === '/api/members/me/password') { await delay(200); return success(null) }
      if (path === '/api/members/me/addresses') return success([])
      if (path === '/api/orders' && method === 'POST') {
        await delay(200)
        if (state.failCheckout) return failure(400, '收件資料不正確', [{ field: 'receiverPhone', reason: '電話格式錯誤' }])
        return success(order)
      }
      if (path === '/api/orders/1/cancel') {
        await delay(200)
        if (state.failCancel) return failure(409, '訂單狀態已變更，請重試')
        return success({ ...order, status: 'CANCELLED' })
      }
      if (path === '/api/orders/1') return success(order)
      if (path === '/api/orders/1/shipment') return success(null)
      if (path === '/api/auth/refresh') {
        state.refreshCount++; state.requestStarted = true
        await delay(state.refreshDelay)
        return success({ token: 'refreshed', member })
      }
      if (path === '/api/auth/logout') return success(null)
      if (path === '/api/auth/login') { await delay(200); return failure(401, '電子郵件或密碼錯誤') }
      if (path === '/api/auth/register') {
        await delay(200)
        return route.fulfill({ status: 400, json: { success: false,
          error: { message: '註冊資料不正確', details: [{ field: 'phone', message: '電話超過限制' }] } } })
      }
      if (path === '/api/categories' || path === '/api/categories/tree') return success([])
      if (path === '/api/products') return success({ content: [], totalPages: 0, totalElements: 0 })
      throw new Error(`Unhandled stability stub ${method} ${path}`)
    })
    await page.goto(base + '/login')
    if (loggedIn) {
      await page.evaluate(member => {
        localStorage.setItem('catshop_token', 'initial-token')
        localStorage.setItem('catshop_member', JSON.stringify(member))
        localStorage.setItem('catshop_session', JSON.stringify({ token: 'initial-token', member, epoch: 'session-one', reason: '' }))
      }, member)
    }
    try { await test(page, context, state); passed++; console.log(`PASS: ${name}`) }
    finally { await context.close() }
  }
  const fillCheckout = async page => {
    await page.getByLabel('收件人 *', { exact: true }).fill('收件人')
    await page.getByLabel('收件電話 *', { exact: true }).fill('0912345678')
    await page.getByLabel('收件地址 *', { exact: true }).fill('台北市')
  }
  try {
    await scenario('Theme-colored header icons preserve member/cart links, compact logout and quantity badge', async page => {
      await page.goto(base + '/cart')
      const header = page.locator('header.site-header .header-actions')
      const cartLink = header.getByRole('button', { name: '購物車，1 件商品', exact: true })
      const memberLink = header.getByRole('link', { name: member.name, exact: true })
      await cartLink.waitFor()
      assert.equal(await cartLink.locator('svg').count(), 1)
      assert.equal(await cartLink.locator('.cart-badge').innerText(), '1')
      assert.equal(await memberLink.locator('svg').count(), 1)
      assert.equal((await memberLink.innerText()).trim(), '')
      assert.equal(await memberLink.getAttribute('title'), `會員中心：${member.name}`)
      assert.equal(await memberLink.evaluate(link => getComputedStyle(link).color), 'rgb(52, 89, 76)')
      const logoutButton = header.getByRole('button', { name: '登出', exact: true })
      assert.equal(await logoutButton.evaluate(button => button.getBoundingClientRect().height), 30)
      assert.equal(await logoutButton.locator('svg').count(), 1)
      assert.equal((await logoutButton.innerText()).trim(), '')
      await memberLink.click(); await page.waitForURL('**/account')
      await cartLink.click(); await page.getByRole('button',{name:'查看完整購物車',exact:true}).click(); await page.waitForURL('**/cart')
      await page.setViewportSize({ width: 390, height: 844 })
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
      assert.equal(await memberLink.locator('svg').evaluate(svg => svg.getBoundingClientRect().width), 20)
      await logoutButton.click(); await page.waitForURL('**/login?redirect=/cart')
      assert.equal(await page.evaluate(() => localStorage.getItem('catshop_token')), null)
    })
    await scenario('Checkout load failure is visible and retry recovers', async (page, context, state) => {
      state.cartFailure = true
      await page.goto(base + '/checkout')
      await page.getByText('購物車暫時無法載入', { exact: false }).waitFor()
      assert.equal(await page.getByRole('button', { name: '確認下單', exact: true }).count(), 0)
      state.cartFailure = false
      await page.getByRole('button', { name: '重試載入', exact: true }).click()
      await page.getByLabel('收件人 *', { exact: true }).waitFor()
    })
    await scenario('Checkout field errors and duplicate-submit guard', async (page, context, state) => {
      state.failCheckout = true
      await page.goto(base + '/checkout'); await fillCheckout(page)
      await page.getByRole('button', { name: '確認下單', exact: true }).evaluate(button => { button.click(); button.click() })
      await page.getByText('電話格式錯誤', { exact: true }).waitFor()
      assert.equal(await page.getByLabel('收件電話 *', { exact: true }).getAttribute('aria-invalid'), 'true')
      assert.equal(state.writes.filter(item => item.path === '/api/orders').length, 1)
    })
    await scenario('Profile errors stay beside the field and only one write is sent', async (page, context, state) => {
      await page.goto(base + '/account/profile')
      await page.getByRole('button', { name: '儲存', exact: true }).waitFor({ state: 'visible' })
      await page.waitForFunction(() => !document.querySelector('#account-name').closest('.card').querySelector('button').disabled)
      await page.getByRole('button', { name: '儲存', exact: true }).evaluate(button => { button.click(); button.click() })
      await page.getByText('姓名不能空白', { exact: true }).waitFor()
      assert.equal(state.writes.filter(item => item.path === '/api/members/me').length, 1)
    })
    await scenario('Password change redirects both tabs and clears private state', async (page, context, state) => {
      await page.goto(base + '/account/password')
      const second = await context.newPage(); await second.goto(base + '/account')
      await page.getByLabel('目前密碼', { exact: true }).fill('password123')
      await page.getByLabel('新密碼', { exact: true }).fill('newpassword123')
      await page.getByRole('button', { name: '變更密碼', exact: true }).evaluate(button => { button.click(); button.click() })
      await page.waitForURL('**/login?reason=password-changed')
      await second.waitForURL('**/login?reason=password-changed')
      await page.getByText('密碼已變更，所有裝置已登出，請重新登入', { exact: true }).waitFor()
      assert.equal(await page.evaluate(() => localStorage.getItem('catshop_token')), null)
      assert.equal(state.writes.filter(item => item.path.endsWith('/password')).length, 1)
    })
    await scenario('Late profile response cannot revive a logged-out session', async (page, context, state) => {
      await page.goto(base + '/account/profile'); await page.getByLabel('姓名', { exact: true }).waitFor()
      state.pendingMember = true
      const pending = page.evaluate(async () => {
        const { useAuthStore } = await import('/src/stores/auth.js')
        try { await useAuthStore().refreshProfile() } catch { /* expected stale response */ }
      })
      while (!state.requestStarted) await delay(10)
      await page.getByRole('button', { name: '登出', exact: true }).click()
      await pending
      assert.equal(await page.evaluate(() => localStorage.getItem('catshop_member')), null)
      await page.getByRole('link', { name: '登入', exact: true }).waitFor()
    })
    await scenario('An old 401 cannot log out a newly signed-in account', async (page, context, state) => {
      await page.goto(base + '/account/profile'); await page.getByLabel('姓名', { exact: true }).waitFor()
      state.pendingMember = true; state.lateStatus = 401
      const pending = page.evaluate(async () => {
        const { default: http } = await import('/src/api/http.js')
        try { await http.get('/members/me') } catch { /* stale 401 is rejected without clearing new login */ }
      })
      while (!state.requestStarted) await delay(10)
      await page.evaluate(async () => {
        const { useAuthStore } = await import('/src/stores/auth.js')
        useAuthStore().accept({ token: 'new-token', member: { id: 2, name: '新會員', role: 'CUSTOMER' } }, true)
      })
      await pending
      assert.equal(await page.evaluate(() => localStorage.getItem('catshop_token')), 'new-token')
      await page.getByRole('link', { name: '新會員', exact: true }).waitFor()
      assert.equal(state.refreshCount, 0)
    })
    await scenario('Failed cancellation keeps order visible and prevents duplicate calls', async (page, context, state) => {
      state.failCancel = true
      await page.goto(base + '/orders/1')
      await page.getByRole('button', { name: '取消訂單', exact: true }).evaluate(button => { button.click(); button.click() })
      await page.getByRole('button',{name:'確認取消訂單',exact:true}).evaluate(button=>{button.click();button.click()})
      await page.getByText('訂單狀態已變更，請重試', { exact: true }).waitFor()
      await page.getByRole('heading', { name: '訂單 TESTORDER', exact: true }).waitFor()
      assert.equal(state.writes.filter(item => item.path.endsWith('/cancel')).length, 1)
      state.failCancel = false
      await page.getByRole('button', { name: '取消訂單', exact: true }).click()
      await page.getByRole('button',{name:'確認取消訂單',exact:true}).click()
      await page.getByText('訂單已取消', { exact: true }).waitFor()
      assert.equal(state.writes.filter(item => item.path.endsWith('/cancel')).length, 2)
    })
    await scenario('Registration field errors use legacy details and do not refresh login failures', async (page, context, state) => {
      await page.goto(base + '/register')
      await page.getByLabel('電子郵件 *', { exact: true }).fill('new@example.com')
      await page.getByLabel('密碼 *（至少 8 字元）', { exact: true }).fill('password123')
      await page.getByLabel('姓名 *', { exact: true }).fill('會員')
      await page.getByRole('button', { name: '註冊', exact: true }).evaluate(button => { button.click(); button.click() })
      await page.getByText('電話超過限制', { exact: true }).waitFor()
      assert.equal(state.writes.filter(item => item.path.endsWith('/register')).length, 1)
      await page.goto(base + '/login')
      await page.getByLabel('電子郵件', { exact: true }).fill('member@example.com')
      await page.getByLabel('密碼', { exact: true }).fill('password123')
      await page.getByRole('button', { name: '登入', exact: true }).click()
      await page.getByText('電子郵件或密碼錯誤', { exact: true }).waitFor()
      assert.equal(state.refreshCount, 0)
    }, false)
    await scenario('Other-tab login replaces protected page data without reloading', async (page, context, state) => {
      await page.goto(base + '/account/profile')
      await page.waitForFunction(() => document.querySelector('#account-name')?.value === '測試會員')
      const second = await context.newPage(); await second.goto(base + '/login')
      await second.evaluate(async () => {
        const { useAuthStore } = await import('/src/stores/auth.js')
        useAuthStore().accept({ token: 'new-token', member: { id: 2, name: '新會員', role: 'CUSTOMER', email: 'new@example.com' } }, true)
      })
      await page.getByRole('link', { name: '新會員', exact: true }).waitFor()
      await page.waitForFunction(() => document.querySelector('#account-name')?.value === '新會員')
    })
    await scenario('Late refresh cannot restore login after logout', async (page, context, state) => {
      await page.goto(base + '/account/profile')
      await page.waitForFunction(() => document.querySelector('#account-name')?.value === '測試會員')
      state.refreshDelay = 350
      const pending = page.evaluate(async () => {
        const { useAuthStore } = await import('/src/stores/auth.js')
        const { default: http } = await import('/src/api/http.js')
        const auth = useAuthStore()
        auth.accept({ token: 'expired', member: auth.member })
        try { await http.get('/members/me') } catch { /* expected after logout */ }
      })
      while (!state.requestStarted) await delay(10)
      await page.getByRole('button', { name: '登出', exact: true }).click()
      await pending
      assert.equal(await page.evaluate(() => localStorage.getItem('catshop_token')), null)
      assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('catshop_session')).token), '')
    })
    await scenario('Late cart response cannot restore private cart after logout', async (page, context, state) => {
      await page.goto(base + '/account/profile')
      await page.waitForFunction(() => document.querySelector('#account-name')?.value === '測試會員')
      state.cartDelay = 350
      const pending = page.evaluate(async () => {
        const { useCartStore } = await import('/src/stores/cart.js')
        try { await useCartStore().fetch() } catch { /* expected after logout */ }
      })
      while (!state.requestStarted) await delay(10)
      await page.getByRole('button', { name: '登出', exact: true }).click()
      await pending
      assert.equal(await page.evaluate(async () => {
        const { useCartStore } = await import('/src/stores/cart.js')
        return useCartStore().items.length
      }), 0)
    })
    assert.deepEqual(pageErrors, [])
    console.log(`PASS STABILITY: ${passed} scenarios; no unhandled page errors.`)
  } finally { await browser.close();await server?.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
