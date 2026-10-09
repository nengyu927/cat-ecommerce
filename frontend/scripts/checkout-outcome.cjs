const {chromium}=require('playwright'),assert=require('node:assert/strict')
const quoteFixture=require('./checkout-quote-fixture.cjs')
const cart={items:[{id:1,skuId:11,productName:'下單商品',stock:5,quantity:2,unitPrice:100,subtotal:200}],totalQuantity:2,totalAmount:200}
const order={id:71,orderNo:'ORDER71',status:'PENDING',paymentMethod:'COD',receiverName:'收件人',receiverPhone:'0911111111',shippingAddress:'台北市',items:[],totalAmount:280,shippingFee:80,createdAt:'2026-10-09T01:00:00Z'}
const gate=()=>{let release;return {wait:new Promise(resolve=>{release=resolve}),release:()=>release()}}
async function main(){
 const {createServer}=await import('vite');let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const [mode,port] of [['storefront',5211],['development',5212]]){
   const server=await createServer({mode,server:{host:'127.0.0.1',port,strictPort:true}})
   try{
    await server.listen()
    async function scenario(name,test,options={}){
     const ctx=await browser.newContext({viewport:{width:options.width||1280,height:900}}),s={posts:0,bodies:[],status:200,abort:false,gate:null,data:cart,...options},unexpected=[]
     await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'outcome'})))
     await ctx.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
     await ctx.route(url=>url.pathname.startsWith('/api/'),async r=>{
      const path=new URL(r.request().url()).pathname,ok=data=>r.fulfill({json:{success:true,data}})
      if(path==='/api/wishlist/ids')return ok([])
      if(path==='/api/orders/request-identity')return ok({version:3})
      if(path==='/api/orders/checkout-quote')return ok(quoteFixture(s.data))
      if(path==='/api/cart')return ok(s.data)
      if(path==='/api/members/me/addresses')return ok([{id:1,receiverName:'收件人',phone:'0911111111',city:'台北市',line1:'',isDefault:true}])
      if(path==='/api/orders'&&r.request().method()==='POST'){
       s.posts++;s.bodies.push(r.request().postDataJSON());const status=s.status,abort=s.abort
       if(s.gate)await s.gate.wait
       if(abort)return r.abort('failed')
       if(status===200)s.data={items:[],totalQuantity:0,totalAmount:0}
       return status===200?ok(order):r.fulfill({status,json:{success:false,error:{message:status===503?'伺服器未回報結果':'資料驗證失敗'}}})
      }
      if(path==='/api/orders/71')return ok(order)
      if(path==='/api/orders/71/shipment')return ok(null)
      unexpected.push(path);return r.fulfill({status:404,json:{success:false,error:{message:'Unexpected API'}}})
     })
     const p=await ctx.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>errors.push(e.message))
     try{
      await p.goto('http://127.0.0.1:'+port+'/checkout')
      await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='確認下單'&&!b.disabled))
      await p.getByLabel('備註',{exact:true}).fill('保留備註')
      await test(p,s);assert.deepEqual(unexpected,[]);console.log('PASS '+mode+': '+name)
     }finally{s.gate?.release();await ctx.close()}
    }
    const submit=async p=>{
     const sent=p.waitForRequest(r=>new URL(r.url()).pathname==='/api/orders'&&r.method()==='POST')
     await require('./confirm-checkout.cjs')(p);await sent
    }
    const settle=async(p,response)=>{
     await (await response).finished()
     await p.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
    }
    await scenario('successful checkout navigates once, clears cart and loads detail',async(p,s)=>{
     await submit(p);await p.waitForURL('**/orders/71')
     await p.getByRole('heading',{name:'訂單 ORDER71',exact:true}).waitFor()
     assert.equal(s.posts,1)
     assert.equal(await p.evaluate(async()=>{const {useCartStore}=await import('/src/stores/cart.js');return useCartStore().count}),0)
    })
    for(const status of [200,400]){
     await scenario('late '+status+' after leaving cannot redirect or mutate new checkout',async(p,s)=>{
      s.status=status;s.gate=gate();const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/orders'&&r.request().method()==='POST')
      await submit(p);await p.getByText('訂單正在送出，請勿重複操作；離開頁面不會取消下單。',{exact:true}).waitFor()
      await p.locator('header button.cart-link').click();await p.getByRole('button',{name:'查看完整購物車',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'了解並離開',exact:true}).click();await p.waitForURL('**/cart')
      assert.equal(await p.evaluate(()=>{const e=new Event('beforeunload',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented}),false)
      await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');await router.push('/checkout')})
      await p.waitForURL('**/checkout')
      await p.getByRole('button',{name:'查詢此次下單結果',exact:true}).waitFor()
      s.gate.release();await settle(p,response)
      assert.equal(new URL(p.url()).pathname,'/checkout')
      assert.equal(await p.getByLabel('備註',{exact:true}).inputValue(),'保留備註')
      assert.equal(await p.getByLabel('備註',{exact:true}).isDisabled(),true)
      assert.equal(await p.getByRole('alert').count(),1)
      assert.equal(await p.evaluate(async()=>{const {useCartStore}=await import('/src/stores/cart.js');return useCartStore().count}),2)
      assert.equal(s.posts,1)
     })
    }
    await scenario('dismiss leave confirmation keeps pending form locked and prevents duplicate POST',async(p,s)=>{
     s.status=400;s.gate=gate();const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/orders')
     await submit(p);await p.getByText('訂單正在送出，請勿重複操作；離開頁面不會取消下單。',{exact:true}).waitFor()
     assert.equal(await p.getByLabel('收件人 *',{exact:true}).isDisabled(),true)
     assert.equal(await p.evaluate(()=>{const e=new Event('beforeunload',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented}),true)
     await p.locator('header button.cart-link').click();await p.getByRole('button',{name:'查看完整購物車',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'留在此頁等待',exact:true}).click()
     assert.equal(new URL(p.url()).pathname,'/checkout')
     await p.getByRole('button',{name:'處理中…',exact:true}).dispatchEvent('click')
     assert.equal(s.posts,1);s.gate.release();await settle(p,response)
     await p.getByRole('alert').filter({hasText:'資料驗證失敗'}).waitFor()
     assert.equal(await p.getByLabel('備註',{exact:true}).inputValue(),'保留備註')
     assert.equal(await p.getByLabel('收件人 *',{exact:true}).isEnabled(),true)
    })
    for(const abort of [false,true]){
     await scenario((abort?'network failure':'503')+' preserves identity and original body; no automatic replay',async(p,s)=>{
      s.status=503;s.abort=abort;await submit(p)
      await p.getByText('尚無法確認訂單是否成立，請先查看我的訂單；不要直接重複送出。',{exact:true}).waitFor()
      assert.equal(await p.getByRole('link',{name:'查看我的訂單',exact:true}).getAttribute('href'),'/orders')
      assert.equal(await p.getByLabel('備註',{exact:true}).inputValue(),'保留備註')
      await p.getByRole('button',{name:'重新同步結帳資料',exact:true}).click()
      await p.getByText('結帳資料已同步，填寫內容已保留；優惠券請重新套用。',{exact:true}).waitFor()
      assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
      assert.equal(s.posts,1)
      s.status=400;s.abort=false
      await p.getByRole('button',{name:'以相同識別碼重試',exact:true}).click()
      await p.getByRole('alert').filter({hasText:'資料驗證失敗'}).waitFor()
      assert.equal(s.posts,2);assert.deepEqual(s.bodies[0],s.bodies[1])
     })
    }
    await scenario('blocked detail navigation preserves created order and never permits resubmission',async(p,s)=>{
     await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');router.beforeEach(to=>to.name==='order-detail'?false:undefined)})
     await submit(p)
     await p.getByText('訂單已成立，但未能開啟詳情，請使用下方連結查看。',{exact:true}).waitFor()
     assert.equal(await p.getByRole('link',{name:'查看已成立訂單',exact:true}).getAttribute('href'),'/orders/71')
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     await p.getByRole('button',{name:'確認下單',exact:true}).dispatchEvent('click')
     assert.equal(s.posts,1)
    })
    async function openLeave(p){
     await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');void router.push('/cart')})
     const dialog=p.getByRole('dialog',{name:'訂單處理中，仍要離開？',exact:true});await dialog.waitFor();return dialog
    }
    for(const width of [1440,390]){
     await scenario(`leave prompt ${width}: safe focus, Escape, identity and unknown-result guidance`,async(p,s)=>{
      s.status=503;s.gate=gate();const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/orders'&&r.request().method()==='POST')
      await submit(p);const dialog=await openLeave(p);assert.equal(await p.evaluate(()=>document.activeElement.textContent),'留在此頁等待')
      await dialog.getByText(new RegExp(s.bodies[0].requestId)).waitFor();assert.equal(s.posts,1)
      if(process.env.PREVIEW_SCREENSHOT_DIR){await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))});await p.screenshot({path:require('node:path').join(process.env.PREVIEW_SCREENSHOT_DIR,`checkout-leave-${mode}-${width}.png`)})}
      await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(new URL(p.url()).pathname,'/checkout');assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
      s.gate.release();await settle(p,response);await p.getByRole('heading',{name:'先確認訂單，再決定下一步',exact:true}).waitFor()
      const panel=p.getByRole('alert',{name:'下單結果待確認',exact:true});assert.equal(await panel.locator('ol li').count(),3);assert.equal(s.posts,1)
      assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true);assert.equal(await p.getByLabel('備註',{exact:true}).inputValue(),'保留備註')
      assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
      if(process.env.PREVIEW_SCREENSHOT_DIR){
       await p.evaluate(()=>window.scrollTo(0,0))
       await p.locator('.alert').evaluateAll(async notices=>{await Promise.all(notices.flatMap(n=>n.getAnimations().map(a=>a.finished)))})
       await p.screenshot({path:require('node:path').join(process.env.PREVIEW_SCREENSHOT_DIR,`checkout-unknown-${mode}-${width}.png`),fullPage:true})
      }
     },{width})
     for(const status of [200,400])await scenario(`result ${status} while leave prompt open ${width} closes stale prompt`,async(p,s)=>{
      s.status=status;s.gate=gate();const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/orders'&&r.request().method()==='POST')
      await submit(p);const dialog=await openLeave(p);s.gate.release();await settle(p,response)
      if(status===200){await p.waitForURL('**/orders/71');await p.getByRole('heading',{name:'訂單 ORDER71',exact:true}).waitFor()}
      else{await p.getByRole('alert').filter({hasText:'資料驗證失敗'}).waitFor();assert.equal(new URL(p.url()).pathname,'/checkout');assert.equal(await p.getByLabel('備註',{exact:true}).inputValue(),'保留備註')}
      await dialog.waitFor({state:'hidden'});assert.equal(await p.evaluate(()=>document.body.style.overflow),'');assert.equal(s.posts,1)
     },{width})
    }
    await scenario('logout while leave prompt pending clears it; late result cannot affect new session',async(p,s)=>{
     s.status=400;s.gate=gate();const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/orders'&&r.request().method()==='POST')
     await submit(p);const dialog=await openLeave(p)
     await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()});await p.waitForURL('**/login?**');await dialog.waitFor({state:'hidden'})
     s.gate.release();await settle(p,response);assert.equal(new URL(p.url()).pathname,'/login');assert.equal(await p.evaluate(()=>document.body.style.overflow),'');assert.equal(s.posts,1)
    },{width:390})
   }finally{await server.close()}
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
