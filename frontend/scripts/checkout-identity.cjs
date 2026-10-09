const {chromium}=require('playwright'),assert=require('node:assert/strict')
const quoteFixture=require('./checkout-quote-fixture.cjs')
const KEY='catshop_checkout_attempt'
const cart=q=>({items:q?[{id:1,skuId:11,productName:'原商品',stock:10,quantity:q,unitPrice:100,subtotal:100*q}]:[],totalQuantity:q,totalAmount:100*q})
const order={id:88,orderNo:'RESTORED88',status:'PAID',items:[],receiverName:'原收件人',receiverPhone:'0922222222',shippingAddress:'原地址',remark:'原備註',paymentMethod:'MOCK',shippingFee:80,totalAmount:280,createdAt:'2026-10-09T01:00:00Z'}
const gate=()=>{let release;return {wait:new Promise(resolve=>{release=resolve}),release:()=>release()}}
async function main(){
 const {createServer}=await import('vite');let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const [mode,port] of [['storefront',5213],['development',5214]]){
   const server=await createServer({mode,server:{host:'127.0.0.1',port,strictPort:true}})
   try{
    await server.listen()
    async function scenario(name,test){
     const ctx=await browser.newContext(),s={bodies:[],lookups:[],abort:true,status:200,result:order,lookupStatus:200,contractVersion:3,data:cart(2),postGate:null,lookupGate:null,conflictCode:'REQUEST_ID_CONFLICT',quoteFail:false,fee:null},unexpected=[]
     await ctx.addInitScript(()=>{
      if(!localStorage.getItem('catshop_session'))localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'identity'}))
     })
     await ctx.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
     await ctx.route(url=>url.pathname.startsWith('/api/'),async r=>{
      const path=new URL(r.request().url()).pathname,ok=data=>r.fulfill({json:{success:true,data}}),fail=(status,message,code='TEST')=>r.fulfill({status,json:{success:false,error:{code,message}}})
      if(path==='/api/wishlist/ids')return ok([])
      if(path==='/api/orders/request-identity')return ok({version:s.contractVersion})
      if(path==='/api/orders/checkout-quote'){
       if(s.quoteFail)return fail(503,'報價暫時失敗')
       const q=quoteFixture(s.data)
       if(s.fee!=null){q.shippingFee=s.fee;q.totalAmount=q.goodsTotal+s.fee}
       return ok(q)
      }
      if(path==='/api/cart')return ok(s.data)
      if(path==='/api/members/me/addresses')return ok([{id:1,receiverName:'預設人',phone:'0911111111',city:'預設地址',line1:'',isDefault:true}])
      if(path==='/api/orders/quote')return ok(quoteFixture(s.data,10,'SAVE'))
      if(path==='/api/auth/logout')return ok(null)
      if(path.startsWith('/api/orders/requests/')){
       s.lookups.push(path);const status=s.lookupStatus
       if(s.lookupGate)await s.lookupGate.wait
       return status===200?ok(s.result):fail(status,status===404?'未找到':'查詢暫時失敗')
      }
      if(path==='/api/orders'&&r.request().method()==='POST'){
       s.bodies.push(r.request().postDataJSON());const abort=s.abort,status=s.status
       if(s.postGate)await s.postGate.wait
       if(abort)return r.abort('failed')
       if(status===409)return fail(409,s.conflictCode==='CHECKOUT_PRICE_CONFLICT'?'商品價格、優惠或運費已變更':s.conflictCode==='CART_SNAPSHOT_CONFLICT'?'購物車內容已變更':'識別碼內容不同',s.conflictCode)
       return ok(order)
      }
      if(path==='/api/orders/88')return ok(order)
      if(path==='/api/orders/88/shipment')return ok(null)
      unexpected.push(path);return fail(404,'Unexpected')
     })
     const p=await ctx.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>errors.push(e.message))
     try{
      await p.goto('http://127.0.0.1:'+port+'/checkout')
      await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='確認下單'&&!b.disabled))
      await p.getByLabel('收件人 *',{exact:true}).fill('原收件人')
      await p.getByLabel('收件電話 *',{exact:true}).fill('0922222222')
      await p.getByLabel('收件地址 *',{exact:true}).fill('原地址')
      await p.getByLabel('備註',{exact:true}).fill('原備註')
      await p.getByLabel('付款方式',{exact:true}).selectOption('MOCK')
      await test(p,s);assert.deepEqual(unexpected,[]);console.log('PASS '+mode+': '+name)
     }finally{s.postGate?.release();s.lookupGate?.release();await ctx.close()}
    }
    const unknown=async p=>{
     await require('./confirm-checkout.cjs')(p)
     await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).waitFor()
    }
    const lookup=p=>p.getByRole('button',{name:'查詢此次下單結果',exact:true})
    const record=p=>p.evaluate(key=>JSON.parse(sessionStorage.getItem(key)),KEY)
    const settle=async(p,response)=>{await (await response).finished();await p.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))}
    await scenario('reload restores frozen request, lookup confirms order without deleting new cart',async(p,s)=>{
     await unknown(p);const saved=await record(p);assert.match(saved.requestId,/^[0-9a-f-]{36}$/)
     assert.equal(saved.payload.cartSnapshot,'[[11,2]]')
     s.data=cart(3);await p.reload();await lookup(p).waitFor()
     assert.equal(await p.getByLabel('收件人 *',{exact:true}).inputValue(),'原收件人')
     assert.equal(await p.getByLabel('備註',{exact:true}).isDisabled(),true)
     assert.equal(await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).isDisabled(),true)
     await lookup(p).click();await p.waitForURL('**/orders/88')
     await p.getByRole('heading',{name:'訂單 RESTORED88',exact:true}).waitFor()
     assert.equal(await record(p),null);assert.equal(s.bodies.length,1)
     assert.deepEqual(s.lookups,['/api/orders/requests/'+saved.requestId])
     assert.equal(await p.evaluate(async()=>{const {useCartStore}=await import('/src/stores/cart.js');return useCartStore().count}),3)
    })
    await scenario('404 cannot unlock new request; retry sends identical original coupon and UUID with empty cart',async(p,s)=>{
     await p.getByLabel('優惠碼',{exact:true}).fill('SAVE')
     await p.getByRole('button',{name:'套用優惠券',exact:true}).click();await p.getByText('已套用 SAVE，折抵 NT$ 10',{exact:true}).waitFor()
     await unknown(p);s.data=cart(0);s.lookupStatus=404
     await p.reload();await lookup(p).waitFor();await lookup(p).click()
     await p.getByText('尚未查到已成立訂單，原請求可能仍在處理；請以相同識別碼和原內容重試，勿換新碼。',{exact:true}).waitFor()
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     assert.equal(await p.getByLabel('優惠碼',{exact:true}).isDisabled(),true)
     s.abort=false;await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).click();await p.waitForURL('**/orders/88')
     assert.deepEqual(s.bodies[1],s.bodies[0]);assert.equal(s.bodies[1].couponCode,'SAVE')
     assert.equal(await record(p),null)
    })
    await scenario('lookup failure preserves identity and can retry read without extra POST',async(p,s)=>{
     await unknown(p);s.lookupStatus=503;await lookup(p).click()
     await p.getByRole('alert').filter({hasText:'查詢暫時失敗'}).waitFor()
     assert.equal((await record(p)).requestId,s.bodies[0].requestId)
     s.lookupStatus=200;await lookup(p).click();await p.waitForURL('**/orders/88')
     assert.equal(s.bodies.length,1);assert.equal(s.lookups.length,2)
    })
    await scenario('expired personal data is stripped without losing lookup identity or unlocking checkout',async(p,s)=>{
     await unknown(p)
     await p.evaluate(key=>{const a=JSON.parse(sessionStorage.getItem(key));a.createdAt=Date.now()-3*60*60*1000;sessionStorage.setItem(key,JSON.stringify(a))},KEY)
     await p.reload();await lookup(p).waitFor()
     assert.equal((await record(p)).payload,undefined)
     assert.equal(await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).isDisabled(),true)
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     assert.equal(await p.getByLabel('收件人 *',{exact:true}).inputValue(),'')
     s.lookupStatus=404;await lookup(p).click()
     await p.getByText('尚未查到已成立訂單，原收件內容已逾期；請聯絡商店確認，勿換新識別碼重送。',{exact:true}).waitFor()
     assert.equal(await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).isDisabled(),true)
     s.lookupStatus=200
     await lookup(p).click();await p.waitForURL('**/orders/88');assert.equal(s.bodies.length,1)
    })
    await scenario('storage write failure stops POST before sending',async(p,s)=>{
     await p.evaluate(()=>{const set=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){if(this===sessionStorage&&k==='catshop_checkout_attempt')throw new Error('storage disabled');return set.call(this,k,v)}})
     await require('./confirm-checkout.cjs')(p)
     await p.getByRole('alert').filter({hasText:'本次未送出'}).waitFor();assert.equal(s.bodies.length,0)
    })
    await scenario('switching login erases old personal data; old successful response cannot affect new member',async(p,s)=>{
     s.abort=false;s.postGate=gate();const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/orders')
     const sent=p.waitForRequest(r=>new URL(r.url()).pathname==='/api/orders'&&r.method()==='POST')
     await require('./confirm-checkout.cjs')(p)
     await sent
     await p.getByRole('button',{name:'處理中…',exact:true}).waitFor()
     await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().accept({token:'new',member:{id:2,name:'新會員',role:'CUSTOMER'}},true)})
     await p.getByLabel('收件人 *',{exact:true}).waitFor()
     await p.waitForFunction(()=>document.querySelector('#checkout-name')?.value==='預設人')
     assert.equal(await record(p),null);s.postGate.release();await settle(p,response)
     assert.equal(new URL(p.url()).pathname,'/checkout')
     assert.equal(await p.getByLabel('收件人 *',{exact:true}).inputValue(),'預設人')
     assert.equal(s.bodies.length,1)
    })
    await scenario('late lookup after leaving cannot navigate or discard preserved identity',async(p,s)=>{
     await unknown(p);s.lookupGate=gate()
     const response=p.waitForResponse(r=>new URL(r.url()).pathname.startsWith('/api/orders/requests/'))
     await lookup(p).click();await p.getByRole('button',{name:'查詢中…',exact:true}).waitFor()
await p.locator('header button.cart-link').click();await p.getByRole('button',{name:'查看完整購物車',exact:true}).click();await p.waitForURL('**/cart')
     s.lookupGate.release();await settle(p,response)
     assert.equal(new URL(p.url()).pathname,'/cart')
     assert.equal((await record(p)).requestId,s.bodies[0].requestId)
     assert.equal(s.bodies.length,1)
    })
    await scenario('identity conflict retains original request and disables retry',async(p,s)=>{
     s.abort=false;s.status=409;await unknown(p)
     await p.getByRole('alert').filter({hasText:'識別碼內容不同'}).waitFor()
     assert.equal((await record(p)).requestId,s.bodies[0].requestId)
     assert.equal(await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).isDisabled(),true)
     await lookup(p).click();await p.waitForURL('**/orders/88');assert.equal(s.bodies.length,1)
    })
    await scenario('malformed pending storage blocks new submission',async(p,s)=>{
     await p.evaluate(key=>sessionStorage.setItem(key,JSON.stringify({memberId:1,epoch:'identity',requestId:'bad',createdAt:Date.now()})),KEY)
     await p.reload();await p.getByRole('alert').filter({hasText:'無法讀取下單暫存'}).waitFor()
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     assert.equal(s.bodies.length,0)
    })
    await scenario('logout clears unresolved request and personal data from the current tab',async(p,s)=>{
     await unknown(p)
     assert.ok((await record(p)).payload)
     await p.getByRole('button',{name:'登出',exact:true}).click()
     await p.waitForURL(url=>url.pathname==='/login')
     assert.equal(await record(p),null);assert.equal(s.bodies.length,1)
    })
    await scenario('outdated backend blocks initial POST and preserves unresolved request on retry',async(p,s)=>{
     s.contractVersion=1
     await require('./confirm-checkout.cjs')(p)
     await p.getByRole('alert').filter({hasText:'本次未送出；請確認後端已更新'}).waitFor()
     assert.equal(s.bodies.length,0);assert.equal(await record(p),null)
     s.contractVersion=3;await unknown(p);const saved=await record(p)
     s.contractVersion=1;await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).click()
     await p.getByRole('alert').filter({hasText:'本次未送出；請確認後端已更新'}).waitFor()
     assert.equal(s.bodies.length,1);assert.equal((await record(p)).requestId,saved.requestId)
    })
    await scenario('price conflict retains fields; new server fee is used only after explicit sync',async(p,s)=>{
     s.abort=false;s.status=409;s.conflictCode='CHECKOUT_PRICE_CONFLICT'
     await require('./confirm-checkout.cjs')(p)
     await p.getByRole('alert').filter({hasText:'商品價格、優惠或運費已變更'}).waitFor()
     assert.equal(await record(p),null)
     assert.equal(await p.getByLabel('收件地址 *',{exact:true}).inputValue(),'原地址')
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     s.fee=37
     await p.getByRole('button',{name:'重新同步結帳資料',exact:true}).click()
     await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='確認下單'&&!b.disabled))
     await p.getByText('NT$ 237',{exact:true}).waitFor()
     s.abort=true;await unknown(p)
     assert.equal(s.bodies[1].expectedAmounts.shippingFee,37)
     assert.equal(s.bodies[1].expectedAmounts.totalAmount,237)
     assert.notEqual(s.bodies[1].requestId,s.bodies[0].requestId)
    })
    await scenario('quote failure blocks checkout and retry does not discard typed address',async(p,s)=>{
     s.quoteFail=true
     await p.getByRole('button',{name:'重新同步結帳資料',exact:true}).click()
     await p.getByRole('alert').filter({hasText:'報價暫時失敗'}).waitFor()
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     assert.equal(s.bodies.length,0)
     s.quoteFail=false
     await p.getByRole('button',{name:'重試載入',exact:true}).click()
     await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='確認下單'&&!b.disabled))
     assert.equal(await p.getByLabel('收件地址 *',{exact:true}).inputValue(),'原地址')
    })
    await scenario('v2 pending request is lookup-only without mutating its amount fingerprint',async(p,s)=>{
     await unknown(p)
     await p.evaluate(key=>{const r=JSON.parse(sessionStorage.getItem(key));delete r.payload.expectedAmounts;sessionStorage.setItem(key,JSON.stringify(r))},KEY)
     await p.reload();await lookup(p).waitFor()
     assert.equal(await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).isDisabled(),true)
     assert.equal(s.bodies.length,1)
     await lookup(p).click();await p.waitForURL(url=>url.pathname==='/orders/88')
     assert.equal(await record(p),null)
    })
    await scenario('old pending request is lookup-only and never silently acquires a snapshot',async(p,s)=>{
     await unknown(p)
     await p.evaluate(key=>{const r=JSON.parse(sessionStorage.getItem(key));delete r.payload.cartSnapshot;sessionStorage.setItem(key,JSON.stringify(r))},KEY)
     await p.reload();await lookup(p).waitFor()
     await p.getByText('此為舊版下單暫存',{exact:false}).waitFor()
     assert.equal(await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).isDisabled(),true)
     assert.equal(s.bodies.length,1)
     await lookup(p).click();await p.waitForURL(url=>url.pathname==='/orders/88')
     assert.equal(await record(p),null)
    })
    await scenario('snapshot conflict clears rejected attempt but requires explicit cart sync',async(p,s)=>{
     s.abort=false;s.status=409;s.conflictCode='CART_SNAPSHOT_CONFLICT'
     await require('./confirm-checkout.cjs')(p)
     await p.getByRole('alert').filter({hasText:'購物車內容已變更'}).waitFor()
     assert.equal(await record(p),null);assert.equal(s.bodies.length,1)
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     s.data=cart(3)
     await p.getByRole('button',{name:'重新同步結帳資料',exact:true}).click()
     await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='確認下單'&&!b.disabled))
     s.abort=true
     await unknown(p)
     assert.equal(s.bodies[1].cartSnapshot,'[[11,3]]')
     assert.notEqual(s.bodies[1].requestId,s.bodies[0].requestId)
    })
    await scenario('cart change detected by retry preflight never sends a second POST',async(p,s)=>{
     await unknown(p);s.data=cart(4)
     await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).click()
     await p.getByRole('alert').filter({hasText:'購物車內容已變更，本次未重送'}).waitFor()
     assert.equal(s.bodies.length,1)
     assert.equal((await record(p)).requestId,s.bodies[0].requestId)
     assert.equal(await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).isDisabled(),true)
    })
   }finally{await server.close()}
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
