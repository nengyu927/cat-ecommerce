const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5213,strictPort:true}})
 await server.listen();let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const width of [1440,390]){
   const ctx=await browser.newContext({viewport:{width,height:850}})
   await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'confirmation'})))
   let writes=0,gate=null,fail=false
   const order=id=>({id,orderNo:'ORDER'+id,status:'PENDING',createdAt:'2026-10-09',items:[],totalAmount:180,shippingFee:80})
   await ctx.route(url=>url.pathname.startsWith('/api/'),async r=>{
    const pathname=new URL(r.request().url()).pathname,ok=data=>r.fulfill({json:{success:true,data}})
    if(pathname==='/api/wishlist/ids')return ok([])
    if(pathname==='/api/cart')return ok({items:[],totalQuantity:0,totalAmount:0})
    if(pathname==='/api/members/me/addresses')return ok([{id:1,receiverName:'地址測試',phone:'0912345678',city:'台北市',district:'大安區',line1:'測試路1號',isDefault:true}])
    if(r.request().method()!=='GET'){
     writes++
     if(gate)await gate.wait
     if(fail)return r.fulfill({status:409,json:{success:false,error:{message:'舊訂單錯誤'}}})
     return ok({...order(1),status:'CANCELLED'})
    }
    if(pathname.endsWith('/shipment'))return ok(null)
    if(/^\/api\/orders\/\d+$/.test(pathname))return ok(order(Number(pathname.split('/').pop())))
    return ok(null)
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   await p.goto('http://127.0.0.1:5213/account/addresses')
   const remove=p.getByRole('button',{name:'刪除地址 #1',exact:true}),dialog=p.getByRole('dialog')
   await remove.click();await dialog.waitFor()
   assert.equal(await p.evaluate(()=>document.activeElement.textContent),'保留不變')
   assert.equal(await dialog.getByText('地址測試 · 0912345678',{exact:false}).count(),1)
   for(let i=0;i<6;i++){await p.keyboard.press('Tab');assert.equal(await dialog.evaluate(d=>document.activeElement===document.body||d.contains(document.activeElement)),true)}
   await p.mouse.click(5,5);assert.equal(await dialog.isVisible(),true);assert.equal(writes,0)
   if(process.env.CONFIRMATION_SCREENSHOT_DIR){
    const path=require('node:path');assert.ok(path.isAbsolute(process.env.CONFIRMATION_SCREENSHOT_DIR))
    await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))})
    await p.screenshot({path:path.join(process.env.CONFIRMATION_SCREENSHOT_DIR,'confirmation-address-'+width+'.png')})
   }
   await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(writes,0)
   assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
   await remove.click();await dialog.getByRole('button',{name:'保留不變'}).click();assert.equal(writes,0)
   await remove.click()
   await p.evaluate(async()=>{const {default:r}=await import('/src/router/index.js');await r.push('/orders/1')})
   await dialog.waitFor({state:'hidden'});assert.equal(writes,0)
   const cancel=p.getByRole('button',{name:'取消訂單',exact:true})
   await cancel.click();await dialog.waitFor()
   await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()})
   await dialog.waitFor({state:'hidden'});await p.waitForURL('**/login?redirect=/orders/1');assert.equal(writes,0)
   await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().accept({token:'new',member:{id:2,name:'新會員',role:'CUSTOMER'}},true);const {default:r}=await import('/src/router/index.js');await r.push('/orders/1')})
   let release;gate={wait:new Promise(done=>{release=done})};fail=true
   await cancel.click();await dialog.getByRole('button',{name:'確認取消訂單',exact:true}).evaluate(b=>{b.click();b.click()})
   await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='取消中…'))
   assert.equal(writes,1)
   await p.evaluate(async()=>{const {default:r}=await import('/src/router/index.js');await r.push('/orders/2')})
   await p.getByRole('heading',{name:'訂單 ORDER2',exact:true}).waitFor()
   const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/orders/1/cancel')
   release();await(await response).finished();await p.evaluate(()=>new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done))))
   assert.equal(await p.getByText('舊訂單錯誤',{exact:true}).count(),0);assert.equal(await cancel.isEnabled(),true)
   gate=null;fail=false;await p.goto('http://127.0.0.1:5213/orders/1')
   await cancel.click();await dialog.getByRole('button',{name:'確認取消訂單',exact:true}).click()
   await p.getByText('訂單已取消',{exact:true}).waitFor();assert.equal(writes,2)
   await ctx.close();console.log('PASS confirmation: safe focus, Esc/cancel, route/member cancellation, duplicate protection, late errors and successful cancel '+width)
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
