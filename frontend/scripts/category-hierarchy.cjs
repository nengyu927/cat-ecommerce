// Isolated admin UI regression: every API call is intercepted, no demo data is changed.
const { chromium } = require('playwright')
const assert = require('node:assert/strict')

async function main() {
  const { createServer } = await import('vite')
  const server = await createServer({ mode: 'admin', server: { host: '127.0.0.1', port: 5176, strictPort: true } })
  let browser
  try {
    await server.listen()
    browser = await chromium.launch({ channel: 'msedge', headless: true })
    const context = await browser.newContext()
    const member = { id: 1, name: '測試管理員', email: 'admin@test.local', role: 'ADMIN' }
    const categories = [
      { id: 1, name: '根分類', slug: 'root', parentId: null, isActive: true },
      { id: 2, name: '子分類', slug: 'child', parentId: 1, isActive: true },
      { id: 3, name: '停用孫分類', slug: 'grandchild', parentId: 2, isActive: false },
      { id: 4, name: '其他分類', slug: 'other', parentId: null, isActive: true }
    ].map(category => ({ ...category, sortOrder: 0, productCount: 0 }))
    const requests = [], failures = []
    await context.addInitScript(member => {
      localStorage.setItem('catshop_token', 'admin-test-token')
      localStorage.setItem('catshop_member', JSON.stringify(member))
      localStorage.setItem('catshop_session', JSON.stringify({ token: 'admin-test-token', member, epoch: 'category-ui', reason: '' }))
    }, member)
    await context.route(url => !['127.0.0.1', 'localhost'].includes(url.hostname), route => route.abort())
    await context.route(url => /^\/(api|admin-api)\//.test(url.pathname), async route => {
      const request = route.request(), path = new URL(request.url()).pathname
      if (path === '/admin-api/admin/categories' && request.method() === 'GET') {
        // Descendants are intentionally on a later page, including an inactive descendant.
        const page = Number(new URL(request.url()).searchParams.get('page') || 0)
        return route.fulfill({ json: { success: true, data: { content: page ? categories.slice(2) : categories.slice(0, 2),
          totalPages: 2, totalElements: 4, last: page === 1 } } })
      }
      if (path === '/admin-api/admin/categories/1' && request.method() === 'PUT') {
        requests.push(request.postDataJSON())
        return route.fulfill({ status: 409, json: { success: false, error: {
          code: 'CATEGORY_HAS_CHILDREN', message: '不可將子分類或孫分類設為上層分類' } } })
      }
      failures.push(`Unhandled API: ${request.method()} ${path}`)
      return route.fulfill({ status: 500, json: { success: false } })
    })
    const page = await context.newPage()
    page.on('dialog', dialog => dialog.accept())
    page.on('pageerror', error => failures.push(error.message))
    await page.goto('http://127.0.0.1:5176/categories')
    await page.getByRole('row').filter({ hasText: '根分類' }).getByRole('button', { name: '編輯', exact: true }).click()
    const select = page.getByLabel('上層分類', { exact: true })
    await page.waitForFunction(() => document.querySelectorAll('select[aria-label="上層分類"] option').length === 2)
    assert.deepEqual(await select.locator('option').allTextContents(), ['無（根分類）', '其他分類'])
    await select.selectOption('4')
    await page.getByRole('button', { name: '儲存', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: '不可將子分類或孫分類設為上層分類' }).waitFor()
    assert.equal(requests.length, 1)
    assert.equal(requests[0].parentId, 4)
    assert.equal(await select.inputValue(), '4')
    await page.getByRole('link', { name: '返回分類列表', exact: true }).click()
    await page.getByRole('row').filter({ hasText: '子分類' }).getByRole('button', { name: '編輯', exact: true }).click()
    await select.waitFor()
    assert.deepEqual(await select.locator('option').allTextContents(), ['無（根分類）', '根分類', '其他分類'])
    await page.getByRole('link', { name: '返回分類列表', exact: true }).click()
    await page.getByRole('button', { name: '新增', exact: true }).click()
    await select.waitFor()
    assert.equal(await select.locator('option').count(), 5)
    assert.deepEqual(failures, [])
    console.log('PASS category hierarchy UI: paged/inactive descendants excluded, valid parents retained, conflict preserves form, new category offers all parents; no real API writes.')
  } finally {
    if (browser) await browser.close()
    await server.close()
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
