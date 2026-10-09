// Live read-only theme QA. Auth demo sessions only; no business writes.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path')
async function main(){
 const output=process.env.PREVIEW_SCREENSHOT_DIR
 assert.ok(output&&path.isAbsolute(output),'Supply an absolute screenshot directory')
 const browser=await chromium.launch({channel:'msedge',headless:true})
 try{
  for(const width of [1440,768,390]){
   const ctx=await browser.newContext({viewport:{width,height:960}}),errors=[],writes=[]
   ctx.setDefaultTimeout(15000)
   ctx.on('page',p=>p.on('pageerror',e=>errors.push(e.message)))
   ctx.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/')&&!['GET','HEAD','OPTIONS'].includes(r.method())&&!new URL(r.url()).pathname.startsWith('/api/auth/'))writes.push(r.method()+' '+r.url())})
   const p=await ctx.newPage()
   await p.goto('http://127.0.0.1:5173/login')
   await p.getByRole('button',{name:'一鍵登入會員（展示用）',exact:true}).click();await p.waitForURL('**/')
   for(const [url,name,ready] of [['/','home','.product-card'],['/products','products','.product-card'],['/cart','cart','.page-title'],['/account','account','.page-title']]){
    await p.goto('http://127.0.0.1:5173'+url)
    await p.locator(ready).first().waitFor()
    // Full-page screenshots must request off-screen lazy images before decoding.
    await p.locator('img').evaluateAll(async images=>{images.forEach(i=>{i.loading='eager'});await Promise.all(images.map(i=>i.decode().catch(()=>{})))})
    assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,`${name} overflow at ${width}`)
    await p.screenshot({path:path.join(output,`theme-${name}-${width}.png`),fullPage:true})
   }
   if(width===390){
    await p.emulateMedia({reducedMotion:'reduce'})
    assert.equal(await p.locator('.header-icon-link').first().evaluate(b=>getComputedStyle(b).transitionDuration),'0s')
   }
   await p.getByRole('button',{name:'登出',exact:true}).click()
   const admin=await ctx.newPage()
   await admin.goto('http://127.0.0.1:5174/login')
   await admin.getByRole('button',{name:'一鍵登入管理員（展示用）',exact:true}).click();await admin.waitForURL('**/products')
   for(const [url,name] of [['/products','admin-products'],['/categories','admin-categories'],['/orders','admin-orders']]){
    await admin.goto('http://127.0.0.1:5174'+url)
    await admin.locator('.page-title').waitFor()
    await admin.waitForFunction(()=>!document.querySelector('.spinner'))
    assert.equal(await admin.locator('.admin-navigation svg').count(),7)
    assert.equal(await admin.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,`${name} overflow at ${width}`)
    await admin.screenshot({path:path.join(output,`theme-${name}-${width}.png`),fullPage:true})
   }
   await admin.getByRole('button',{name:'登出',exact:true}).click()
   assert.deepEqual(errors,[]);assert.deepEqual(writes,[])
   await ctx.close();console.log(`PASS theme ${width}: seven front/Admin pages, no document overflow, seven icons, no page errors or business writes`)
  }
 }finally{await browser.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
