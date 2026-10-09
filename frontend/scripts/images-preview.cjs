// Read-only live visual QA: demo sessions, catalogue/images and drawer; no business writes.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path')
async function main(){
 const output=process.env.PREVIEW_SCREENSHOT_DIR
 assert.ok(output&&path.isAbsolute(output),'Supply an absolute screenshot output directory')
 const browser=await chromium.launch({channel:'msedge',headless:true})
 try{
  const ctx=await browser.newContext({viewport:{width:1440,height:960}}),errors=[]
  ctx.on('page',p=>p.on('pageerror',e=>errors.push(e.message)))
  const p=await ctx.newPage()
  await p.goto('http://127.0.0.1:5173/products')
  await p.locator('.product-card').first().waitFor()
  const catalogue=await p.evaluate(async()=>{const r=await fetch('/api/products?size=50');return(await r.json()).data})
  assert.equal(catalogue.content.length,10)
  for(const product of catalogue.content){
   assert.match(product.primaryImageUrl,/^\/product-images\/[a-z-]+\.png$/)
   for(const base of ['http://127.0.0.1:5173','http://127.0.0.1:5174']){
    const r=await ctx.request.get(base+product.primaryImageUrl);assert.equal(r.status(),200);assert.match(r.headers()['content-type'],/image\/png/)
   }
  }
  await p.locator('.product-thumb').evaluateAll(async images=>{await Promise.all(images.map(i=>i.decode()))})
  await p.screenshot({path:path.join(output,'product-images-preview.png'),fullPage:true})
  await p.goto('http://127.0.0.1:5173/login')
  await p.getByRole('button',{name:'一鍵登入會員（展示用）',exact:true}).click();await p.waitForURL('**/')
  await p.locator('.member-icon-link').hover()
  await p.getByRole('navigation',{name:'會員功能選單'}).waitFor()
  await p.getByRole('navigation',{name:'會員功能選單'}).evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))})
  await p.screenshot({path:path.join(output,'member-hover-live-preview.png')})
  await p.locator('header button.cart-link').hover()
  await p.getByRole('region',{name:'購物車清單'}).waitFor()
  await p.getByRole('region',{name:'購物車清單'}).evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))})
  await p.waitForFunction(()=>!document.querySelector('.header-cart-panel [role="status"]'))
  await p.screenshot({path:path.join(output,'cart-hover-live-preview.png')})
  await p.locator('header button.cart-link').click();await p.getByRole('dialog',{name:'購物車預覽'}).waitFor()
  await p.getByRole('dialog',{name:'購物車預覽'}).evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))})
  await p.waitForFunction(()=>!document.querySelector('.cart-drawer [role="status"]'))
  await p.screenshot({path:path.join(output,'cart-drawer-live-preview.png')})
  await p.keyboard.press('Escape')
  await p.getByRole('button',{name:'登出',exact:true}).click()
  const admin=await ctx.newPage();await admin.goto('http://127.0.0.1:5174/login')
  await admin.getByRole('button',{name:'一鍵登入管理員（展示用）',exact:true}).click();await admin.waitForURL('**/products')
  await admin.goto('http://127.0.0.1:5174/products/1/images');await admin.locator('img').first().waitFor()
  await admin.locator('img').first().evaluate(i=>i.decode())
  await admin.screenshot({path:path.join(output,'admin-product-images-preview.png'),fullPage:true})
  await admin.getByRole('button',{name:'登出',exact:true}).click()
  assert.deepEqual(errors,[])
  console.log('PASS LIVE: all ten database image mappings, twenty frontend/Admin asset responses, decoded catalogue/Admin images, live drawer and screenshots; no business mutations.')
 }finally{await browser.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
