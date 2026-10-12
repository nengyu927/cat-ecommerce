const {chromium}=require('playwright'),assert=require('node:assert/strict')
const product=id=>({id,name:'購物商品'+id,basePrice:100,images:[],skus:[{id:id*10+1,specName:'小包',price:100,stock:5},{id:id*10+2,specName:'大包',price:200,stock:2}]})
async function main(){
 const {createServer}=await import('vite')
 let browser
 const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const [mode,port] of [['storefront',5205],['development',5206]]){
   const server=await createServer({mode,server:{host:'127.0.0.1',port,strictPort:true}})
   try{
    assert.equal(server.config.env.VITE_APP_SURFACE||'combined',mode==='storefront'?'storefront':'combined')
    await server.listen()
    const base='http://127.0.0.1:'+port
    async function scenario(name,test){
     const ctx=await browser.newContext(),state={writes:[],failure:false,gate:null,total:0},unexpected=[]
     await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'product-cart'})))
     await ctx.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
     await ctx.route(url=>url.pathname.startsWith('/api/'),async r=>{
      const path=new URL(r.request().url()).pathname,method=r.request().method()
      const ok=data=>r.fulfill({json:{success:true,data}})
      if(path==='/api/cart/items'&&method==='POST'){
       const payload=r.request().postDataJSON(),gate=state.gate,failure=state.failure
       state.writes.push(payload)
       if(gate)await gate.wait
       if(failure)return r.fulfill({status:409,json:{success:false,error:{message:'庫存已變更，請調整數量'}}})
       state.total+=payload.quantity
       return ok({items:[],totalQuantity:state.total,totalAmount:state.total*100})
      }
      if(path==='/api/cart')return ok({items:[],totalQuantity:state.total,totalAmount:state.total*100})
      if(path==='/api/wishlist/ids')return ok([])
      if(/^\/api\/products\/\d+\/related$/.test(path))return ok([product(2)])
      if(/^\/api\/products\/\d+$/.test(path))return ok(product(Number(path.split('/').pop())))
      unexpected.push(path);return r.fulfill({status:404,json:{success:false,error:{message:'Unexpected API'}}})
     })
     const p=await ctx.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>errors.push(e.message))
     try{
      await p.goto(base+'/products/1');await p.getByRole('heading',{name:'購物商品1',exact:true}).waitFor()
      await test(p,state);assert.deepEqual(unexpected,[]);console.log('PASS '+mode+': '+name)
     }finally{state.gate?.release();await ctx.close()}
    }
    await scenario('invalid quantity keeps detail and never writes, then recovers',async(p,s)=>{
     for(const value of ['0','6','1.5','']){
      await p.getByLabel('數量',{exact:true}).fill(value)
      await p.getByRole('button',{name:'加入購物車',exact:true}).click()
      await p.getByRole('alert').filter({hasText:'請輸入庫存範圍內的整數數量'}).waitFor()
      assert.equal(s.writes.length,0)
      await p.getByRole('heading',{name:'購物商品1',exact:true}).waitFor()
     }
     await p.getByLabel('數量',{exact:true}).fill('2')
     await p.getByRole('button',{name:'加入購物車',exact:true}).click()
     await p.getByText('已加入購物車',{exact:true}).waitFor()
     assert.equal(await p.getByRole('dialog',{name:'購物車預覽'}).isVisible(),false)
     assert.deepEqual(s.writes,[{skuId:11,quantity:2}]);assert.equal(await p.getByRole('alert').count(),0)
    })
    await scenario('cart failure preserves product, SKU and quantity for retry',async(p,s)=>{
     s.failure=true;await p.getByLabel('選擇規格',{exact:true}).selectOption('12')
     await p.getByLabel('數量',{exact:true}).fill('2');await p.getByRole('button',{name:'加入購物車',exact:true}).click()
     await p.getByRole('alert').filter({hasText:'庫存已變更'}).waitFor()
     await p.getByRole('heading',{name:'購物商品1',exact:true}).waitFor()
     assert.equal(await p.getByLabel('選擇規格',{exact:true}).inputValue(),'12')
     assert.equal(await p.getByLabel('數量',{exact:true}).inputValue(),'2')
     assert.equal(await p.getByRole('button',{name:'重試載入商品',exact:true}).count(),0)
     s.failure=false;await p.getByRole('button',{name:'加入購物車',exact:true}).click()
     await p.getByText('已加入購物車',{exact:true}).waitFor();assert.equal(s.writes.length,2)
    })
    for(const failure of [false,true]){
     await scenario('pending '+(failure?'failed':'successful')+' add cannot change next product UI',async(p,s)=>{
      s.failure=failure
      let release;s.gate={wait:new Promise(resolve=>{release=resolve}),release:()=>release()}
      const request=p.waitForRequest(r=>new URL(r.url()).pathname==='/api/cart/items'&&r.method()==='POST')
      await p.getByRole('button',{name:'加入購物車',exact:true}).click();await request
      assert.equal(await p.getByLabel('數量',{exact:true}).isDisabled(),true)
      assert.equal(await p.getByLabel('選擇規格',{exact:true}).isDisabled(),true)
      assert.equal(await p.getByRole('button',{name:'加入中…',exact:true}).isDisabled(),true)
      await p.locator('a.product-card').click();await p.getByRole('heading',{name:'購物商品2',exact:true}).waitFor()
      assert.equal(await p.getByRole('button',{name:'加入購物車',exact:true}).isEnabled(),true)
      const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/cart/items'&&r.request().method()==='POST')
      s.gate.release();await (await response).finished()
      // Synchronize through the real queue, not an assumed header refresh.
      await p.evaluate(async()=>{
       const {useCartStore}=await import('/src/stores/cart.js')
       await useCartStore().fetch()
      })
      assert.equal(await p.getByRole('button',{name:'加入購物車',exact:true}).isEnabled(),true)
      await p.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
      assert.equal(await p.getByText('已加入購物車',{exact:true}).count(),0)
      assert.equal(await p.getByRole('alert').count(),0)
      assert.equal(await p.getByLabel('數量',{exact:true}).inputValue(),'1')
      assert.equal(s.writes.length,1)
      await p.getByRole('button',{name:failure?'購物車':'購物車，1 件商品',exact:true}).waitFor()
     })
    }
   }finally{await server.close()}
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
