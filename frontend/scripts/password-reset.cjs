const {chromium}=require('playwright'),assert=require('node:assert/strict')
const secret='A'.repeat(43)
async function main(){
 const {createServer}=await import('vite')
 const server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5183,strictPort:true}})
 const admin=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5184,strictPort:true}})
 let browser;const errors=[];let passed=0
 try{
  await server.listen();await admin.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,test){
   const context=await browser.newContext(),state={calls:[],fail:false,delay:0}
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async r=>{
    const path=new URL(r.request().url()).pathname
    if(path.endsWith('/forgot-password') || path.endsWith('/reset-password')){
     state.calls.push({path,body:r.request().postDataJSON(),authorization:r.request().headers().authorization})
     if(state.delay)await new Promise(resolve=>setTimeout(resolve,state.delay))
     if(state.fail)return r.fulfill({status:400,json:{success:false,error:{message:'重設連結無效或已過期，請重新申請'}}})
     return r.fulfill({json:{success:true,data:path.endsWith('/forgot-password')?'若此電子郵件對應可使用的帳號，將提供重設連結；請稍後查看信件。':null}})
    }
    return r.fulfill({json:{success:true,data:{content:[],items:[],totalQuantity:0}}})
   })
   const p=await context.newPage();p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message))
   try{await test(p,state);passed++;console.log('PASS: '+name)}catch(e){console.error('Diagnostic:',p.url(),await p.getByRole('alert').allTextContents(),state.calls.map(c=>c.path),errors);throw e}finally{await context.close()}
  }
  const base='http://127.0.0.1:5183',back='http://127.0.0.1:5184'
  const fill=async p=>{await p.getByLabel('新密碼',{exact:true}).fill('new-password');await p.getByLabel('確認新密碼',{exact:true}).fill('new-password')}
  await scenario('Storefront and Admin have public recovery links and correct API boundary',async(p,s)=>{
   for(const b of [base,back]){
    await p.goto(b+'/login');await p.getByRole('link',{name:'忘記密碼？'}).click()
    await p.getByRole('heading',{name:'忘記密碼',exact:true}).waitFor()
    await p.getByLabel('電子郵件',{exact:true}).fill('member@example.com');await p.getByRole('button',{name:'申請重設連結'}).click()
    await p.getByRole('status').waitFor();assert.equal(s.calls.at(-1).path,b===base?'/api/auth/forgot-password':'/admin-api/auth/forgot-password')
    assert.equal(s.calls.at(-1).body.surface,b===base?'storefront':'admin')
   }
  })
  await scenario('Mismatch is local, fragment stripped, one reset clears session without auto-login',async(p,s)=>{
   await p.goto(base+'/reset-password#token='+secret);await p.waitForURL(base+'/reset-password');await fill(p)
   await p.getByLabel('確認新密碼',{exact:true}).fill('different-password');await p.getByRole('button',{name:'重設密碼',exact:true}).click()
   await p.getByRole('alert').waitFor();assert.equal(s.calls.length,0)
   await p.getByLabel('確認新密碼',{exact:true}).fill('new-password');await p.getByRole('button',{name:'重設密碼',exact:true}).click();await p.getByRole('status').waitFor()
   assert.equal(s.calls.length,1);assert.equal(s.calls[0].body.token,secret);assert.equal(s.calls[0].authorization,undefined)
   assert.equal(await p.evaluate(()=>localStorage.getItem('catshop_token')),null)
   assert.ok(!(await p.evaluate(()=>JSON.stringify(localStorage))).includes(secret))
   await p.getByRole('link',{name:'返回登入'}).click();await p.waitForURL(base+'/login')
  })
  await scenario('Missing link disables reset; refreshing stripped URL does not retain secret',async(p,s)=>{
   await p.goto(base+'/reset-password');assert.ok(await p.getByRole('button',{name:'重設密碼',exact:true}).isDisabled())
   await p.goto(base+'/reset-password#token='+secret);await p.waitForURL(base+'/reset-password');await p.reload()
   assert.ok(await p.getByRole('button',{name:'重設密碼',exact:true}).isDisabled());assert.equal(s.calls.length,0)
  })
  await scenario('Expired/reused link fails visibly and recovery route works without stale state',async(p,s)=>{
   s.fail=true;await p.goto(base+'/reset-password#token='+secret);await fill(p);await p.getByRole('button',{name:'重設密碼',exact:true}).click()
   await p.getByRole('alert').waitFor();assert.match(await p.getByRole('alert').textContent(),/過期/)
   await p.getByRole('link',{name:'重新申請連結'}).click();await p.getByRole('heading',{name:'忘記密碼',exact:true}).waitFor()
   assert.equal(await p.getByRole('alert').count(),0);s.fail=false
   await p.getByLabel('電子郵件',{exact:true}).fill('member@example.com');await p.getByRole('button',{name:'申請重設連結'}).click();await p.getByRole('status').waitFor()
  })
  await scenario('Late success cannot clear a newer login and mobile page fits',async(p,s)=>{
   s.delay=450;await p.setViewportSize({width:390,height:844});await p.goto(base+'/reset-password#token='+secret);await fill(p)
   await p.getByRole('button',{name:'重設密碼',exact:true}).click()
   await p.evaluate(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'new-login',member:{id:2,role:'CUSTOMER'},epoch:'new-epoch'})))
   await p.getByRole('status').waitFor();assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('catshop_session')).token),'new-login')
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  })
  assert.equal(passed,5);assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close();await admin.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
