const { chromium }=require('playwright'), assert=require('node:assert/strict')
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
async function main() {
 const {createServer}=await import('vite')
 const server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5181,strictPort:true}})
 let browser; let passed=0; const errors=[]
 try {
  await server.listen(); browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,test,role='ADMIN') {
   const context=await browser.newContext(); const state={calls:[],fail:false}
   await context.addInitScript(role=>{
    const member={id:1,name:'測試管理員',role,email:'test@local'}
    localStorage.setItem('catshop_session',JSON.stringify({token:'test-token',member,epoch:'low-stock',reason:''}))
   },role)
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),route=>route.abort())
   await context.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async route=>{
    const url=new URL(route.request().url()), params=Object.fromEntries(url.searchParams); state.calls.push({path:url.pathname,params})
    const ok=data=>route.fulfill({json:{success:true,data}})
    if(url.pathname.endsWith('/inventory/low-stock')) {
     if(state.fail) return route.fulfill({status:503,json:{success:false,message:'庫存暫時無法載入'}})
     if(params.keyword==='slow') await delay(450)
     return ok({lowStockCount:30,outOfStockCount:4,items:{content:[{skuId:1,productId:1,productName:params.keyword==='slow'?'過時商品':'測試商品',skuCode:'SKU-1',specName:'1kg',categoryName:'Food',productStatus:'ON_SALE',stock:0,safetyStock:5,shortage:5}],page:Number(params.page||0),totalElements:params.outOfStockOnly==='true'?4:30,totalPages:2,last:params.page==='1'}})
    }
    if(url.pathname.endsWith('/admin/products')) return ok({content:[],totalPages:0,last:true})
    if(url.pathname.endsWith('/admin/categories')) return ok({content:[],totalPages:0,last:true})
    if(url.pathname.endsWith('/inventory/movements')) return ok({content:[],page:Number(params.page||0),totalElements:0,totalPages:0,last:true})
    return ok([])
   })
   const page=await context.newPage(); page.setDefaultTimeout(10000); page.on('pageerror',e=>errors.push(e.message))
   try {await test(page,state); passed++; console.log('PASS: '+name)} finally {await context.close()}
  }
  const base='http://127.0.0.1:5181', summary=page=>page.getByText('符合搜尋的全部預警規格：',{exact:false}).waitFor()
  await scenario('Summary is global; filters/page/reload/clear retain URL state',async(page,state)=>{
   await page.goto(base+'/inventory'); await summary(page)
   assert.match(await page.getByText('符合搜尋的全部預警規格：',{exact:false}).textContent(),/30 筆.*4 筆/)
   const before=state.calls.length; await page.getByRole('button',{name:'搜尋',exact:true}).click(); await summary(page)
   assert.ok(state.calls.length>before,'Same-query search must refresh current stock')
   await page.getByLabel('低庫存搜尋',{exact:true}).fill('SKU'); await page.getByRole('checkbox',{name:'只看缺貨',exact:true}).check()
   await page.getByRole('button',{name:'搜尋',exact:true}).click(); await summary(page)
   await page.getByRole('button',{name:'下一頁',exact:true}).click(); await page.waitForURL(/page=1/); await page.reload(); await summary(page)
   assert.equal(state.calls.filter(c=>c.path.endsWith('/low-stock')).at(-1).params.outOfStockOnly,'true')
   await page.getByRole('button',{name:'清除條件',exact:true}).click(); await page.waitForURL(url=>!url.searchParams.has('keyword')); await summary(page)
  })
  await scenario('Failure is visible and retry recovers',async(page,state)=>{
   state.fail=true; await page.goto(base+'/inventory'); await page.getByRole('button',{name:'重試載入',exact:true}).waitFor()
   state.fail=false; await page.getByRole('button',{name:'重試載入',exact:true}).click(); await summary(page)
  })
  await scenario('Old slow query cannot overwrite newer result',async(page,state)=>{
   await page.goto(base+'/inventory'); await summary(page)
   await page.getByLabel('低庫存搜尋',{exact:true}).fill('slow'); await page.getByRole('button',{name:'搜尋',exact:true}).click()
   await page.waitForURL(/keyword=slow/); await page.getByLabel('低庫存搜尋',{exact:true}).fill('fast'); await page.getByRole('button',{name:'搜尋',exact:true}).click()
   await summary(page); await delay(600); assert.equal(await page.getByText('過時商品',{exact:true}).count(),0)
  })
  await scenario('Product-management link preloads keyword and mobile table stays contained',async page=>{
   await page.setViewportSize({width:390,height:844}); await page.goto(base+'/inventory'); await summary(page)
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth))
   await page.getByRole('link',{name:'前往商品管理',exact:true}).click(); await page.waitForURL(/\/products\?keyword=/)
   assert.equal(await page.getByLabel('名稱搜尋',{exact:true}).inputValue(),'測試商品')
  })
  await scenario('Customer cannot access the inventory page',async page=>{
   await page.goto(base+'/inventory'); await page.waitForURL(/\/login/); assert.equal(await page.getByRole('heading',{name:'低庫存清單',exact:true}).count(),0)
  },'CUSTOMER')
  await scenario('URL size and inventory return survive product and movement navigation',async(page,state)=>{
   await page.goto(base+'/inventory?keyword=SKU&outOfStockOnly=true&page=1&size=50');await summary(page)
   assert.equal(state.calls.filter(c=>c.path.endsWith('/low-stock')).at(-1).params.size,'50')
   const source=page.url()
   await page.getByRole('link',{name:'前往商品管理',exact:true}).click();await page.waitForURL(/\/products\?keyword=/)
   await page.getByRole('link',{name:'返回低庫存清單',exact:true}).click();await page.waitForURL(source);await summary(page)
   await page.getByRole('link',{name:'異動紀錄',exact:true}).click();await page.waitForURL(/\/movements\?/)
   await page.reload();await page.getByRole('link',{name:'返回低庫存清單',exact:true}).click();await page.waitForURL(source);await summary(page)
   await page.getByRole('button',{name:'搜尋',exact:true}).click();await page.waitForURL(u=>!u.searchParams.has('page'));await summary(page)
   assert.equal(state.calls.filter(c=>c.path.endsWith('/low-stock')).at(-1).params.size,'50')
  })
  await scenario('Invalid inventory query makes no request and clears safely',async(page,state)=>{
   for(const query of ['page=1&page=2','keyword=A&keyword=B','size=101','outOfStockOnly=true&outOfStockOnly=false','page=1e2']){
    const before=state.calls.length;await page.goto(base+'/inventory?'+query);await page.getByRole('alert').waitFor();assert.equal(state.calls.length,before)
   }
   await page.getByRole('button',{name:'清除條件',exact:true}).click();await page.waitForURL(base+'/inventory');await summary(page)
  })
  assert.equal(passed,7); assert.deepEqual(errors,[])
 } finally {await browser?.close();await server.close()}
}
main().catch(error=>{console.error(error);process.exitCode=1})
