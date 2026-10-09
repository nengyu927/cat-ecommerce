// Local live-preview check. Only demo login/logout and reads; no cart/order/product changes.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path')
async function main(){
 const output=process.env.PREVIEW_SCREENSHOT_DIR
 assert.ok(output && path.isAbsolute(output),'Supply an absolute screenshot output directory')
 const browser=await chromium.launch({channel:'msedge',headless:true})
 try {
  const ctx=await browser.newContext({viewport:{width:1440,height:960}})
  const errors=[];ctx.on('page',p=>p.on('pageerror',e=>errors.push(e.message)))
  await ctx.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
  for(const [base,button,heading,file] of [
   ['http://127.0.0.1:5173','一鍵登入會員（展示用）',null,'storefront-preview.png'],
   ['http://127.0.0.1:5174','一鍵登入管理員（展示用）','商品管理','admin-preview.png']]){
   const p=await ctx.newPage();p.setDefaultTimeout(15000)
   await p.goto(base+'/login')
   await p.getByRole('button',{name:button,exact:true}).click()
   await p.waitForURL(url=>url.pathname===(heading?'/products':'/'))
   if(heading){await p.getByRole('heading',{name:heading,exact:true}).waitFor();await p.locator('tbody tr').first().waitFor()}
   else {await p.getByRole('link',{name:'會員中心',exact:true}).waitFor();await p.locator('.product-card').first().waitFor()}
   const contract=await p.evaluate(async ()=>{
    const {orderApi}=await import('/src/api/index.js')
    return orderApi.requestIdentity()
   })
   assert.equal(contract.version,3)
   await p.screenshot({path:path.join(output,file),fullPage:true})
   const loggedOut=p.waitForResponse(r=>new URL(r.url()).pathname===(heading?'/admin-api/auth/logout':'/api/auth/logout'))
   await p.getByRole('button',{name:'登出',exact:true}).click()
   assert.equal((await loggedOut).status(),200)
   await p.waitForFunction(()=>!localStorage.getItem('catshop_token') && !JSON.parse(localStorage.getItem('catshop_session')||'null')?.token)
   if(heading)await p.waitForURL(url=>url.pathname==='/login')
   await p.close()
  }
  assert.deepEqual(errors,[])
  console.log('PASS LIVE PREVIEW: separate storefront/Admin demo logins, v3 backend, screenshots and session-only logout; no data mutations.')
 }finally{await browser.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
