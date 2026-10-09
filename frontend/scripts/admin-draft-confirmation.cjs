const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5218,strictPort:true}})
 await server.listen();let browser
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const width of [1440,390])for(const kind of ['product','category','coupon'])for(const mode of ['new','edit']){
   const ctx=await browser.newContext({viewport:{width,height:900}}),writes=[],errors=[];ctx.setDefaultTimeout(12000)
   await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'管理員',role:'ADMIN'},epoch:'admin-draft'})))
   await ctx.route(url=>url.pathname.startsWith('/admin-api/'),r=>{
    const pathname=new URL(r.request().url()).pathname.replace('/admin-api/','/api/')
    if(r.request().method()!=='GET')writes.push(pathname)
    let data=[]
    if(pathname==='/api/admin/categories')data={content:[1,2].map(id=>({id,name:'原分類'+id,slug:'category-'+id,parentId:null,sortOrder:0,isActive:true})),totalPages:1,last:true}
    if(/^\/api\/admin\/products\/[12]$/.test(pathname)){const id=Number(pathname.split('/').pop());data={id,name:'原商品'+id,slug:'product-'+id,categoryId:1,basePrice:100,status:'ON_SALE',isActive:true,images:[],skus:[]}}
    if(/^\/api\/admin\/coupons\/[12]$/.test(pathname)){const id=Number(pathname.split('/').pop());data={id,code:'DEMO'+id,discountAmount:100,minimumAmount:500,maxUses:100,active:true,version:3,startsAt:'2026-10-01T00:00:00',endsAt:'2026-11-01T00:00:00'}}
    return r.fulfill({json:{success:true,data}})
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   const plural={product:'products',category:'categories',coupon:'coupons'}[kind],target=`/${plural}/${mode==='new'?'new':'1/edit'}`
   await p.goto('http://127.0.0.1:5218'+target)
   const field=p.getByLabel(kind==='coupon'?'折抵金額':'名稱',{exact:true});await field.waitFor()
   const baseline=await field.inputValue(),draft=kind==='coupon'?'555':'草稿'
   await field.fill(draft);const reload=p.getByRole('button',{name:'重新載入',exact:true}),dialog=p.getByRole('dialog')
   await reload.click();await dialog.waitFor()
   assert.equal(await p.evaluate(()=>document.activeElement.textContent),'繼續編輯');assert.equal(await field.isDisabled(),true)
   await p.locator('.page form').evaluate(f=>f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));assert.deepEqual(writes,[])
   if(process.env.PREVIEW_SCREENSHOT_DIR&&mode==='edit'){
    await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))})
    await p.screenshot({path:path.join(process.env.PREVIEW_SCREENSHOT_DIR,`admin-draft-${kind}-${width}.png`)})
   }
   await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(await field.inputValue(),draft)
   await reload.click();await dialog.getByRole('button',{name:'放棄修改並重新載入',exact:true}).click()
   await field.waitFor();await p.waitForFunction(()=>!document.querySelector('.page [role="status"]'))
   assert.equal(await field.inputValue(),baseline);await field.fill(draft)
   const back=p.getByRole('link',{name:`返回${{product:'商品',category:'分類',coupon:'優惠券'}[kind]}列表`,exact:true})
   await back.click();await dialog.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(new URL(p.url()).pathname,target);assert.equal(await field.inputValue(),draft)
   assert.equal(await p.evaluate(()=>{const e=new Event('beforeunload',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented}),true)
   const alternate=`/${plural}/2/edit`
   async function navigate(url){await p.evaluate(async url=>{const {default:router}=await import('/src/router/index.js');void router.push(url)},url)}
   await navigate(alternate);await dialog.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(new URL(p.url()).pathname,target)
   await navigate(alternate);await dialog.getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL(u=>u.pathname===alternate);await field.waitFor()
   assert.equal(await field.inputValue(),kind==='coupon'?'100':kind==='product'?'原商品2':'原分類2')
   await field.fill(draft);await back.click();await dialog.waitFor()
   await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()})
   await p.waitForURL(u=>u.pathname==='/login');await dialog.waitFor({state:'hidden'})
   assert.deepEqual(writes,[]);assert.deepEqual(errors,[]);assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
   await ctx.close();console.log(`PASS Admin draft ${kind} ${mode} ${width}: retain/reload, safe focus, no writes, route reuse, native unload and logout`)
  }
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
