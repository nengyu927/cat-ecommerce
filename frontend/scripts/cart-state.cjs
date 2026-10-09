const { chromium } = require('playwright')
const assert = require('node:assert/strict')

async function main() {
  const { createServer } = await import('vite')
  const server = await createServer({ mode: 'storefront', server: { host: '127.0.0.1', port: 5178, strictPort: true } })
  let browser
  // Blank document avoids app watchers: test the actual Pinia store in isolation.
  server.middlewares.use((req, res, next) => {
    if (req.url !== '/cart-state-harness') return next()
    res.setHeader('Content-Type', 'text/html'); res.end('<!doctype html><html><body>Cart regression</body></html>')
  })
  try {
    await server.listen()
    browser = await chromium.launch({ channel: 'msedge', headless: true })
    const page = await browser.newPage(), errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto('http://127.0.0.1:5178/cart-state-harness')
    const passed = await page.evaluate(async () => {
      const { runRegression } = await import('/scripts/cart-state-harness.js')
      return runRegression()
    })
    assert.equal(passed.length, 8); assert.deepEqual(errors, [])
    passed.forEach(name => console.log(`PASS: ${name}`))
  } finally { await browser?.close(); await server.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
