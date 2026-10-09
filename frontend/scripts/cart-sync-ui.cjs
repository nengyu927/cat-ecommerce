const {chromium}=require('playwright'),assert=require('node:assert/strict')
function snapshot(price=100,stock=5,quantity=2){
 return {items:quantity?[{id:1,skuId:11,productName:'同步商品',specName:'小包',unitPrice:price,quantity,subtotal:price*quantity,stock}]:[],totalQuantity:quantity,totalAmount:price*quantity}
}
async function main(){
 const {createServer}=await import('vite');let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const [mode,port] of [['storefront',5207],['development',5208]]){
   const server=await createServer({mode,server:{host:'127.0.0.1',port,strictPort:true}})
   try{
    await server.listen()
    async function scenario(name,test,initialFailure=false){
     const ctx=await browser.newContext(),state={data:snapshot(),fail:initialFailure,writeFail:false,reads:0,writes:0,gate:null},unexpected=[]
     await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'cart-sync'})))
     await ctx.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
     await ctx.route(url=>url.pathname.startsWith('/api/'),async r=>{
      const path=new URL(r.request().url()).pathname,method=r.request().method(),ok=data=>r.fulfill({json:{success:true,data}})
      if(path==='/api/wishlist/ids')return ok([])
      if(path==='/api/cart'&&method==='GET'){
       state.reads++;const data=state.data,fail=state.fail
       if(state.gate)await state.gate.wait
       return fail?r.fulfill({status:503,json:{success:false,error:{message:'同步暫時失敗'}}}):ok(data)
      }
      if(path==='/api/cart/items/1'&&['PATCH','DELETE'].includes(method)){
       state.writes++
       if(state.writeFail)return r.fulfill({status:409,json:{success:false,error:{message:'庫存已變更'}}})
       state.data=method==='DELETE'?snapshot(100,5,0):snapshot(state.data.items[0].unitPrice,state.data.items[0].stock,r.request().postDataJSON().quantity)
       return ok(state.data)
      }
      unexpected.push(path);return r.fulfill({status:404,json:{success:false,error:{message:'Unexpected API'}}})
     })
     const p=await ctx.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>errors.push(e.message))
     try{
      await p.goto('http://127.0.0.1:'+port+'/cart')
      if(!initialFailure)await p.getByRole('button',{name:'重新同步購物車',exact:true}).waitFor({state:'visible'})
      await test(p,state);assert.deepEqual(unexpected,[]);console.log('PASS '+mode+': '+name)
     }finally{state.gate?.release();await ctx.close()}
    }
    const ready=async p=>{await p.getByLabel('商品數量：同步商品',{exact:true}).waitFor();await p.waitForFunction(()=>!document.querySelector('input[aria-label="商品數量：同步商品"]').disabled)}
    await scenario('refresh updates price, stock, quantity and blocks stale checkout',async(p,s)=>{
     await ready(p);s.data=snapshot(250,1,2)
     await p.getByRole('button',{name:'重新同步購物車',exact:true}).click()
     await p.getByText('購物車已同步最新庫存與價格',{exact:true}).waitFor()
     await p.getByText('目前庫存：1',{exact:true}).waitFor()
     assert.equal(await p.getByLabel('商品數量：同步商品',{exact:true}).getAttribute('max'),'1')
     await p.getByText('NT$ 500',{exact:true}).first().waitFor()
     assert.equal(await p.getByRole('button',{name:'前往結帳',exact:true}).isDisabled(),true)
     await p.getByLabel('商品數量：同步商品',{exact:true}).fill('1');await p.getByLabel('商品數量：同步商品',{exact:true}).press('Tab')
     await p.getByText('NT$ 250',{exact:true}).first().waitFor()
     await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='前往結帳'&&!b.disabled))
    })
    await scenario('failed refresh retains rows and totals, retry recovers',async(p,s)=>{
     await ready(p);s.fail=true
     await p.getByRole('button',{name:'重新同步購物車',exact:true}).click()
     await p.getByRole('alert').filter({hasText:'同步暫時失敗'}).waitFor()
     assert.equal(await p.getByLabel('商品數量：同步商品',{exact:true}).inputValue(),'2')
     await p.getByText('NT$ 200',{exact:true}).first().waitFor()
     assert.equal(await p.getByRole('button',{name:'前往結帳',exact:true}).isDisabled(),true)
     s.fail=false;s.data=snapshot(150,5,1)
     await p.getByRole('button',{name:'重試同步',exact:true}).click()
     await p.getByText('購物車已同步最新庫存與價格',{exact:true}).waitFor()
     assert.equal(await p.getByLabel('商品數量：同步商品',{exact:true}).inputValue(),'1')
     assert.equal(await p.getByRole('alert').count(),0)
    })
    await scenario('pending refresh locks writes, preserves rows and can return empty',async(p,s)=>{
     await ready(p);let release;s.gate={wait:new Promise(resolve=>{release=resolve}),release:()=>release()}
     s.data=snapshot(100,5,0)
     const request=p.waitForRequest(r=>new URL(r.url()).pathname==='/api/cart')
     await p.getByRole('button',{name:'重新同步購物車',exact:true}).click();await request
     assert.equal(await p.getByLabel('商品數量：同步商品',{exact:true}).isDisabled(),true)
     assert.equal(await p.getByRole('button',{name:'移除',exact:true}).isDisabled(),true)
     assert.equal(await p.getByRole('button',{name:'前往結帳',exact:true}).isDisabled(),true)
     const count=s.reads
     await p.getByRole('button',{name:'同步中…',exact:true}).dispatchEvent('click')
     assert.equal(s.reads,count);assert.equal(s.writes,0)
     s.gate.release();await p.getByText('購物車是空的',{exact:false}).waitFor()
     assert.equal(await p.getByLabel('商品數量：同步商品',{exact:true}).count(),0)
    })
    await scenario('rejected quantity update recovers by read without replaying write',async(p,s)=>{
     await ready(p);s.writeFail=true
     await p.getByLabel('商品數量：同步商品',{exact:true}).fill('3');await p.getByLabel('商品數量：同步商品',{exact:true}).press('Tab')
     await p.getByRole('alert').filter({hasText:'庫存已變更'}).waitFor()
     assert.equal(await p.getByRole('button',{name:'前往結帳',exact:true}).isDisabled(),true)
     s.writeFail=false;s.data=snapshot(200,2,2)
     await p.getByRole('button',{name:'重新同步購物車',exact:true}).click()
     await p.getByText('購物車已同步最新庫存與價格',{exact:true}).waitFor()
     await p.getByText('目前庫存：2',{exact:true}).waitFor()
     assert.equal(await p.getByLabel('商品數量：同步商品',{exact:true}).inputValue(),'2')
     assert.equal(s.writes,1)
     assert.equal(await p.getByRole('button',{name:'前往結帳',exact:true}).isEnabled(),true)
    })
    await scenario('initial read failure retries without claiming empty cart',async(p,s)=>{
     await p.getByRole('button',{name:'重試載入',exact:true}).waitFor()
     assert.equal(await p.getByText('購物車是空的',{exact:false}).count(),0)
     s.fail=false;await p.getByRole('button',{name:'重試載入',exact:true}).click();await ready(p)
     assert.equal(await p.getByRole('alert').count(),0)
    },true)
   }finally{await server.close()}
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
