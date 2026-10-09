const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite'),front=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5201,strictPort:true}}),admin=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5202,strictPort:true}})
 let browser;const errors=[]
 try{
  await front.listen();await admin.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  for(const [surface,path,extra,prefix] of [
   ['front','products','brand=Test&sort=name,asc',''],['front','orders','',''],['front','wishlist','',''],
   ['admin','products','isActive=false&sort=name,desc&returnTo=%2Finventory%3Fkeyword%3DSKU',''],['admin','orders','status=PAID&sort=totalAmount,asc',''],
   ['admin','categories','isActive=false',''],['admin','members','role=CUSTOMER',''],['admin','coupons','',''],
   ['admin','inventory','outOfStockOnly=true',''],['admin','movements','skuId=1&reason=ORDER_PLACED',''],['admin','members/2/history','page=1&size=50','紀錄']
  ]){
   const context=await browser.newContext({viewport:{width:390,height:844}}),calls=[]
   await context.addInitScript(role=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'測試',role},epoch:'pager'})),surface==='admin'?'ADMIN':'CUSTOMER')
   await context.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
   await context.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async r=>{
    const url=new URL(r.request().url()),query=Object.fromEntries(url.searchParams),endpoint=url.pathname;calls.push({endpoint,query})
    const ok=data=>r.fulfill({json:{success:true,data}})
    if(endpoint==='/api/wishlist/ids')return ok([1])
    if(endpoint==='/api/products/brands')return ok(['Test'])
    if(endpoint==='/api/categories')return ok([])
    const member={id:2,name:'會員',email:'test@test.local',role:'CUSTOMER',status:'ACTIVE',statusVersion:0}
    if(endpoint.endsWith('/members/2'))return ok(member)
    const item=endpoint.includes('low-stock')?{skuId:1,productId:1,productName:'商品',stock:1,safetyStock:2,shortage:1}:
     endpoint.includes('movements')?{id:1,skuId:1,productName:'商品',beforeStock:2,afterStock:1,quantityDelta:-1,reason:'ORDER_PLACED'}:
     endpoint.endsWith('history')?{id:1,reason:'原因',fromStatus:'ACTIVE',toStatus:'DISABLED'}:
     endpoint.endsWith('members')?member:endpoint.endsWith('coupons')?{id:1,code:'CODE',startsAt:'2026-01-01T00:00:00',endsAt:'2027-01-01T00:00:00'}:
     endpoint.endsWith('orders')?{id:1,orderNo:'ORDER',status:'PAID',totalAmount:100}:
     {id:1,name:'商品',brand:'Test',basePrice:100,status:'ON_SALE',isActive:true,sortOrder:0,available:true}
    const page=Number(query.page||0),size=Number(query.size||20),totalPages=Math.ceil(120/size),result={content:page<totalPages?[item]:[],page,size,totalElements:120,totalPages,last:page+1>=totalPages}
    return ok(endpoint.endsWith('low-stock')?{items:result,lowStockCount:120,outOfStockCount:120}:result)
   })
   const p=await context.newPage();p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message))
   const base='http://127.0.0.1:'+(surface==='front'?5201:5202),history=!!prefix,pageKey=history?'historyPage':'page',sizeKey=history?'historySize':'size'
   await p.goto(base+'/'+path+'?'+extra)
   await p.getByLabel(prefix+'每頁筆數',{exact:true}).selectOption('50');await p.waitForURL(u=>u.searchParams.get(sizeKey)==='50'&&u.searchParams.get(pageKey)==='0')
   const jump=p.getByRole('button',{name:prefix+'前往',exact:true});await p.locator('.paging-jump button:not([disabled])').waitFor()
   await p.getByLabel(prefix+'前往頁碼',{exact:true}).fill('2');await jump.click();await p.waitForURL(u=>u.searchParams.get(pageKey)==='1')
   await p.reload();await p.locator('.paging-jump button:not([disabled])').waitFor();assert.equal(await p.getByLabel(prefix+'每頁筆數',{exact:true}).inputValue(),'50')
   const query=new URL(p.url()).searchParams
   for(const [key,value] of new URLSearchParams(extra))assert.equal(query.get(key),value)
   const latest=()=>calls.filter(c=>history?c.endpoint.endsWith('/history'):surface==='front'?c.endpoint==='/api/'+path:path==='inventory'?c.endpoint.endsWith('/low-stock'):path==='movements'?c.endpoint.endsWith('/movements'):c.endpoint==='/admin-api/admin/'+path).at(-1)
   assert.equal(latest().query.size,'50');assert.equal(latest().query.page,'1')
   for(const value of ['0','4','1.5','-1','abc']){
    const count=calls.length;await p.getByLabel(prefix+'前往頁碼',{exact:true}).fill(value);await jump.click();await p.locator('.list-pagination [role=alert]').waitFor();assert.equal(calls.length,count)
   }
   await p.getByLabel(prefix+'前往頁碼',{exact:true}).fill('3');await jump.click();await p.waitForURL(u=>u.searchParams.get(pageKey)==='2')
   await p.getByLabel(prefix+'每頁筆數',{exact:true}).selectOption('20');await p.waitForURL(u=>u.searchParams.get(sizeKey)==='20'&&u.searchParams.get(pageKey)==='0')
   if(surface==='admin'&&['products','orders'].includes(path)){
    await p.getByLabel(path==='products'?'品牌篩選':'訂單編號',{exact:true}).fill('尚未送出')
    await p.getByLabel('列表排序',{exact:true}).selectOption('id,asc');await p.waitForURL(u=>u.searchParams.get('sort')==='id,asc');await p.locator('tbody tr').first().waitFor();assert.equal(latest().query.sort,'id,asc')
    assert.equal(latest().query[path==='products'?'brand':'orderNo'],undefined)
    for(const [key,value] of new URLSearchParams(extra))if(key!=='sort')assert.equal(new URL(p.url()).searchParams.get(key),value)
   }
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));console.log('PASS '+surface+' '+path+' size, jump, invalid input, reload, filters and mobile');await context.close()
  }
  const context=await browser.newContext();await context.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
  await context.route(url=>url.pathname.startsWith('/api/'),r=>r.fulfill({json:{success:true,data:new URL(r.request().url()).pathname==='/api/products'?{content:[],page:0,size:12,totalPages:0,totalElements:0,last:true}:[]}}))
  const p=await context.newPage();await p.goto('http://127.0.0.1:5201/products?page=99&brand=Test&size=12');await p.waitForURL(u=>u.searchParams.get('page')==='0')
  await p.locator('.empty').filter({hasText:'找不到符合條件的商品'}).waitFor();assert.equal(await p.getByRole('button',{name:'前往',exact:true}).isDisabled(),true)
  assert.equal(new URL(p.url()).searchParams.get('brand'),'Test');console.log('PASS storefront empty result clamps page and disables jump');await context.close()
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await front.close();await admin.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
