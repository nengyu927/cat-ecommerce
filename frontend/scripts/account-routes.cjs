const {chromium}=require('playwright'),assert=require('node:assert/strict')
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
async function main(){
 const {createServer}=await import('vite')
 const server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5196,strictPort:true}})
 let browser;const errors=[]
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  const base='http://127.0.0.1:5196'
  async function scenario(name,test,logged=true){
   const context=await browser.newContext({viewport:{width:390,height:844}})
   if(logged)await context.addInitScript(()=>{if(!localStorage.getItem('catshop_session'))localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER',email:'member@test.local'},epoch:'account-test',reason:''}))})
   const state={calls:[],member:{id:1,name:'會員',phone:'0912345678',role:'CUSTOMER',email:'member@test.local'},failLoad:false,failSave:false,failPassword:false,writeDelay:0}
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>url.pathname.startsWith('/api/'),async r=>{
    const path=new URL(r.request().url()).pathname,method=r.request().method(),body=method==='GET'?null:r.request().postDataJSON()
    state.calls.push({path,method,body})
    const ok=data=>r.fulfill({json:{success:true,data}})
    const bad=(message,field)=>r.fulfill({status:400,json:{success:false,error:{message},errors:[{field,reason:message}]}})
    if(path==='/api/members/me'){
     if(method==='PUT'){
      if(state.writeDelay)await delay(state.writeDelay)
      if(state.failSave)return bad('姓名資料有誤','name')
      Object.assign(state.member,body);return ok(state.member)
     }
     if(state.failLoad)return r.fulfill({status:503,json:{success:false,error:{message:'會員資料無法載入'}}})
     return ok(state.member)
    }
    if(path==='/api/members/me/password'){
     if(state.writeDelay)await delay(state.writeDelay)
     return state.failPassword?bad('目前密碼不正確','currentPassword'):ok(null)
    }
    if(path.endsWith('/addresses'))return ok([])
    if(path.endsWith('/wishlist/ids'))return ok([])
    if(path.endsWith('/cart'))return ok({items:[],totalQuantity:0,totalAmount:0})
    if(path.endsWith('/auth/logout'))return ok(null)
    return ok({content:[],totalPages:0})
   })
   const page=await context.newPage();page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message))
   try{await test(page,state,context);console.log('PASS '+name)}finally{await context.close()}
  }
  await scenario('Overview has separate profile/password entries and responsive cards',async(p,s)=>{
   await p.goto(base+'/account');await p.getByRole('link',{name:/收件地址.*管理地址/}).waitFor()
   assert.equal(await p.getByLabel('姓名',{exact:true}).count(),0);assert.equal(await p.getByLabel('目前密碼',{exact:true}).count(),0)
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.getByRole('link',{name:/個人資料.*編輯資料/}).click();await p.waitForURL('**/account/profile');await p.getByLabel('姓名',{exact:true}).waitFor()
  })
  await scenario('Direct profile reload saves safe DTO and synchronizes local member',async(p,s)=>{
   await p.goto(base+'/account/profile');await p.getByLabel('姓名',{exact:true}).waitFor();await p.reload()
   await p.getByLabel('姓名',{exact:true}).fill('新姓名');await p.getByLabel('電話',{exact:true}).fill('0987654321')
   await p.getByRole('button',{name:'儲存',exact:true}).click();await p.getByText('個人資料已更新',{exact:true}).waitFor()
   assert.deepEqual(s.calls.find(c=>c.method==='PUT').body,{name:'新姓名',phone:'0987654321'})
   assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('catshop_session')).member.name),'新姓名')
   assert.equal(s.calls.some(c=>c.path.endsWith('/addresses')),false)
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.getByRole('link',{name:'返回會員中心',exact:true}).click();await p.waitForURL('**/account')
  })
  await scenario('Profile server field errors retain draft and reload requires confirmation',async(p,s)=>{
   s.failSave=true;await p.goto(base+'/account/profile');await p.getByLabel('姓名',{exact:true}).fill('保留草稿')
   await p.getByRole('button',{name:'儲存',exact:true}).click();await p.locator('#account-name-error').waitFor()
   assert.equal(await p.getByLabel('姓名',{exact:true}).getAttribute('aria-invalid'),'true')
   assert.equal(await p.getByLabel('姓名',{exact:true}).inputValue(),'保留草稿')
await p.getByRole('button',{name:'重新載入資料',exact:true}).click();await p.getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(await p.getByLabel('姓名',{exact:true}).inputValue(),'保留草稿')
   await p.getByRole('button',{name:'重新載入資料',exact:true}).click();await p.getByRole('button',{name:'放棄修改並重新載入',exact:true}).click()
   await p.waitForFunction(()=>document.querySelector('#account-name')?.value==='會員')
  })
  await scenario('Dirty profile leave can be cancelled or confirmed without writes',async(p,s)=>{
   await p.goto(base+'/account/profile');await p.getByLabel('姓名',{exact:true}).fill('尚未儲存')
   await p.getByRole('link',{name:'返回會員中心',exact:true}).click();await p.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(new URL(p.url()).pathname,'/account/profile')
   await p.getByRole('link',{name:'返回會員中心',exact:true}).click();await p.getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/account')
   assert.equal(s.calls.some(c=>c.method==='PUT'),false)
  })
  await scenario('Password failure retains fields and success signs out both tabs',async(p,s,context)=>{
   s.failPassword=true;await p.goto(base+'/account/password')
   const second=await context.newPage();await second.goto(base+'/account/profile');await second.getByLabel('姓名',{exact:true}).fill('其他分頁未存草稿')
   await p.getByLabel('目前密碼',{exact:true}).fill('password123');await p.getByLabel('新密碼',{exact:true}).fill('newpassword123')
   await p.getByRole('button',{name:'變更密碼',exact:true}).click();await p.locator('#password-current-error').waitFor()
   assert.equal(await p.getByLabel('目前密碼',{exact:true}).inputValue(),'password123')
   s.failPassword=false;await p.getByRole('button',{name:'變更密碼',exact:true}).click()
   await p.waitForURL('**/login?reason=password-changed');await second.waitForURL('**/login?reason=password-changed')
   assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('catshop_session')).token),'')
   assert.equal(await p.getByLabel('目前密碼',{exact:true}).count(),0)
   assert.deepEqual(s.calls.filter(c=>c.path.endsWith('/password')).at(-1).body,{currentPassword:'password123',newPassword:'newpassword123'})
  })
  await scenario('Profile saving blocks duplicate submit and navigation',async(p,s)=>{
   s.writeDelay=500;await p.goto(base+'/account');await p.getByRole('link',{name:/個人資料.*編輯資料/}).click()
   await p.getByLabel('姓名',{exact:true}).fill('新姓名');await p.getByRole('button',{name:'儲存',exact:true}).evaluate(b=>{b.click();b.click()})
   await p.getByRole('link',{name:'返回會員中心',exact:true}).click();assert.equal(new URL(p.url()).pathname,'/account/profile')
   await p.getByText('個人資料已更新',{exact:true}).waitFor();assert.equal(s.calls.filter(c=>c.method==='PUT').length,1)
  })
  await scenario('Load failure safely retries without an enabled form',async(p,s)=>{
   s.failLoad=true;await p.goto(base+'/account/password');await p.getByText('會員資料無法載入',{exact:true}).waitFor()
   assert.equal(await p.getByRole('form',{name:'密碼變更',exact:true}).count(),0)
   s.failLoad=false;await p.getByRole('button',{name:'重新載入資料',exact:true}).click();await p.getByLabel('目前密碼',{exact:true}).waitFor()
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  })
  await scenario('Late profile update does not restore a signed-out session',async(p,s,context)=>{
   s.writeDelay=500;await p.goto(base+'/account/profile');await p.getByLabel('姓名',{exact:true}).fill('舊姓名')
   await p.getByRole('button',{name:'儲存',exact:true}).click()
   const second=await context.newPage();await second.goto(base+'/account')
   await second.getByRole('button',{name:'登出',exact:true}).click();await p.waitForURL(/\/login/)
   await delay(600);assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('catshop_session')).token),'')
   assert.equal(await p.getByText('個人資料已更新',{exact:true}).count(),0)
  })
  await scenario('Unauthenticated direct password and profile routes require login',async(p,s)=>{
   await p.goto(base+'/account/password');await p.waitForURL(/\/login/)
   assert.equal(s.calls.some(c=>c.path.startsWith('/api/members')),false)
   await p.goto(base+'/account/profile');await p.waitForURL(/\/login/)
  },false)
  await scenario('Password draft can be discarded and never enters the next profile form',async(p,s)=>{
   await p.goto(base+'/account/password');await p.getByLabel('目前密碼',{exact:true}).fill('secret-draft')
   await p.getByLabel('新密碼',{exact:true}).fill('secret-new-draft')
   await p.getByRole('link',{name:'返回會員中心',exact:true}).click();await p.getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(await p.getByLabel('目前密碼',{exact:true}).inputValue(),'secret-draft')
   await p.getByRole('link',{name:'返回會員中心',exact:true}).click();await p.getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/account')
   await p.getByRole('link',{name:/個人資料.*編輯資料/}).click();await p.getByLabel('姓名',{exact:true}).waitFor()
   assert.equal(await p.getByLabel('目前密碼',{exact:true}).count(),0)
   assert.equal((await p.evaluate(()=>localStorage.getItem('catshop_session'))).includes('secret-draft'),false)
   assert.equal(s.calls.some(c=>c.method==='PUT'),false)
  })
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
