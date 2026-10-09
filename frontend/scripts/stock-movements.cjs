const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite')
 const server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5182,strictPort:true}})
 let browser;const errors=[];let passed=0
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,test,role='ADMIN'){
   const context=await browser.newContext(),state={calls:[],fail:false}
   await context.addInitScript(role=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Admin',role},epoch:'movements',reason:''})),role)
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async r=>{
    const url=new URL(r.request().url()),params=Object.fromEntries(url.searchParams);state.calls.push({params,method:r.request().method()})
    if(state.fail)return r.fulfill({status:503,json:{success:false,message:'異動暫時無法載入'}})
    if(params.skuId==='1')await new Promise(resolve=>setTimeout(resolve,450))
    return r.fulfill({json:{success:true,data:{content:[{id:1,skuId:params.skuId||2,productName:params.skuId==='1'?'過時商品':'測試商品',skuCode:'SKU-2',reason:'ORDER_PLACED',beforeStock:10,afterStock:8,quantityDelta:-2,actorMemberId:1,orderNo:'ORDER-1',createdAt:'2026-10-08T12:00:00'}],page:Number(params.page||0),totalElements:21,totalPages:2,last:params.page==='1'}}})
   })
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(10000)
   try{await test(page,state);assert.ok(state.calls.every(c=>c.method==='GET'));passed++;console.log('PASS: '+name)}finally{await context.close()}
  }
  const base='http://127.0.0.1:5182',ready=p=>p.getByText('共 21 筆異動',{exact:true}).waitFor()
  await scenario('Filters paging reload clear and same-query refresh',async(p,s)=>{
   await p.goto(base+'/movements');await ready(p);const n=s.calls.length
   await p.getByRole('button',{name:'搜尋',exact:true}).click();await ready(p);assert.ok(s.calls.length>n)
   await p.getByLabel('SKU ID',{exact:true}).fill('2');await p.getByLabel('異動原因').selectOption('ORDER_PLACED');await p.getByRole('button',{name:'搜尋',exact:true}).click();await ready(p)
   await p.getByRole('button',{name:'下一頁'}).click();await p.waitForURL(/page=1/);await p.reload();await ready(p)
   assert.equal(s.calls.at(-1).params.reason,'ORDER_PLACED');assert.equal(s.calls.at(-1).params.skuId,'2')
   await p.getByRole('button',{name:'清除條件'}).click();await p.waitForURL(u=>!u.searchParams.has('skuId'));await ready(p)
  })
  await scenario('Failure retry and invalid SKU',async(p,s)=>{
   s.fail=true;await p.goto(base+'/movements');await p.getByRole('button',{name:'重試載入'}).waitFor();s.fail=false
   await p.getByRole('button',{name:'重試載入'}).click();await ready(p)
   await p.goto(base+'/movements?skuId=0');await p.getByRole('alert').waitFor();assert.match(await p.getByRole('alert').textContent(),/正整數/)
  })
  await scenario('Slow result does not replace new query',async p=>{
   await p.goto(base+'/movements');await ready(p);await p.getByLabel('SKU ID',{exact:true}).fill('1');await p.getByRole('button',{name:'搜尋',exact:true}).click();await p.waitForURL(/skuId=1/)
   await p.getByLabel('SKU ID',{exact:true}).fill('2');await p.getByRole('button',{name:'搜尋',exact:true}).click();await ready(p)
   await new Promise(resolve=>setTimeout(resolve,600));assert.equal(await p.getByText('過時商品',{exact:true}).count(),0)
  })
  await scenario('Mobile layout contains table and displays quantities',async p=>{
   await p.setViewportSize({width:390,height:844});await p.goto(base+'/movements');await ready(p)
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.getByText('10 → 8',{exact:true}).waitFor();await p.getByText('-2',{exact:true}).waitFor()
  })
  await scenario('Customer cannot enter admin history',async p=>{await p.goto(base+'/movements');await p.waitForURL(/\/login/);assert.equal(await p.getByRole('heading',{name:'庫存異動紀錄',exact:true}).count(),0)},'CUSTOMER')
  await scenario('URL size, duplicate parameter rejection and safe inventory return',async(p,s)=>{
   await p.goto(base+'/movements?size=50&skuId=2&page=1');await ready(p);assert.equal(s.calls.at(-1).params.size,'50')
   await p.getByRole('button',{name:'搜尋',exact:true}).click();await p.waitForURL(u=>!u.searchParams.has('page'));await ready(p);assert.equal(s.calls.at(-1).params.size,'50')
   for(const query of ['page=1&page=2','reason=ORDER_PLACED&reason=SKU_CREATED','skuId=1&skuId=2','size=101','page=1e2']){
    const before=s.calls.length;await p.goto(base+'/movements?'+query);await p.getByRole('alert').waitFor();assert.equal(s.calls.length,before)
   }
   for(const returnTo of ['https://example.com','//example.com','/members','/movements']){
    await p.goto(base+'/movements?returnTo='+encodeURIComponent(returnTo))
    assert.equal(await p.getByRole('link',{name:'返回低庫存清單',exact:true}).getAttribute('href'),'/inventory')
   }
  })
  assert.equal(passed,6);assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
