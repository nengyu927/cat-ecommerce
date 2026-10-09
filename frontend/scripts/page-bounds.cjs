const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5200,strictPort:true}})
 let browser
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  const ctx=await browser.newContext({viewport:{width:390,height:844}}),base='http://127.0.0.1:5200',calls=[],errors=[]
  await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Admin',role:'ADMIN'},epoch:'page-bounds'})))
  await ctx.route(url=>!['localhost','127.0.0.1'].includes(url.hostname),r=>r.abort())
  const member={id:2,name:'會員',email:'test@test.local',role:'CUSTOMER',status:'ACTIVE',statusVersion:0}
  let fail=false,slow=false,empty=false,releaseSlow
  const slowGate=new Promise(resolve=>{releaseSlow=resolve})
  await ctx.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async r=>{
   const url=new URL(r.request().url()),query=Object.fromEntries(url.searchParams),path=url.pathname
   calls.push({path,query})
   if(fail)return r.fulfill({status:503,json:{success:false,error:{message:'測試讀取失敗'}}})
   if(slow&&query.page==='99')await slowGate
   if(path.endsWith('/members/2'))return r.fulfill({json:{success:true,data:member}})
   const item=path.includes('/low-stock')?{skuId:1,productId:1,productName:'預警商品',stock:0,safetyStock:5,shortage:5}:
    path.includes('/movements')?{id:1,skuId:1,productName:'庫存商品',beforeStock:2,afterStock:1,quantityDelta:-1,reason:'ORDER_PLACED'}:
    path.endsWith('/history')?{id:1,reason:'歷程原因',fromStatus:'ACTIVE',toStatus:'DISABLED'}:
    path.endsWith('/members')?member:path.endsWith('/coupons')?{id:1,code:'TEST',active:true,startsAt:'2026-01-01T00:00:00',endsAt:'2027-01-01T00:00:00'}:
    path.endsWith('/orders')?{id:1,orderNo:'ORDER1',status:'PENDING',totalAmount:1}:{id:1,name:'商品分類',status:'ON_SALE',isActive:true,sortOrder:0}
   const page=Number(query.page||0),totalPages=empty?0:2,result={content:!empty&&page<2?[item]:[],page,size:Number(query.size||20),totalPages,totalElements:empty?0:100,last:page>=1}
   await r.fulfill({json:{success:true,data:path.endsWith('/low-stock')?{items:result,lowStockCount:100,outOfStockCount:100}:result}})
  })
  const p=await ctx.newPage();p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message))
  for(const section of ['products','orders','categories','members','coupons','inventory','movements']){
   await p.goto(base+'/'+section+'?size=50&page=99')
   await p.waitForURL(u=>u.searchParams.get('page')==='1');await p.locator('tbody tr').first().waitFor()
   assert.equal(new URL(p.url()).searchParams.get('size'),'50')
   await p.reload();await p.locator('tbody tr').first().waitFor()
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   console.log('PASS '+section+' clamps oversized page, preserves size and reloads on mobile')
  }
  await p.goto(base+'/members/2/history?page=1&size=50&historyPage=99&historySize=2')
  await p.waitForURL(u=>u.searchParams.get('historyPage')==='1');await p.getByText('歷程原因',{exact:true}).waitFor()
  assert.equal(new URL(p.url()).searchParams.get('page'),'1');assert.equal(new URL(p.url()).searchParams.get('size'),'50')
  assert.equal(calls.filter(c=>c.path.endsWith('/history')).at(-1).query.size,'2')
  const pageZero=p.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/history')&&new URL(r.url()).searchParams.get('page')==='0')
  await p.getByRole('button',{name:'紀錄上一頁',exact:true}).click();await p.waitForURL(u=>u.searchParams.get('historyPage')==='0');await pageZero;await p.getByText('歷程原因',{exact:true}).waitFor()
  assert.equal(new URL(p.url()).searchParams.get('historySize'),'2')
  console.log('PASS history page/size independent from original member list')
  for(const query of ['historyPage=-1','historyPage=1&historyPage=2','historySize=0','historySize=101','historySize=2&historySize=3']){
   const before=calls.length;await p.goto(base+'/members/2/history?size=50&'+query);await p.getByRole('alert').waitFor();assert.equal(calls.length,before)
  }
  await p.getByRole('button',{name:'清除紀錄查詢',exact:true}).click();await p.getByText('歷程原因',{exact:true}).waitFor()
  assert.equal(new URL(p.url()).searchParams.get('size'),'50')
  assert.equal(calls.filter(c=>c.path.endsWith('/history')).at(-1).query.size,'10')
  console.log('PASS invalid history query makes no API and recovers without losing list size')
  empty=true;await p.goto(base+'/coupons?size=50&page=99');await p.waitForURL(u=>u.searchParams.get('page')==='0');await p.getByText('尚無優惠券',{exact:true}).waitFor();empty=false
  console.log('PASS zero-page result replaces out-of-range query with page zero')
  fail=true;await p.goto(base+'/members?page=99');await p.getByRole('alert').waitFor();assert.equal(new URL(p.url()).searchParams.get('page'),'99');fail=false
  console.log('PASS request failure never pretends the page was successfully clamped')
  slow=true;const pendingRequest=p.waitForRequest(r=>r.url().includes('/low-stock')&&r.url().includes('page=99'));await p.goto(base+'/inventory?page=99');await pendingRequest
  await p.getByRole('link',{name:'會員管理',exact:true}).click();await p.waitForURL('**/members');await p.locator('tbody tr').first().waitFor()
  const oldResponse=p.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/low-stock')&&new URL(r.url()).searchParams.get('page')==='99')
  releaseSlow();await (await oldResponse).finished()
  await p.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
  assert.equal(new URL(p.url()).pathname,'/members');assert.equal(new URL(p.url()).searchParams.has('page'),false)
  console.log('PASS old inventory response cannot replace a newer module URL')
  assert.deepEqual(errors,[]);await ctx.close()
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
