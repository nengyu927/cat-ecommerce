const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5219,strictPort:true}})
 await server.listen();let browser
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const width of [1440,390])for(const kind of ['products','categories']){
   const ctx=await browser.newContext({viewport:{width,height:900}});ctx.setDefaultTimeout(12000)
   const session={token:'test',member:{id:1,name:'管理員',role:'ADMIN'},epoch:'list-confirmation'}
   await ctx.addInitScript(s=>localStorage.setItem('catshop_session',JSON.stringify(s)),session)
   const calls=[],errors=[];let fail=false,release=null,slow=false
   const item={id:1,name:kind==='products'?'測試商品 <標記>':'測試分類 <標記>',status:'ON_SALE',isActive:true,basePrice:100,totalStock:10,sortOrder:0}
   await ctx.route(url=>url.pathname.startsWith('/admin-api/'),async r=>{
    const url=new URL(r.request().url()),method=r.request().method(),pathname=url.pathname
    calls.push({method,path:pathname,query:Object.fromEntries(url.searchParams)})
    if(method==='DELETE'){
     assert.equal(pathname,`/admin-api/admin/${kind}/1`)
     if(slow)await new Promise(done=>{release=done})
     if(fail)return r.fulfill({status:409,json:{success:false,error:{message:kind==='products'?'商品狀態已變更':'分類仍有子分類，無法停用'}}})
     item.isActive=false;item.status='OFF_SHELF';return r.fulfill({json:{success:true,data:null}})
    }
    const isCurrent=pathname===`/admin-api/admin/${kind}`
    const data={content:isCurrent?[{...item}]:[],totalPages:2,last:false}
    return r.fulfill({json:{success:true,data}})
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   const action=kind==='products'?'下架':'停用',approval=`確認${kind==='products'?'下架商品':'停用分類'}`
   const target=`/${kind}?keyword=測試&page=1&size=20`,url='http://127.0.0.1:5219'+target
   const button=p.getByRole('button',{name:action,exact:true}),dialog=p.getByRole('dialog')
   const idle=()=>p.waitForFunction(()=>!document.querySelector('.spinner'))
   const writes=()=>calls.filter(c=>c.method==='DELETE')
   async function navigate(to){await p.evaluate(async to=>{const {default:router}=await import('/src/router/index.js');await router.push(to)},to);await p.waitForURL(u=>u.pathname===to.split('?')[0])}
   await p.goto(url);await button.waitFor();await idle()
   await button.click();await dialog.waitFor();assert.equal(await p.evaluate(()=>document.activeElement.textContent),'保留不變')
   await dialog.getByText(new RegExp(item.name.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'))).waitFor()
   assert.equal(await dialog.locator('標記').count(),0);assert.equal(await button.isDisabled(),true)
   assert.equal(await p.getByLabel('名稱搜尋',{exact:true}).isDisabled(),true)
   assert.equal(await p.getByRole('button',{name:'下一頁',exact:true}).isDisabled(),true)
   await p.getByRole('form',{name:'管理列表篩選'}).evaluate(f=>f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));assert.equal(writes().length,0)
   if(process.env.PREVIEW_SCREENSHOT_DIR){await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))});await p.screenshot({path:path.join(process.env.PREVIEW_SCREENSHOT_DIR,`admin-list-confirmation-${kind}-${width}.png`)})}
   await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(item.isActive,true);assert.equal(await button.isDisabled(),false)
   await button.click();await dialog.getByRole('button',{name:'保留不變',exact:true}).click();assert.equal(writes().length,0)
   console.log(`PASS list ${kind} ${width}: safe focus, Escape/cancel, plain text and locked controls`)

   await button.click();await dialog.waitFor();await navigate('/orders');await dialog.waitFor({state:'hidden'});assert.equal(writes().length,0);assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
   await p.goto(url);await idle();await button.click();await dialog.waitFor()
   await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()});await p.waitForURL('**/login?**');await dialog.waitFor({state:'hidden'});assert.equal(writes().length,0)
   await p.evaluate(s=>localStorage.setItem('catshop_session',JSON.stringify(s)),{...session,epoch:'list-confirmation-2'});await p.goto(url);await idle()
   console.log(`PASS list ${kind} ${width}: route/logout cancel stale approvals without writes`)

   fail=true;await button.click();await dialog.getByRole('button',{name:approval,exact:true}).click();await p.getByRole('alert').waitFor();assert.equal(writes().length,1);assert.equal(item.isActive,true)
   const count=calls.length;await p.getByRole('button',{name:'重試載入',exact:true}).click();await button.waitFor();await idle();assert.equal(calls.slice(count).every(c=>c.method==='GET'),true)
   fail=false;slow=true;await button.click();await dialog.getByRole('button',{name:approval,exact:true}).click()
   await p.waitForFunction(()=>document.querySelector('tbody button.btn-danger').disabled)
   for(let n=0;!release&&n<100;n++)await new Promise(done=>setTimeout(done,10));assert.ok(release)
   await button.evaluate(b=>b.dispatchEvent(new MouseEvent('click',{bubbles:true})));assert.equal(writes().length,2)
   release();release=null;await p.getByText(kind==='products'?'商品已下架':'分類已停用',{exact:true}).waitFor();assert.equal(await button.isDisabled(),true)
   const last=calls.filter(c=>c.method==='GET'&&c.path===`/admin-api/admin/${kind}`).at(-1);assert.equal(last.query.page,'1');assert.equal(last.query.keyword,'測試');assert.equal(last.query.size,'20')
   console.log(`PASS list ${kind} ${width}: conflict retry, duplicate prevention, success and retained query`)

   item.isActive=true;item.status='ON_SALE';await p.goto(url);await idle();fail=true;await button.click();await dialog.getByRole('button',{name:approval,exact:true}).click()
   for(let n=0;!release&&n<100;n++)await new Promise(done=>setTimeout(done,10));assert.ok(release)
   await navigate('/orders');release();release=null;await new Promise(done=>setTimeout(done,150));assert.equal(await p.getByRole('alert').count(),0);assert.equal(await p.getByText('商品已下架',{exact:true}).count(),0)
   assert.equal(await p.evaluate(()=>document.body.style.overflow),'');assert.deepEqual(errors,[])
   console.log(`PASS list ${kind} ${width}: late failure cannot overwrite another section`)
   await ctx.close()
  }
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
