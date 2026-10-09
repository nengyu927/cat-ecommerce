const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),quote=require('./checkout-quote-fixture.cjs')
const cart={items:[{id:1,skuId:11,productName:'主食',specName:'80g',unitPrice:100,quantity:2,subtotal:200,stock:5}],totalAmount:200,totalQuantity:2}
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5217,strictPort:true}})
 await server.listen();let browser
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const width of [1440,390])for(const kind of ['accept','coupon','change','logout']){
   const ctx=await browser.newContext({viewport:{width,height:900}}),bodies=[],errors=[];let identities=0
   ctx.setDefaultTimeout(12000)
   await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'review'})))
   await ctx.route(url=>url.pathname.startsWith('/api/'),r=>{
    const url=new URL(r.request().url()),ok=data=>r.fulfill({json:{success:true,data}})
    if(url.pathname==='/api/cart')return ok(cart)
    if(url.pathname==='/api/orders/checkout-quote')return ok(quote(cart))
    if(url.pathname==='/api/orders/quote')return ok(quote(cart,20,'DEMO'))
    if(url.pathname==='/api/orders/request-identity'){identities++;return ok({version:3})}
    if(url.pathname==='/api/members/me/addresses')return ok([{receiverName:'收件人',phone:'0912345678',city:'台北市',line1:'地址',isDefault:true}])
    if(url.pathname==='/api/orders'){bodies.push(r.request().postDataJSON());return r.fulfill({status:400,json:{success:false,error:{code:'TEST_STOP',message:'測試拒絕'}}})}
    return ok([])
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   await p.goto('http://127.0.0.1:5217/checkout');const field=p.getByLabel('收件人 *',{exact:true})
   await field.fill('手動收件人');await p.getByLabel('備註',{exact:true}).fill('下午配送')
   if(kind==='coupon'){await p.getByLabel('優惠碼',{exact:true}).fill('DEMO');await p.getByRole('button',{name:'套用優惠券',exact:true}).click();await p.getByText('已套用 DEMO，折抵 NT$ 20',{exact:true}).waitFor()}
   const open=()=>p.getByRole('button',{name:'確認下單',exact:true}).click(),dialog=p.getByRole('dialog',{name:'確認此次訂單',exact:true})
   await open();await dialog.waitFor()
   assert.equal(await p.evaluate(()=>document.activeElement.textContent),'返回修改')
   assert.equal(await field.isDisabled(),true);assert.deepEqual(bodies,[]);assert.equal(identities,0)
   assert.equal(await p.evaluate(()=>sessionStorage.getItem('catshop_checkout_attempt')),null)
   const text=await dialog.innerText();assert.ok(text.includes('主食 / 80g × 2'));assert.ok(text.includes('手動收件人'));assert.ok(text.includes('下午配送'));assert.ok(text.includes(kind==='coupon'?'合計：NT$ 260':'合計：NT$ 280'))
   if(kind==='accept'&&process.env.PREVIEW_SCREENSHOT_DIR){await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))});await p.screenshot({path:path.join(process.env.PREVIEW_SCREENSHOT_DIR,`checkout-review-${width}.png`)})}
   await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(await field.inputValue(),'手動收件人');assert.deepEqual(bodies,[])
   await open();await dialog.waitFor()
   if(kind==='change'){
    await p.evaluate(async()=>{const {useCartStore}=await import('/src/stores/cart.js');useCartStore().items[0].quantity=1})
    await dialog.waitFor({state:'hidden'});await p.getByText('結帳內容已變更，請檢查最新資料後重新確認下單。',{exact:true}).waitFor();assert.deepEqual(bodies,[])
   }else if(kind==='logout'){
    await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()})
    await p.waitForURL(u=>u.pathname==='/login');await dialog.waitFor({state:'hidden'});assert.deepEqual(bodies,[])
   }else{
    await dialog.getByRole('button',{name:'確認成立訂單',exact:true}).click();await p.getByText('測試拒絕',{exact:true}).waitFor()
    assert.equal(bodies.length,1);assert.equal(bodies[0].receiverName,'手動收件人');assert.equal(bodies[0].remark,'下午配送')
    assert.equal(bodies[0].expectedAmounts.totalAmount,kind==='coupon'?260:280);assert.equal(bodies[0].couponCode,kind==='coupon'?'DEMO':null)
   }
   assert.equal(await p.evaluate(()=>document.body.style.overflow),'');assert.deepEqual(errors,[])
   await ctx.close();console.log(`PASS review ${kind} ${width}: safe focus/cancel, no premature POST/storage, exact summary, state isolation`)
  }
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
