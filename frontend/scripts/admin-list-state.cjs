const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5199,strictPort:true}})
 let browser
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  const ctx=await browser.newContext(),calls=[],errors=[],base='http://127.0.0.1:5199'
  await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'管理員',role:'ADMIN'},epoch:'admin-lists'})))
  await ctx.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
  let failure=false
  await ctx.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async r=>{
   const url=new URL(r.request().url()),section=url.pathname.split('/')[3],query=Object.fromEntries(url.searchParams)
   calls.push({section,query})
   if(failure&&section==='coupons')return r.fulfill({status:503,json:{success:false,error:{message:'測試載入失敗'}}})
   const items={products:{id:1,name:'測試商品',status:'ON_SALE',basePrice:100,totalStock:2},categories:{id:1,name:'測試分類',slug:'test',sortOrder:0,isActive:false},orders:{id:1,orderNo:'ORDER1',status:'PENDING',totalAmount:100},members:{id:1,name:'會員',email:'test@test.local',role:'CUSTOMER',status:'ACTIVE'},coupons:{id:1,code:'TEST',discountAmount:100,minimumAmount:500,maxUses:10,active:true,startsAt:'2026-01-01T00:00:00',endsAt:'2027-01-01T00:00:00'}}
   const data=/\/\d+$/.test(url.pathname)?items[section]:{content:[items[section]],page:Number(query.page||0),size:Number(query.size||20),totalPages:3,totalElements:3,last:Number(query.page||0)===2}
   await r.fulfill({json:{success:true,data}})
  })
  const p=await ctx.newPage();p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message))
  const listCall=section=>calls.filter(c=>c.section===section).at(-1)
  for(const section of ['products','orders','categories','members','coupons']){
   const extra=section==='products'?'&sort=name,desc&isActive=false':section==='orders'?'&sort=totalAmount,asc&from=2024-02-29':section==='categories'?'&isActive=false':''
   await p.goto(base+'/'+section+'?page=1&size=50'+extra)
   await p.locator('tbody tr').first().waitFor()
   await p.reload();await p.locator('tbody tr').first().waitFor()
   assert.equal(listCall(section).query.size,'50');assert.equal(listCall(section).query.page,'1')
   if(section==='products')assert.equal(listCall(section).query.sort,'name,desc')
   if(section==='orders')assert.equal(listCall(section).query.sort,'totalAmount,asc')
   await p.getByRole('button',{name:'下一頁',exact:true}).click();await p.waitForURL(u=>u.searchParams.get('page')==='2');await p.locator('tbody tr').first().waitFor()
   assert.equal(listCall(section).query.size,'50')
   await p.goBack();await p.locator('tbody tr').first().waitFor();assert.equal(new URL(p.url()).searchParams.get('page'),'1')
   const target=section==='products'?'/products/1/edit':section==='orders'?'/orders/1':section==='categories'?'/categories/1/edit':section==='members'?'/members/1':'/coupons/1/edit'
   if(target){
    if(['products','categories','coupons'].includes(section))await p.getByRole('button',{name:'編輯',exact:true}).click()
    else await p.locator('a[href^="'+target+'?"]').first().click()
    await p.waitForURL(u=>u.pathname===target)
    const back=section==='products'?'返回商品列表':section==='orders'?'返回訂單列表':section==='categories'?'返回分類列表':section==='members'?'返回會員列表':'返回優惠券列表'
    const link=p.getByRole('link',{name:back,exact:true});await link.waitFor()
    const href=new URL(await link.getAttribute('href'),base);assert.equal(href.searchParams.get('size'),'50');assert.equal(href.searchParams.get('page'),'1')
    if(['products','orders'].includes(section))assert.ok(href.searchParams.has('sort'))
    await link.click();await p.locator('tbody tr').first().waitFor()
   }
   if(section!=='coupons'){
    await p.getByRole('button',{name:'搜尋',exact:true}).click();await p.waitForURL(u=>!u.searchParams.has('page'));await p.locator('tbody tr').first().waitFor()
    assert.equal(listCall(section).query.size,'50');assert.equal(listCall(section).query.page,'0')
    if(['products','orders'].includes(section))assert.ok(listCall(section).query.sort)
   }
   console.log('PASS '+section+' paging reload history and return context')
  }
  for(const path of ['/products?page=1&page=2','/products?brand=A&brand=B','/products?sort=secret,asc','/categories?isActive=true&isActive=false','/categories?size=0','/orders?from=2026-02-30','/orders?memberId=0','/members?role=CUSTOMER&role=ADMIN','/members?size=101','/coupons?page=1&page=2','/coupons?size=0']){
   const before=calls.length;await p.goto(base+path);await p.getByRole('alert').first().waitFor();assert.equal(calls.length,before)
  }
  console.log('PASS invalid and duplicate queries make no list API')
  failure=true;await p.goto(base+'/coupons?size=50');await p.getByRole('alert').waitFor();assert.equal(await p.getByText('尚無優惠券',{exact:true}).count(),0)
  failure=false;await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByText('TEST',{exact:true}).waitFor();assert.equal(listCall('coupons').query.size,'50')
  console.log('PASS failed coupon load hides false empty state and retry preserves size')
  assert.deepEqual(errors,[]);await ctx.close()
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
