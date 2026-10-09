const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5215,strictPort:true}})
 await server.listen();let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const width of [1440,390])for(const kind of ['profile','password','address']){
   const ctx=await browser.newContext({viewport:{width,height:850}}),writes=[]
   await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'原會員',role:'CUSTOMER'},epoch:'unsaved'})))
   const member={id:1,name:'原會員',phone:'0912345678',email:'demo@example.test',role:'CUSTOMER'}
   await ctx.route(url=>url.pathname.startsWith('/api/'),r=>{
    const path=new URL(r.request().url()).pathname
    if(r.request().method()!=='GET')writes.push(path)
    let data=[]
    if(path==='/api/members/me')data=member
    if(/^\/api\/members\/me\/addresses\/[12]$/.test(path))data={id:Number(path.split('/').pop()),receiverName:path.endsWith('/1')?'原收件人':'第二收件人',phone:'0912345678',city:'台北市',line1:'原地址',isDefault:true}
    if(path==='/api/cart')data={items:[],totalQuantity:0,totalAmount:0}
    return r.fulfill({json:{success:true,data}})
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   const target=kind==='address'?'/account/addresses/1/edit':'/account/'+kind
   await p.goto('http://127.0.0.1:5215'+target)
   const field=p.getByLabel(kind==='address'?'收件人':kind==='password'?'目前密碼':'姓名',{exact:true})
   await field.waitFor();await field.fill(kind==='password'?'secret-draft':'草稿')
   const reload=p.getByRole('button',{name:kind==='address'?'重新載入地址':'重新載入資料',exact:true})
   await reload.click();const dialog=p.getByRole('dialog')
   await dialog.waitFor();assert.equal(await p.evaluate(()=>document.activeElement.textContent),'繼續編輯')
   assert.equal(await field.isDisabled(),true)
   assert.equal((await dialog.innerText()).includes('secret-draft'),false)
   if(process.env.PREVIEW_SCREENSHOT_DIR){
    assert.ok(path.isAbsolute(process.env.PREVIEW_SCREENSHOT_DIR))
    await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))})
    await p.screenshot({path:path.join(process.env.PREVIEW_SCREENSHOT_DIR,`unsaved-${kind}-${width}.png`)})
   }
   await p.locator('.page form').evaluate(f=>f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})))
   assert.deepEqual(writes,[])
   await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'})
   assert.equal(await field.inputValue(),kind==='password'?'secret-draft':'草稿')
   await reload.click();await dialog.getByRole('button',{name:'放棄修改並重新載入',exact:true}).click()
   await p.waitForFunction(()=>!!document.querySelector('.page form input:not(:disabled)'))
   assert.equal(await field.inputValue(),kind==='address'?'原收件人':kind==='password'?'':'原會員')
   await field.fill(kind==='password'?'secret-draft':'草稿')
   const back=p.getByRole('link',{name:kind==='address'?'返回地址列表':'返回會員中心',exact:true})
   await back.click();await dialog.getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(new URL(p.url()).pathname,target)
   assert.equal(await field.inputValue(),kind==='password'?'secret-draft':'草稿')
   // beforeunload remains a browser-level safety net; no secret content in custom dialogs.
   assert.equal(await p.evaluate(()=>{const e=new Event('beforeunload',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented}),true)
   // Reused form components also protect drafts during route updates, not only unmounts.
   const alternate=kind==='address'?'/account/addresses/2/edit':kind==='profile'?'/account/password':'/account/profile'
   async function navigate(url){await p.evaluate(async url=>{const {default:router}=await import('/src/router/index.js');void router.push(url)},url)}
   await navigate(alternate);await dialog.waitFor()
   await dialog.getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(new URL(p.url()).pathname,target)
   assert.equal(await field.inputValue(),kind==='password'?'secret-draft':'草稿')
   await navigate(alternate);await dialog.getByRole('button',{name:'放棄修改並離開',exact:true}).click()
   await p.waitForURL(u=>u.pathname===alternate)
   const alternateField=p.getByLabel(kind==='address'?'收件人':kind==='profile'?'目前密碼':'姓名',{exact:true})
   await alternateField.waitFor()
   assert.equal(await alternateField.inputValue(),kind==='address'?'第二收件人':kind==='profile'?'':'原會員')
   await navigate(target);await p.waitForURL(u=>u.pathname===target)
   await field.waitFor();await field.fill(kind==='password'?'secret-draft':'草稿')
   await back.click();await dialog.waitFor()
   await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()})
   await p.waitForURL(u=>u.pathname==='/login');await dialog.waitFor({state:'hidden'})
   assert.deepEqual(writes,[])
   assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
   await ctx.close();console.log('PASS unsaved: '+kind+' '+width+' safe focus, retain/reload, route update, locked submit, password privacy, native unload and logout')
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
