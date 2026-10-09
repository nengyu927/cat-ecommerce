const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  try {
    const page = await browser.newPage()
    const errors = []
    const requests = []
    page.on('pageerror', e => errors.push(e.message))
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      if (new URL(route.request().url()).pathname === '/api/auth/login') {
        requests.push(route.request().postDataJSON())
        await new Promise(resolve => setTimeout(resolve, 250))
        return route.fulfill({ status: 401, json: { success: false, error: { message: '展示帳號無法登入，請確認帳號密碼' } } })
      }
      await route.fulfill({ json: { success: true, data: [] } })
    })
    const base = process.env.SMOKE_URL || 'http://127.0.0.1:5175'
    for (const [path, name, email] of [
      ['/login', '一鍵登入會員（展示用）', 'customer@catshop.local'],
      ['/admin/login', '一鍵登入管理員（展示用）', 'admin@catshop.local']
    ]) {
      await page.goto(base + path)
      await page.getByRole('heading', { name: path === '/login' ? '會員登入' : '後台登入', exact: true }).waitFor()
      const button = page.getByRole('button', { name, exact: true })
      if (process.env.DEMO_EXPECT_DISABLED === 'true') {
        assert.equal(await button.count(), 0)
        assert.equal(await page.getByRole('button', { name: '登入', exact: true }).count(), 1)
      } else {
        await button.evaluate(el => { el.click(); el.click() })
        await page.getByText('展示帳號無法登入，請確認帳號密碼', { exact: true }).waitFor()
        assert.equal(requests.length, path === '/login' ? 1 : 2)
        assert.deepEqual(requests.at(-1), { email, password: 'password123' })
        assert.equal(await button.isEnabled(), true)
      }
    }
    assert.deepEqual(errors, [])
    console.log(process.env.DEMO_EXPECT_DISABLED === 'true'
      ? 'PASS DEMO: production buttons hidden; manual login remains available.'
      : 'PASS DEMO: both demo payloads, single request on double click, failure message and retry.')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e); process.exitCode = 1 })
