const { chromium } = require('playwright')
const assert = require('node:assert/strict')
async function main() {
 const { createServer } = await import('vite')
 const server = await createServer({ mode: 'storefront', server: { host: '127.0.0.1', port: 5179, strictPort: true } })
 let browser
 server.middlewares.use((req, res, next) => {
   if (req.url !== '/wishlist-state-harness') return next()
   res.setHeader('Content-Type', 'text/html'); res.end('<!doctype html><html><body>Wishlist regression</body></html>')
 })
 try {
   await server.listen(); browser = await chromium.launch({ channel: 'msedge', headless: true })
   const page = await browser.newPage(), errors = []; page.on('pageerror', error => errors.push(error.message))
   await page.goto('http://127.0.0.1:5179/wishlist-state-harness')
   const passed = await page.evaluate(async () => {
     let timer
     try {
       return await Promise.race([
         (await import('/scripts/wishlist-state-harness.js')).runRegression(),
         new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Wishlist regression timed out')), 15000) })
       ])
     } finally { clearTimeout(timer) }
   })
   assert.equal(passed.length, 6); assert.deepEqual(errors, []); passed.forEach(name => console.log(`PASS: ${name}`))
 } finally { await browser?.close(); await server.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
