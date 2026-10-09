const {chromium}=require('playwright'),assert=require('node:assert/strict')
const product=id=>({id,name:'路由商品'+id,brand:'Test',basePrice:100,images:[],skus:[]})
function holdResponse(data,failure=false){
 let release
 const wait=new Promise(resolve=>{release=resolve})
 return {wait,release,data,failure}
}
async function releaseResponse(page,path,gate){
 const pending=page.waitForResponse(r=>new URL(r.url()).pathname===path)
 gate.release()
 await (await pending).finished()
 await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
}
async function main(){
 const {createServer}=await import('vite')
 let browser
 const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const [mode,port] of [['storefront',5203],['development',5204]]){
   const server=await createServer({mode,server:{host:'127.0.0.1',port,strictPort:true}})
   try{
    assert.equal(server.config.env.VITE_APP_SURFACE||'combined',mode==='storefront'?'storefront':'combined')
    await server.listen()
    const base='http://127.0.0.1:'+port
    async function scenario(name,test){
     const ctx=await browser.newContext({viewport:{width:390,height:844}}),calls=[],state={items:[product(1),product(2),product(3)],relatedFailure:false,detailFailure:false,gates:{},relatedItems:[product(3)]}
     await ctx.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
     await ctx.route(url=>url.pathname.startsWith('/api/'),async r=>{
      const url=new URL(r.request().url()),path=url.pathname,query=Object.fromEntries(url.searchParams)
      calls.push({path,query})
      const ok=data=>r.fulfill({json:{success:true,data}})
      const gate=state.gates[path]
      if(gate){
       await gate.wait
       return gate.failure?r.fulfill({status:503,json:{success:false,error:{message:'過期回應失敗'}}}):ok(gate.data)
      }
      if(path==='/api/products/brands')return ok(['Test'])
      if(path==='/api/categories')return ok([{id:7,name:'貓糧',slug:'food'},{id:8,name:'玩具',slug:'toys'}])
      if(path==='/api/categories/slug/food')return ok({id:7,name:'貓糧',slug:'food'})
      if(path==='/api/categories/slug/missing')return r.fulfill({status:404,json:{success:false,error:{message:'分類不存在'}}})
      if(path==='/api/products'){
       const page=Number(query.page||0),size=Number(query.size||12),items=state.items
       return ok({content:items.slice(page*size,(page+1)*size),page,size,totalElements:items.length,totalPages:Math.ceil(items.length/size)})
      }
      if(/^\/api\/products\/\d+\/related$/.test(path))return state.relatedFailure?r.fulfill({status:503,json:{success:false,error:{message:'推薦暫時無法載入'}}}):ok(state.relatedItems)
      if(/^\/api\/products\/\d+$/.test(path)){
       const id=Number(path.split('/').pop())
       if(state.detailFailure)return r.fulfill({status:503,json:{success:false,error:{message:'商品暫時無法載入'}}})
       return state.items.some(item=>item.id===id)?ok(product(id)):r.fulfill({status:404,json:{success:false,error:{message:'商品已下架'}}})
      }
      return r.fulfill({status:404,json:{success:false,error:{message:'未預期 API '+path}}})
     })
     const p=await ctx.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>errors.push(e.message))
     const ready=()=>p.locator('.paging-jump button:not([disabled])').waitFor()
     const latest=()=>calls.filter(c=>c.path==='/api/products').at(-1).query
     try{await test(p,calls,ready,latest,state);console.log('PASS '+mode+': '+name)}finally{for(const gate of Object.values(state.gates))gate.release();await ctx.close()}
    }
    await scenario('category filters retain slug, paging and changed category',async(p,calls,ready,latest)=>{
     await p.goto(base+'/category/food?size=1&page=1&sort=name,asc');await ready()
     await p.getByLabel('商品品牌',{exact:true}).selectOption('Test')
     await p.waitForURL(u=>u.pathname==='/category/food'&&u.searchParams.get('brand')==='Test'&&!u.searchParams.has('page'));await ready()
     assert.equal(latest().categoryId,'7');assert.equal(latest().size,'1')
     await p.getByLabel('商品排序',{exact:true}).selectOption('price,desc')
     await p.waitForURL(u=>u.pathname==='/category/food'&&u.searchParams.get('sort')==='price,desc');await ready()
     assert.equal(latest().sort,'price,desc')
     await p.getByLabel('商品關鍵字',{exact:true}).fill('路由商品')
     await p.getByRole('button',{name:'搜尋',exact:true}).click();await p.waitForURL(u=>u.pathname==='/category/food'&&u.searchParams.get('q')==='路由商品');await ready()
     await p.getByRole('button',{name:'下一頁',exact:true}).click();await p.waitForURL(u=>u.searchParams.get('page')==='1');await ready()
     assert.equal(latest().categoryId,'7');assert.equal(latest().keyword,'路由商品')
     await p.reload();await ready();assert.equal(await p.getByLabel('商品品牌',{exact:true}).inputValue(),'Test')
     await p.getByLabel('商品分類',{exact:true}).selectOption('8')
     await p.waitForURL(u=>u.pathname==='/search'&&u.searchParams.get('categoryId')==='8');await ready()
     assert.equal(latest().categoryId,'8')
    })
    for(const path of ['/category/food','/search']){
     await scenario(path+' detail, related, reload and return preserve exact source',async(p,calls,ready,latest)=>{
      const source=base+path+'?q=cat&brand=Test&minPrice=0&maxPrice=200&inStock=false&size=1&page=1&sort=name,asc#catalog'
      await p.goto(source);await ready()
await p.locator('.product-image-wrap a').click();await p.waitForURL(u=>u.pathname==='/products/2')
      assert.equal(new URL(p.url()).searchParams.get('returnTo'),new URL(source).pathname+new URL(source).search+new URL(source).hash)
      await p.reload();await p.getByRole('heading',{name:'路由商品2',exact:true}).waitFor()
      await p.locator('a.product-card').first().click();await p.getByRole('heading',{name:'路由商品3',exact:true}).waitFor()
      await p.getByRole('link',{name:'返回商品列表',exact:true}).click();await p.waitForURL(source);await ready()
      assert.equal(latest().page,'1');assert.equal(latest().size,'1');assert.equal(latest().minPrice,'0')
      assert.equal(new URL(p.url()).searchParams.get('inStock'),'false')
      assert.equal(latest().inStock,undefined) // The API default is false; the store omits this value.
      if(path.startsWith('/category'))assert.equal(latest().categoryId,'7')
      assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
     })
    }
    await scenario('category and search last-page correction preserve route and query',async(p,calls,ready,latest)=>{
     for(const path of ['/category/food','/search']){
      await p.goto(base+path+'?q=cat&brand=Test&size=1&page=99&sort=name,asc#catalog')
      await p.waitForURL(u=>u.pathname===path&&u.searchParams.get('page')==='2');await ready()
      assert.equal(latest().page,'2');assert.equal(latest().keyword,'cat')
      assert.equal(new URL(p.url()).hash,'#catalog')
     }
    })
    await scenario('off-shelf detail returns to shortened category and empty search pages',async(p,calls,ready,latest,state)=>{
     for(const [path,remaining,page] of [['/category/food',[product(1),product(2)],'1'],['/search',[],'0']]){
      state.items=[product(1),product(2),product(3)]
      await p.goto(base+path+'?q=cat&brand=Test&minPrice=0&size=1&page=2&sort=name,asc#catalog');await ready()
      await p.locator('.product-image-wrap a').click();await p.getByRole('heading',{name:'路由商品3',exact:true}).waitFor()
      state.items=remaining;await p.reload();await p.getByRole('alert').filter({hasText:'商品已下架'}).waitFor()
      await p.getByRole('link',{name:'返回商品列表',exact:true}).click()
      await p.waitForURL(u=>u.pathname===path&&u.searchParams.get('page')===page)
      if(remaining.length)await ready();else await p.locator('.empty').filter({hasText:'找不到符合條件的商品'}).waitFor()
      assert.equal(latest().page,page);assert.equal(latest().brand,'Test');assert.equal(latest().minPrice,'0')
      assert.equal(new URL(p.url()).hash,'#catalog')
     }
    })
    await scenario('related failure preserves detail and retries only related API',async(p,calls,ready,latest,state)=>{
     state.relatedFailure=true;await p.goto(base+'/products/1')
     await p.getByRole('alert').filter({hasText:'相關商品載入失敗'}).waitFor()
     await p.getByRole('heading',{name:'路由商品1',exact:true}).waitFor()
     const count=calls.filter(c=>c.path==='/api/products/1').length
     state.relatedFailure=false;await p.getByRole('button',{name:'重試相關商品',exact:true}).click()
     await p.locator('a.product-card').waitFor()
     assert.equal(calls.filter(c=>c.path==='/api/products/1').length,count)
     assert.equal(await p.getByRole('alert').count(),0)
    })
    await scenario('detail retry preserves source URL',async(p,calls,ready,latest,state)=>{
     state.detailFailure=true
     const url=base+'/products/1?returnTo='+encodeURIComponent('/category/food?size=1&page=1')
     await p.goto(url);await p.getByRole('alert').filter({hasText:'商品暫時無法載入'}).waitFor()
     state.detailFailure=false;await p.getByRole('button',{name:'重試載入商品',exact:true}).click()
     await p.getByRole('heading',{name:'路由商品1',exact:true}).waitFor()
     assert.equal(p.url(),url)
     await p.getByRole('link',{name:'返回商品列表',exact:true}).click();await p.waitForURL(base+'/category/food?size=1&page=1');await ready()
    })
    for(const failure of [false,true]){
     await scenario('slow related '+(failure?'error':'success')+' cannot replace newer product',async(p,calls,ready,latest,state)=>{
      await p.goto(base+'/products/1');await p.locator('a.product-card').waitFor()
      const path='/api/products/3/related',gate=state.gates[path]=holdResponse([product(2)],failure)
      const requested=p.waitForRequest(r=>new URL(r.url()).pathname===path)
      await p.locator('a.product-card').click();await requested
      await p.getByRole('heading',{name:'路由商品3',exact:true}).waitFor()
      const region=p.getByRole('region',{name:'相關商品',exact:true})
      await region.getByRole('status').waitFor();assert.equal(await region.getAttribute('aria-busy'),'true')
      await p.getByRole('button',{name:'加入購物車',exact:true}).waitFor()
      await p.goBack();await p.getByRole('heading',{name:'路由商品1',exact:true}).waitFor()
      await region.getByRole('link').waitFor()
      await releaseResponse(p,path,gate)
      assert.equal(new URL(p.url()).pathname,'/products/1')
      assert.equal(await p.getByRole('heading',{name:'路由商品3',exact:true}).count(),0)
      assert.equal(await region.getByText('路由商品2',{exact:true}).count(),0)
      assert.equal(await region.getByText('路由商品3',{exact:true}).count(),1)
      assert.equal(await p.getByRole('alert').count(),0)
      assert.equal(await region.getAttribute('aria-busy'),'false')
     })
    }
    await scenario('slow primary product cannot replace newer detail',async(p,calls,ready,latest,state)=>{
     await p.goto(base+'/products/1');await p.locator('a.product-card').waitFor()
     const path='/api/products/3',gate=state.gates[path]=holdResponse(product(3))
     const requested=p.waitForRequest(r=>new URL(r.url()).pathname===path)
     await p.locator('a.product-card').click();await requested
     await p.goBack();await p.getByRole('heading',{name:'路由商品1',exact:true}).waitFor()
     await releaseResponse(p,path,gate)
     assert.equal(await p.getByRole('heading',{name:'路由商品3',exact:true}).count(),0)
     assert.equal(calls.filter(c=>c.path==='/api/products/3/related').length,0)
    })
    await scenario('leave pending related request and empty retry remain safe',async(p,calls,ready,latest,state)=>{
     const path='/api/products/1/related',gate=state.gates[path]=holdResponse([product(2)])
     const requested=p.waitForRequest(r=>new URL(r.url()).pathname===path)
     await p.goto(base+'/products/1?returnTo='+encodeURIComponent('/search?q=cat&size=1'));await requested
     await p.getByRole('heading',{name:'路由商品1',exact:true}).waitFor()
     await p.getByRole('link',{name:'返回商品列表',exact:true}).click();await ready()
     await releaseResponse(p,path,gate)
     assert.equal(new URL(p.url()).pathname,'/search');assert.equal(await p.getByRole('region',{name:'相關商品',exact:true}).count(),0)
     delete state.gates[path];state.relatedFailure=true
     await p.goto(base+'/products/1');await p.getByRole('alert').filter({hasText:'相關商品載入失敗'}).waitFor()
     state.relatedFailure=false;state.relatedItems=[]
     await p.getByRole('button',{name:'重試相關商品',exact:true}).click()
     await p.getByText('目前沒有相關商品',{exact:true}).waitFor()
     await p.getByRole('heading',{name:'路由商品1',exact:true}).waitFor()
     assert.equal(await p.getByRole('alert').count(),0)
    })
    await scenario('missing category blocks product query and clear restores all products',async(p,calls,ready,latest)=>{
     await p.goto(base+'/category/missing?size=1&page=1')
     await p.getByRole('alert').filter({hasText:'分類不存在'}).waitFor()
     assert.equal(calls.filter(c=>c.path==='/api/products').length,0)
     await p.getByRole('button',{name:'清除條件',exact:true}).click();await p.waitForURL(base+'/products');await ready()
     assert.equal(latest().page,'0');assert.equal(latest().size,'12')
    })
   }finally{await server.close()}
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
