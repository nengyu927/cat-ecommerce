const {chromium}=require('playwright'),assert=require('node:assert/strict')
const quoteFixture=require('./checkout-quote-fixture.cjs')
const quoteCart={items:[{id:1,skuId:1,productId:1,productName:'商品',quantity:2,unitPrice:200,subtotal:400,stock:10,available:true}],totalAmount:400,totalQuantity:2}
async function main(){
 const {createServer}=await import('vite')
 const admin=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5190,strictPort:true}})
 const front=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5191,strictPort:true}})
 let browser;const errors=[]
 try{
  await admin.listen();await front.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,role,test){
   const context=await browser.newContext({viewport:{width:390,height:844},timezoneId:'Asia/Taipei'})
   await context.addInitScript(role=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Test',role},epoch:'coupon-test',reason:''})),role)
   const state={calls:[],coupons:[],fail:false,slow:false,delayDetail:false}
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async r=>{
    const path=new URL(r.request().url()).pathname,method=r.request().method(),body=method==='GET'?null:r.request().postDataJSON()
    state.calls.push({path,method,body});const ok=data=>r.fulfill({json:{success:true,data}})
    if(path.endsWith('/orders/request-identity'))return ok({version:3})
    if(path.endsWith('/orders/checkout-quote'))return ok(quoteFixture(quoteCart))
    if(path.endsWith('/orders/quote')){
     if(state.slow)await new Promise(resolve=>setTimeout(resolve,400))
     if(state.fail)return r.fulfill({status:400,json:{success:false,error:{message:'優惠券名額已用完'}}})
     return ok(quoteFixture(quoteCart,100,body.code.toUpperCase()))
    }
    if(path.includes('/admin/coupons')){
     if(method==='POST'){state.coupons.push({...body,id:1,version:0});return ok(state.coupons[0])}
     if(method==='PUT'){
      if(state.fail)return r.fulfill({status:409,json:{success:false,error:{message:'優惠券已變更，請重新載入'}}})
      state.coupons=[{...body,id:1,version:1}];return ok(state.coupons[0])
     }
     if(/\/coupons\/\d+$/.test(path)){
      const found=state.coupons.find(c=>String(c.id)===path.split('/').pop())
      if(state.delayDetail&&path.endsWith('/1'))await new Promise(resolve=>setTimeout(resolve,400))
      return found?ok(found):r.fulfill({status:404,json:{success:false,error:{message:'找不到優惠券'}}})
     }
     return ok({content:state.coupons,totalPages:1})
    }
    if(path.endsWith('/orders')&&method==='POST')return ok({id:1})
    if(path.endsWith('/orders/1/shipment'))return ok(null)
    if(path.endsWith('/orders/1'))return ok({id:1,orderNo:'COUPON-ORDER',status:'PENDING',items:[],shippingFee:80,totalAmount:380,couponCode:'SAVE100',discountAmount:100,createdAt:'2026-10-08T12:00:00'})
    if(path.endsWith('/cart'))return ok({items:[{id:1,skuId:1,productId:1,productName:'商品',quantity:2,unitPrice:200,subtotal:400,stock:10,available:true}],totalAmount:400,totalQuantity:2})
    if(path.endsWith('/members/me/addresses'))return ok([])
    if(path.endsWith('/wishlist/ids'))return ok([])
    return ok({content:[],items:[],totalQuantity:0})
   })
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message))
   try{await test(page,state);console.log('PASS '+name)}finally{await context.close()}
  }
  async function checkout(p){
   await p.goto('http://127.0.0.1:5191/checkout')
   await p.getByLabel('收件人 *',{exact:true}).fill('會員')
   await p.getByLabel('收件電話 *',{exact:true}).fill('0912345678')
   await p.getByLabel('收件地址 *',{exact:true}).fill('台北市')
   await p.getByLabel('優惠碼',{exact:true}).fill('save100')
  }
  await scenario('Coupon quote, checkout code and frozen order discount','CUSTOMER',async(p,s)=>{
   await checkout(p);await p.getByRole('button',{name:'套用優惠券',exact:true}).click()
   await p.getByText('已套用 SAVE100，折抵 NT$ 100',{exact:true}).waitFor()
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
   await require('./confirm-checkout.cjs')(p);await p.getByRole('heading',{name:'訂單 COUPON-ORDER',exact:true}).waitFor()
   const call=s.calls.find(c=>c.path.endsWith('/orders')&&c.method==='POST')
   assert.equal(call.body.couponCode,'SAVE100');assert.equal('discountAmount' in call.body,false)
   await p.getByText('優惠 SAVE100',{exact:true}).waitFor()
  })
  await scenario('Pending quote locks code; editing requires explicit removal before ordering','CUSTOMER',async(p,s)=>{
   s.slow=true;await checkout(p);await p.getByRole('button',{name:'套用優惠券',exact:true}).click()
   assert.equal(await p.getByLabel('優惠碼',{exact:true}).isDisabled(),true)
   await p.getByText('已套用 SAVE100，折抵 NT$ 100',{exact:true}).waitFor()
   await p.getByLabel('優惠碼',{exact:true}).fill('OTHER')
   assert.equal(await p.getByText(/已套用 SAVE100/).count(),0)
   assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
   await p.getByRole('button',{name:'移除優惠並確認原價',exact:true}).click()
   await p.getByText('優惠已移除，請確認最新原價合計後，再自行送出訂單。',{exact:true}).waitFor()
   await require('./confirm-checkout.cjs')(p);await p.waitForURL('**/orders/1')
   assert.equal(s.calls.find(c=>c.path.endsWith('/orders')&&c.method==='POST').body.couponCode,null)
  })
  await scenario('Rejected coupon shows useful error without discount','CUSTOMER',async(p,s)=>{
   s.fail=true;await checkout(p);await p.getByRole('button',{name:'套用優惠券',exact:true}).click()
   await p.getByRole('alert').getByText('優惠券名額已用完',{exact:true}).waitFor()
   assert.equal(await p.getByText(/已套用 SAVE100/).count(),0)
  })
  await scenario('Admin creates then edits coupon with version and UTC period','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5190/coupons');await p.getByRole('button',{name:'新增優惠券',exact:true}).click()
   await p.getByLabel('優惠碼',{exact:true}).fill('save100');await p.getByRole('button',{name:'儲存',exact:true}).click()
   await p.getByText('優惠券已儲存',{exact:true}).waitFor();assert.equal(s.coupons[0].code,'SAVE100')
   await p.waitForURL('**/coupons/1/edit');assert.equal(await p.getByLabel('優惠碼',{exact:true}).isDisabled(),true)
   await p.getByLabel('折抵金額',{exact:true}).fill('150');await p.getByRole('button',{name:'儲存',exact:true}).click()
   await p.getByText('優惠券已儲存',{exact:true}).waitFor()
   await p.getByRole('form',{name:'優惠券編輯'}).waitFor()
   assert.equal(s.calls.find(c=>c.method==='PUT').body.version,0);assert.equal(s.coupons[0].discountAmount,150)
  })
  await scenario('Admin stale edit is kept for correction with conflict','ADMIN',async(p,s)=>{
   s.coupons=[{id:1,code:'SAVE100',discountAmount:100,minimumAmount:0,maxUses:1,active:true,version:1,startsAt:'2026-10-01T00:00:00',endsAt:'2026-11-01T00:00:00'}];s.fail=true
   await p.goto('http://127.0.0.1:5190/coupons');await p.getByRole('button',{name:'編輯',exact:true}).click()
   await p.getByLabel('折抵金額',{exact:true}).fill('150');await p.getByRole('button',{name:'儲存',exact:true}).click()
   await p.getByRole('alert').getByText('優惠券已變更，請重新載入',{exact:true}).waitFor()
   assert.equal(await p.getByLabel('折抵金額',{exact:true}).inputValue(),'150')
  })
  const seed=()=>({id:1,code:'SAVE100',discountAmount:100,minimumAmount:0,maxUses:5,active:true,version:7,startsAt:'2026-10-01T00:00:12.1234567',endsAt:'2026-11-01T00:00:42.9876543'})
  await scenario('Direct detail, reload, safe DTO and unchanged timestamp precision','ADMIN',async(p,s)=>{
   s.coupons=[seed()];await p.goto('http://127.0.0.1:5190/coupons/1/edit?page=3')
   await p.getByRole('form',{name:'優惠券編輯'}).waitFor();await p.reload()
   await p.getByRole('form',{name:'優惠券編輯'}).waitFor()
   assert.equal(await p.getByLabel('優惠碼',{exact:true}).isDisabled(),true)
   assert.match(await p.getByLabel('開始時間（本地時間）',{exact:true}).inputValue(),/^2026-10-01T08:00:12\.123$/)
   await p.getByLabel('折抵金額',{exact:true}).fill('120');await p.getByRole('button',{name:'儲存',exact:true}).click()
   await p.getByText('優惠券已儲存',{exact:true}).waitFor()
   const payload=s.calls.find(c=>c.method==='PUT').body
   assert.equal(payload.startsAt,seed().startsAt);assert.equal(payload.endsAt,seed().endsAt);assert.equal(payload.version,7)
   assert.deepEqual(Object.keys(payload).sort(),['active','code','discountAmount','endsAt','maxUses','minimumAmount','startsAt','version'].sort())
   assert.equal(s.calls.filter(c=>c.path.endsWith('/admin/coupons')&&c.method==='GET').length,0)
   await p.getByRole('link',{name:'返回優惠券列表',exact:true}).click();await p.waitForURL('**/coupons?page=3')
   await p.goBack();await p.getByRole('form',{name:'優惠券編輯'}).waitFor()
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
  })
  await scenario('Changed local dates convert to UTC with seconds and milliseconds','ADMIN',async(p,s)=>{
   s.coupons=[seed()];await p.goto('http://127.0.0.1:5190/coupons/1/edit')
   await p.getByLabel('開始時間（本地時間）',{exact:true}).fill('2026-10-02T09:30:15.123')
   await p.getByLabel('結束時間（本地時間）',{exact:true}).fill('2026-11-02T09:30:25.456')
   await p.getByRole('button',{name:'儲存',exact:true}).click();await p.getByText('優惠券已儲存',{exact:true}).waitFor()
   assert.equal(s.calls.find(c=>c.method==='PUT').body.startsAt,'2026-10-02T01:30:15.123')
   assert.equal(s.calls.find(c=>c.method==='PUT').body.endsAt,'2026-11-02T01:30:25.456')
   await p.getByLabel('折抵金額',{exact:true}).fill('130');await p.getByRole('button',{name:'儲存',exact:true}).click()
   await p.getByText('優惠券已儲存',{exact:true}).waitFor();assert.equal(s.calls.filter(c=>c.method==='PUT')[1].body.version,1)
  })
  await scenario('Invalid range blocks save and dirty navigation can be cancelled','ADMIN',async(p,s)=>{
   s.coupons=[seed()];await p.goto('http://127.0.0.1:5190/coupons/1/edit')
   await p.getByLabel('結束時間（本地時間）',{exact:true}).fill('2026-09-01T08:00')
   await p.getByRole('button',{name:'儲存',exact:true}).click();await p.getByText('結束時間須晚於開始時間',{exact:true}).waitFor()
   assert.equal(s.calls.some(c=>c.method==='PUT'),false)
   await p.getByRole('link',{name:'返回優惠券列表',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.match(p.url(),/\/1\/edit$/)
   await p.getByRole('link',{name:'返回優惠券列表',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/coupons')
  })
  await scenario('Missing detail retries without a writable empty form','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5190/coupons/1/edit');await p.getByText('找不到優惠券',{exact:true}).waitFor()
   assert.equal(await p.getByRole('form',{name:'優惠券編輯'}).count(),0)
   s.coupons=[seed()];await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('form',{name:'優惠券編輯'}).waitFor()
  })
  await scenario('Invalid ID does not request an API and legacy detail redirects','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5190/coupons/0/edit');await p.getByText('優惠券編號格式不正確',{exact:true}).waitFor()
   assert.equal(s.calls.some(c=>c.path.includes('/admin/coupons')),false)
   s.coupons=[seed()];await p.goto('http://127.0.0.1:5190/admin/coupons/1/edit?page=2');await p.waitForURL('**/coupons/1/edit?page=2')
   await p.getByRole('form',{name:'優惠券編輯'}).waitFor()
  })
  await scenario('Customer cannot access protected coupon edit','CUSTOMER',async(p,s)=>{
   await p.goto('http://127.0.0.1:5190/coupons/1/edit');await p.waitForURL('**/login**')
   assert.equal(s.calls.some(c=>c.path.includes('/admin/coupons')),false)
  })
  await scenario('Conflict reload respects dirty confirmation and fetches latest version','ADMIN',async(p,s)=>{
   s.coupons=[seed()];s.fail=true;await p.goto('http://127.0.0.1:5190/coupons/1/edit')
   await p.getByLabel('折抵金額',{exact:true}).fill('150');await p.getByRole('button',{name:'儲存',exact:true}).click()
   await p.getByText('優惠券已變更，請重新載入',{exact:true}).waitFor()
   s.coupons=[{...seed(),version:8,discountAmount:200}]
   await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(await p.getByLabel('折抵金額',{exact:true}).inputValue(),'150')
   await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'放棄修改並重新載入',exact:true}).click()
   await p.waitForFunction(()=>document.querySelector('input[type=number]')?.value==='200')
   s.fail=false;await p.getByLabel('折抵金額',{exact:true}).fill('210');await p.getByRole('button',{name:'儲存',exact:true}).click()
   await p.getByText('優惠券已儲存',{exact:true}).waitFor();assert.equal(s.calls.filter(c=>c.method==='PUT')[1].body.version,8)
  })
  await scenario('Delayed detail cannot replace a newer route','ADMIN',async(p,s)=>{
   s.coupons=[seed(),{...seed(),id:2,code:'SECOND'}];s.delayDetail=true
   await p.goto('http://127.0.0.1:5190/coupons/1/edit')
   await p.getByRole('link',{name:'返回優惠券列表',exact:true}).click();await p.waitForURL('**/coupons')
   await p.getByRole('button',{name:'編輯',exact:true}).nth(1).click()
   await p.getByRole('form',{name:'優惠券編輯'}).waitFor();await p.waitForTimeout(500)
   assert.equal(await p.getByLabel('優惠碼',{exact:true}).inputValue(),'SECOND')
  })
  await scenario('Invalid list page makes no coupon request and can be cleared','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5190/coupons?page=-1');await p.getByRole('alert').waitFor()
   assert.equal(s.calls.some(c=>c.path.includes('/admin/coupons')),false)
   await p.getByRole('button',{name:'清除頁碼',exact:true}).click();await p.waitForURL('**/coupons')
   await p.getByText('尚無優惠券',{exact:true}).waitFor()
  })
  assert.deepEqual(errors,[])
 }finally{if(browser)await browser.close();await admin.close();await front.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
