const {chromium}=require('playwright'),assert=require('node:assert/strict')
const quoteFixture=require('./checkout-quote-fixture.cjs')
const cart=(price=100,stock=5,quantity=2)=>({items:quantity?[{id:1,skuId:11,productName:'結帳商品',unitPrice:price,quantity,subtotal:price*quantity,stock}]:[],totalAmount:price*quantity,totalQuantity:quantity})
const address={id:1,receiverName:'預設收件人',phone:'0911111111',city:'台北市',line1:'預設地址',isDefault:true}
async function draft(p){
 await p.getByLabel('收件人 *',{exact:true}).fill('手動收件人')
 await p.getByLabel('收件電話 *',{exact:true}).fill('0922222222')
 await p.getByLabel('收件地址 *',{exact:true}).fill('高雄市手動地址')
 await p.getByLabel('備註',{exact:true}).fill('下午配送')
 await p.getByLabel('付款方式',{exact:true}).selectOption('MOCK')
}
async function retained(p){
 for(const [label,value] of [['收件人 *','手動收件人'],['收件電話 *','0922222222'],['收件地址 *','高雄市手動地址'],['備註','下午配送'],['付款方式','MOCK']])
  assert.equal(await p.getByLabel(label,{exact:true}).inputValue(),value)
}
async function main(){
 const {createServer}=await import('vite');let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const [mode,port] of [['storefront',5209],['development',5210]]){
   const server=await createServer({mode,server:{host:'127.0.0.1',port,strictPort:true}})
   try{
    await server.listen()
    async function scenario(name,test,options={}){
     const ctx=await browser.newContext(),state={data:cart(),readFail:false,addressFail:false,orderStatus:409,orders:[],quotes:0,quoteGate:null,readGate:null,...options},unexpected=[]
     await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'checkout-recovery'})))
     await ctx.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
     await ctx.route(url=>url.pathname.startsWith('/api/'),async r=>{
      const path=new URL(r.request().url()).pathname,ok=data=>r.fulfill({json:{success:true,data}}),fail=(status,message)=>r.fulfill({status,json:{success:false,error:{message}}})
      if(path==='/api/wishlist/ids')return ok([])
      if(path==='/api/orders/request-identity')return ok({version:3})
      if(path==='/api/orders/checkout-quote')return ok(quoteFixture(state.data))
      if(path==='/api/cart'){
       const data=state.data,failed=state.readFail
       if(state.readGate)await state.readGate.wait
       return failed?fail(503,'同步失敗'):ok(data)
      }
      if(path==='/api/members/me/addresses')return state.addressFail?fail(503,'地址失敗'):ok([address])
      if(path==='/api/orders/quote'){
       state.quotes++;if(state.quoteGate)await state.quoteGate.wait
       return ok(quoteFixture(state.data,20,'DEMO'))
      }
      if(path==='/api/orders'){
       state.orders.push(r.request().postDataJSON())
       return fail(state.orderStatus,state.orderStatus===409?'庫存已變更':'收件資料不正確')
      }
      unexpected.push(path);return fail(404,'Unexpected API')
     })
     const p=await ctx.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>errors.push(e.message))
     try{
      await p.goto('http://127.0.0.1:'+port+'/checkout')
      await p.getByRole('button',{name:'重新同步結帳資料',exact:true}).waitFor()
      await test(p,state);assert.deepEqual(unexpected,[]);console.log('PASS '+mode+': '+name)
     }finally{state.quoteGate?.release();state.readGate?.release();await ctx.close()}
    }
    const sync=async p=>{
     await p.getByRole('button',{name:'重新同步結帳資料',exact:true}).click()
     await p.getByText('結帳資料已同步，填寫內容已保留；優惠券請重新套用。',{exact:true}).waitFor()
    }
    const gate=()=>{let release;return {wait:new Promise(resolve=>{release=resolve}),release:()=>release()}}
    await scenario('refresh preserves draft and updates prices, invalidates applied coupon',async(p,s)=>{
     assert.equal(await p.getByLabel('收件人 *',{exact:true}).inputValue(),'預設收件人')
     await draft(p);await p.getByLabel('優惠碼',{exact:true}).fill('DEMO')
     await p.getByRole('button',{name:'套用優惠券',exact:true}).click()
     await p.getByText('已套用 DEMO，折抵 NT$ 20',{exact:true}).waitFor()
     s.data=cart(250);await sync(p);await retained(p)
     await p.getByText('NT$ 580',{exact:true}).waitFor()
     assert.equal(await p.getByText('已套用 DEMO，折抵 NT$ 20',{exact:true}).count(),0)
     assert.equal(await p.getByLabel('優惠碼',{exact:true}).inputValue(),'DEMO')
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     await p.getByRole('button',{name:'套用優惠券',exact:true}).click()
     await p.getByText('已套用 DEMO，折抵 NT$ 20',{exact:true}).waitFor()
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isEnabled(),true)
    })
    await scenario('failed sync keeps draft and locks checkout until retry',async(p,s)=>{
     await draft(p);s.readFail=true
     await p.getByRole('button',{name:'重新同步結帳資料',exact:true}).click()
     await p.getByRole('alert').filter({hasText:'同步失敗'}).waitFor();await retained(p)
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     await p.getByRole('button',{name:'確認下單',exact:true}).dispatchEvent('click')
     assert.equal(s.orders.length,0);s.readFail=false
     await p.getByRole('button',{name:'重試載入',exact:true}).click()
     await p.getByText('結帳資料已同步，填寫內容已保留；優惠券請重新套用。',{exact:true}).waitFor();await retained(p)
    })
    await scenario('address retry does not overwrite manual draft; explicit selection still applies',async(p,s)=>{
     await draft(p);s.addressFail=false
     await p.getByRole('button',{name:'重試地址',exact:true}).click()
     await p.getByRole('button',{name:'預設收件人 · 台北市預設地址',exact:true}).waitFor()
     await retained(p)
     await p.getByRole('button',{name:'預設收件人 · 台北市預設地址',exact:true}).click()
     assert.equal(await p.getByLabel('收件人 *',{exact:true}).inputValue(),'預設收件人')
     assert.equal(await p.getByLabel('備註',{exact:true}).inputValue(),'下午配送')
    },{addressFail:true})
    await scenario('short stock blocks quotes and orders; mobile empty recovery',async(p,s)=>{
     await draft(p)
     await p.getByRole('alert').filter({hasText:'需要 2 件，目前庫存 1 件'}).waitFor()
     assert.equal(await p.getByRole('link',{name:'返回購物車調整',exact:true}).getAttribute('href'),'/cart')
     await p.getByLabel('優惠碼',{exact:true}).fill('DEMO')
     await p.getByRole('button',{name:'套用優惠券',exact:true}).dispatchEvent('click')
     await p.getByRole('button',{name:'確認下單',exact:true}).dispatchEvent('click')
     assert.equal(s.orders.length,0);assert.equal(s.quotes,0)
     s.data=cart(100,5,0);await sync(p);await retained(p)
     await p.getByText('購物車是空的，請先加入商品。',{exact:false}).waitFor()
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     await p.setViewportSize({width:390,height:844})
     assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
    },{data:cart(100,1)})
    await scenario('409 retains draft, requires sync and never automatically replays POST',async(p,s)=>{
     await draft(p);await require('./confirm-checkout.cjs')(p)
     await p.getByRole('alert').filter({hasText:'庫存已變更'}).waitFor();await retained(p)
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     assert.equal(s.orders.length,1);await sync(p);await retained(p);assert.equal(s.orders.length,1)
     assert.equal(await p.getByRole('alert').count(),0)
     s.orderStatus=400;await require('./confirm-checkout.cjs')(p)
     await p.getByRole('alert').filter({hasText:'收件資料不正確'}).waitFor();await retained(p)
     assert.equal(s.orders.length,2)
     const {requestId,cartSnapshot,expectedAmounts,...body}=s.orders[1]
     assert.equal(expectedAmounts.totalAmount,280)
     assert.ok(cartSnapshot.startsWith('[['))
     assert.match(requestId,/^[0-9a-f-]{36}$/)
     assert.notEqual(requestId,s.orders[0].requestId)
     assert.deepEqual(body,{receiverName:'手動收件人',receiverPhone:'0922222222',shippingAddress:'高雄市手動地址',remark:'下午配送',paymentMethod:'MOCK',couponCode:null})
     assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isEnabled(),true)
    })
    await scenario('late coupon cannot restore obsolete quote; syncing locks order but retains editable draft',async(p,s)=>{
     await draft(p);s.quoteGate=gate()
     await p.getByLabel('優惠碼',{exact:true}).fill('DEMO')
     const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/orders/quote')
     await p.getByRole('button',{name:'套用優惠券',exact:true}).click()
     await p.getByText('驗證優惠中…',{exact:true}).waitFor()
     s.readGate=gate();const request=p.waitForRequest(r=>new URL(r.url()).pathname==='/api/cart')
     await p.getByRole('button',{name:'重新同步結帳資料',exact:true}).click();await request
     await retained(p);assert.equal(await p.getByRole('button',{name:'確認下單',exact:true}).isDisabled(),true)
     await p.getByLabel('備註',{exact:true}).fill('同步時修改')
     s.quoteGate.release();await (await response).finished()
     s.readGate.release()
     await p.getByText('結帳資料已同步，填寫內容已保留；優惠券請重新套用。',{exact:true}).waitFor()
     assert.equal(await p.getByText('已套用 DEMO，折抵 NT$ 20',{exact:true}).count(),0)
     assert.equal(await p.getByLabel('備註',{exact:true}).inputValue(),'同步時修改')
     assert.equal(s.orders.length,0)
    })
   }finally{await server.close()}
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
