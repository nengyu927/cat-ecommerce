const {chromium}=require('playwright'),assert=require('node:assert/strict')
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
const product=id=>({id,name:'商品'+id,brand:'Test',basePrice:100,skus:[],images:[]})
const order=id=>({id,orderNo:'ORDER'+id,status:'PENDING',totalAmount:100,createdAt:'2026-10-09T00:00:00',items:[]})
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5198,strictPort:true}})
 let browser;const errors=[]
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  const base='http://127.0.0.1:5198'
  async function scenario(name,test){
   const context=await browser.newContext({viewport:{width:390,height:844}}),state={calls:[],wishlist:[product(1),product(2)],slow:false,fail:false}
   await context.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Member',role:'CUSTOMER'},epoch:'lists'})))
   await context.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
   await context.route(url=>url.pathname.startsWith('/api/'),async r=>{
    const url=new URL(r.request().url()),path=url.pathname,method=r.request().method(),query=Object.fromEntries(url.searchParams)
    state.calls.push({path,method,query})
    const ok=data=>r.fulfill({json:{success:true,data}})
    const page=Number(query.page||0),size=Number(query.size||12)
    if(['/api/products','/api/orders','/api/wishlist'].includes(path)){
     if(state.fail)return r.fulfill({status:503,json:{success:false,error:{message:'列表無法載入'}}})
     const data=path.endsWith('/wishlist')?state.wishlist:path.endsWith('/orders')?[order(1),order(2),order(3)]:[product(1),product(2),product(3)]
     const result={content:data.slice(page*size,(page+1)*size),totalElements:data.length,totalPages:Math.ceil(data.length/size),page,last:(page+1)*size>=data.length}
     if(state.slow&&page===1)await delay(500)
     return ok(result)
    }
    if(path==='/api/wishlist/ids')return ok(state.wishlist.map(p=>p.id))
    if(/^\/api\/wishlist\/\d+$/.test(path)){state.wishlist=state.wishlist.filter(p=>p.id!==Number(path.split('/').pop()));return ok(state.wishlist.map(p=>p.id))}
    if(path==='/api/products/brands')return ok(['Test'])
    if(path.startsWith('/api/categories/slug/'))return ok({id:1,name:'分類'})
    if(path.includes('/categories'))return ok([])
    if(/\/products\/\d+\/related$/.test(path))return ok([product(1)])
    if(/\/products\/\d+$/.test(path))return ok(product(Number(path.split('/').pop())))
    if(/\/orders\/\d+\/shipment$/.test(path))return ok(null)
    if(/\/orders\/\d+$/.test(path))return ok(order(Number(path.split('/').pop())))
    if(path==='/api/cart')return ok({items:[],totalQuantity:0,totalAmount:0})
    return ok(null)
   })
   const p=await context.newPage();p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message))
   try{await test(p,state);console.log('PASS '+name)}finally{await context.close()}
  }
  await scenario('Orders paging reload, detail return, history and mobile',async(p,s)=>{
   await p.goto(base+'/orders?size=1&page=1');await p.getByText('ORDER2',{exact:true}).waitFor();await p.reload();await p.getByText('ORDER2',{exact:true}).waitFor()
   await p.getByRole('link',{name:'詳情',exact:true}).click();await p.getByRole('heading',{name:'訂單 ORDER2',exact:true}).waitFor()
   await p.getByRole('link',{name:'返回訂單列表',exact:true}).click();await p.waitForURL('**/orders?size=1&page=1')
   await p.getByRole('button',{name:'下一頁',exact:true}).click();await p.waitForURL(/page=2/);await p.getByText('ORDER3',{exact:true}).waitFor()
   await p.goBack();await p.getByText('ORDER2',{exact:true}).waitFor()
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  })
  await scenario('Wishlist detail and related product return preserve size/page',async(p,s)=>{
   await p.goto(base+'/wishlist?size=1&page=1');await p.locator('.product-name').waitFor()
await p.locator('.product-image-wrap a').click();await p.getByRole('link',{name:'返回收藏列表',exact:true}).waitFor()
   await p.locator('a.product-card').first().click();await p.getByRole('heading',{name:'商品1',exact:true}).waitFor()
   await p.getByRole('link',{name:'返回收藏列表',exact:true}).click();await p.waitForURL('**/wishlist?size=1&page=1')
   await p.reload();await p.locator('.product-name').waitFor();assert.equal(s.calls.filter(c=>c.path==='/api/wishlist').at(-1).query.page,'1')
  })
  await scenario('Remove final row clamps empty last page using replace',async(p,s)=>{
   await p.goto(base+'/wishlist?size=1&page=1');await p.getByRole('button',{name:'取消收藏：商品2',exact:true}).click()
   await p.waitForURL(/page=0/);await p.getByText('商品1',{exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.path==='/api/wishlist').at(-1).query.size,'1')
  })
  await scenario('Product filters detail related return and reload preserve source',async(p,s)=>{
   const query='?q=cat&brand=Test&inStock=false&minPrice=0&size=1&page=1&sort=price,asc'
   await p.goto(base+'/search'+query);await p.locator('.product-image-wrap a').click();await p.waitForURL(u=>u.pathname==='/products/2')
   await p.reload();await p.getByRole('link',{name:'返回商品列表',exact:true}).click()
   await p.waitForURL(u=>u.pathname==='/search'&&u.searchParams.get('page')==='1')
   assert.equal(new URL(p.url()).searchParams.get('minPrice'),'0')
   assert.equal(new URL(p.url()).searchParams.get('inStock'),'false')
   assert.equal(s.calls.filter(c=>c.path==='/api/products').at(-1).query.size,'1')
  })
  await scenario('Invalid scalar page size sort ID and duplicate values make no list API',async(p,s)=>{
   for(const path of ['/orders?page=-1','/wishlist?size=101','/products?page=1.2','/products?sort=password,asc','/products?categoryId=0','/orders?page=1&page=2']){
    const count=s.calls.filter(c=>['/api/orders','/api/wishlist','/api/products'].includes(c.path)).length
    await p.goto(base+path);await p.getByRole('alert').first().waitFor()
    assert.equal(s.calls.filter(c=>['/api/orders','/api/wishlist','/api/products'].includes(c.path)).length,count)
   }
   await p.goto(base+'/orders?page=-1');await p.getByRole('button',{name:'清除查詢',exact:true}).click();await p.waitForURL('**/orders');await p.getByText('ORDER1',{exact:true}).waitFor()
  })
  await scenario('External and wrong-surface return targets use safe fallback',async(p,s)=>{
   for(const value of ['https://example.com','//example.com','/admin/products','/products/2']){
    await p.goto(base+'/products/1?returnTo='+encodeURIComponent(value))
    assert.equal(await p.getByRole('link',{name:'返回商品列表',exact:true}).getAttribute('href'),'/products')
   }
   await p.goto(base+'/orders/1?returnTo='+encodeURIComponent('/products'))
   assert.equal(await p.getByRole('link',{name:'返回訂單列表',exact:true}).getAttribute('href'),'/orders')
  })
  await scenario('Failed orders hide stale rows and retry without losing query',async(p,s)=>{
   s.fail=true;await p.goto(base+'/orders?size=1&page=1');await p.getByRole('alert').filter({hasText:'列表無法載入'}).waitFor()
   assert.equal(await p.getByText('還沒有任何訂單',{exact:false}).count(),0)
   s.fail=false;await p.getByRole('button',{name:'重試載入',exact:true}).click();await p.getByText('ORDER2',{exact:true}).waitFor()
  })
  await scenario('Slow old orders cannot replace a newer list URL',async(p,s)=>{
   s.slow=true;await p.goto(base+'/orders?size=1&page=1')
   await p.getByRole('link',{name:'我的訂單',exact:true}).first().click();await p.getByText('ORDER1',{exact:true}).waitFor();await delay(600)
   assert.equal(new URL(p.url()).searchParams.has('page'),false)
   assert.equal(s.calls.filter(c=>c.path==='/api/orders').at(-1).query.page,'0')
  })
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
