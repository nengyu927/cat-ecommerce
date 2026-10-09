const {chromium}=require('playwright'),assert=require('node:assert/strict'),quote=require('./checkout-quote-fixture.cjs')
const cart={items:[{id:1,skuId:11,productName:'測試商品',unitPrice:100,quantity:2,subtotal:200,stock:5}],totalAmount:200,totalQuantity:2}
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5216,strictPort:true}})
 await server.listen();let browser
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const code of ['COUPON_EXPIRED','COUPON_INACTIVE','COUPON_NOT_STARTED','COUPON_EXHAUSTED','COUPON_MINIMUM_NOT_MET','COUPON_ALREADY_USED','COUPON_NOT_FOUND','COUPON_INVALID_FORMAT']){
   const width=['COUPON_EXHAUSTED','COUPON_MINIMUM_NOT_MET'].includes(code)?390:1440
   const ctx=await browser.newContext({viewport:{width,height:900}}),state={failure:code,orders:0,orderFailure:false,baseFailure:false},errors=[]
   ctx.setDefaultTimeout(12000)
   await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'coupon'})))
   await ctx.route(url=>url.pathname.startsWith('/api/'),r=>{
    const url=new URL(r.request().url()),ok=data=>r.fulfill({json:{success:true,data}}),fail=code=>r.fulfill({status:400,json:{success:false,error:{code,message:'優惠拒絕'}}})
    if(url.pathname==='/api/cart')return ok(cart)
    if(url.pathname==='/api/wishlist/ids')return ok([])
    if(url.pathname==='/api/members/me/addresses')return ok([{receiverName:'收件人',phone:'0912345678',city:'台北市',line1:'地址',isDefault:true}])
    if(url.pathname==='/api/orders/request-identity')return ok({version:3})
    if(url.pathname==='/api/orders/checkout-quote')return state.baseFailure?fail('FAIL'):ok(quote(cart))
    if(url.pathname==='/api/orders/quote')return state.failure?fail(state.failure):ok(quote(cart,20,'DEMO'))
    if(url.pathname==='/api/orders'){state.orders++;return state.orderFailure?fail(code):fail('TEST_STOP')}
    return ok([])
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   await p.goto('http://127.0.0.1:5216/checkout')
   const submit=p.getByRole('button',{name:'確認下單',exact:true}),apply=p.getByRole('button',{name:'套用優惠券',exact:true}),input=p.getByLabel('優惠碼',{exact:true}),remove=p.getByRole('button',{name:'移除優惠並確認原價',exact:true})
   await p.getByLabel('收件人 *',{exact:true}).waitFor();await input.fill('DEMO');assert.equal(await submit.isDisabled(),true)
   await apply.click();await p.locator('form[aria-label="套用優惠券"] [role="alert"]').waitFor()
   assert.equal(await input.inputValue(),'DEMO');assert.equal(await submit.isDisabled(),true)
   await submit.dispatchEvent('click');assert.equal(state.orders,0)
   // Clearing the text is not a hidden opt-out from the confirmation requirement.
   await input.fill('');assert.equal(await submit.isDisabled(),true)
   state.baseFailure=true;await remove.click();await p.getByText(/優惠尚未移除/).waitFor();assert.equal(await submit.isDisabled(),true)
   state.baseFailure=false;await remove.click();await p.getByText('優惠已移除，請確認最新原價合計後，再自行送出訂單。',{exact:true}).waitFor()
   assert.equal(await submit.isEnabled(),true);assert.equal(state.orders,0)
   await input.fill('DEMO');state.failure=null;await apply.click();await p.getByText('已套用 DEMO，折抵 NT$ 20',{exact:true}).waitFor()
   assert.equal(await submit.isEnabled(),true)
   // Quote success cannot reserve a coupon. Another checkout may exhaust it before create.
   state.orderFailure=true;await require('./confirm-checkout.cjs')(p);await p.locator('form[aria-label="套用優惠券"] [role="alert"]').waitFor()
   assert.equal(await input.inputValue(),'DEMO');assert.equal(await submit.isDisabled(),true);assert.equal(state.orders,1)
   assert.equal(await p.getByLabel('收件人 *',{exact:true}).inputValue(),'收件人')
   assert.equal(await p.evaluate(()=>sessionStorage.getItem('catshop_checkout_attempt')),null)
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true)
   assert.deepEqual(errors,[]);await ctx.close();console.log('PASS coupon recovery '+code+': quote failure, explicit removal/requote, blank-code guard and create rejection')
  }
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
