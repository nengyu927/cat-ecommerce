const {chromium}=require('playwright'),assert=require('node:assert/strict')
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
async function main(){
 const {createServer}=await import('vite')
 const server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5185,strictPort:true}})
 const storefront=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5186,strictPort:true}})
 let browser,passed=0;const errors=[]
 try{
  await server.listen();await storefront.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,test,role='ADMIN'){
   const context=await browser.newContext()
   const state={calls:[],fail:false,conflict:false,delay:0,audits:[],member:{id:2,email:'customer@test.local',name:'測試會員',phone:'0912345678',role:'CUSTOMER',status:'ACTIVE',statusVersion:0,createdAt:'2026-10-08T12:00:00'}}
   await context.addInitScript(role=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Admin',role},epoch:'admin-members',reason:''})),role)
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async r=>{
    const url=new URL(r.request().url()),path=url.pathname,params=Object.fromEntries(url.searchParams),method=r.request().method()
    state.calls.push({path,params,method,body:method==='PATCH'?r.request().postDataJSON():null})
    const ok=data=>r.fulfill({json:{success:true,data}})
    if(path.endsWith('/members/2/status')){
     const payload=r.request().postDataJSON()
     if(state.delay)await delay(state.delay)
     if(state.conflict){state.member.status='DISABLED';state.member.statusVersion=1;return r.fulfill({status:409,json:{success:false,error:{message:'會員狀態已變更'}}})}
     state.audits.unshift({id:state.audits.length+1,memberId:2,actorMemberId:1,fromStatus:state.member.status,toStatus:payload.status,statusVersion:payload.version+1,reason:payload.reason,createdAt:'2026-10-08T12:00:00'})
     state.member.status=payload.status;state.member.statusVersion=payload.version+1;return ok({...state.member})
    }
    if(/\/members\/\d+\/history$/.test(path)){
     if(state.historyFail)return r.fulfill({status:503,json:{success:false,error:{message:'歷程暫時無法載入'}}})
     return ok({content:state.audits,page:Number(params.page||0),totalElements:state.audits.length,totalPages:state.historyPages|| (state.audits.length?1:0),last:!state.historyPages||Number(params.page||0)+1>=state.historyPages})
    }
    if(/\/admin\/members\/\d+$/.test(path)){
     const id=path.split('/').pop()
     if(id==='1')return ok({id:1,name:'Admin',email:'admin@test.local',role:'ADMIN',status:'ACTIVE',statusVersion:0})
     if(id!=='2')return r.fulfill({status:404,json:{success:false,error:{message:'找不到會員'}}})
     if(state.fail)return r.fulfill({status:503,json:{success:false,error:{message:'會員資料暫時無法載入'}}})
     const snapshot={...state.member}
     if(state.detailDelay)await delay(state.detailDelay)
     return ok(snapshot)
    }
    if(path.endsWith('/admin/members')){
     if(state.fail)return r.fulfill({status:503,json:{success:false,error:{message:'會員列表暫時無法載入'}}})
     if(params.keyword==='slow')await delay(450)
     return ok({content:[{id:1,name:'Admin',email:'admin@test.local',role:'ADMIN',status:'ACTIVE',statusVersion:0,createdAt:'2026-10-08T12:00:00'},{...state.member,name:params.keyword==='slow'?'過時會員':state.member.name}],page:Number(params.page||0),totalElements:21,totalPages:2,last:params.page==='1'})
    }
    return ok({content:[],items:[],totalQuantity:0})
   })
   const p=await context.newPage();p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message))
   try{await test(p,state);passed++;console.log('PASS: '+name)}catch(e){console.error('Diagnostic:',p.url(),await p.getByRole('alert').allTextContents(),errors);throw e}finally{await context.close()}
  }
  const base='http://127.0.0.1:5185',ready=p=>p.getByText('符合條件共 21 位會員',{exact:true}).waitFor()
  await scenario('Applied filters paging reload clear and refresh preserve URL',async(p,s)=>{
   await p.goto(base+'/members');await ready(p)
   await p.getByLabel('會員搜尋',{exact:true}).fill('test');await p.getByLabel('會員狀態').selectOption('ACTIVE');await p.getByLabel('會員角色').selectOption('CUSTOMER')
   await p.getByRole('button',{name:'搜尋',exact:true}).click();await ready(p)
   await p.getByRole('button',{name:'下一頁',exact:true}).click();await p.waitForURL(/page=1/);await p.reload();await ready(p)
   assert.equal(s.calls.at(-1).params.role,'CUSTOMER');assert.equal(s.calls.at(-1).params.status,'ACTIVE')
   await p.getByRole('button',{name:'清除條件'}).click();await p.waitForURL(u=>!u.searchParams.has('keyword'));await ready(p)
   const n=s.calls.length;await p.getByRole('button',{name:'搜尋',exact:true}).click();await ready(p);assert.ok(s.calls.length>n)
  })
  await scenario('Explicit confirmation reason version and audit; cancellation makes no writes',async(p,s)=>{
   await p.goto(base+'/members');await ready(p)
   assert.equal(await p.getByRole('button',{name:'停用會員 #1',exact:true}).count(),0)
   await p.getByRole('link',{name:'會員詳情 #2',exact:true}).click()
   await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await p.getByRole('dialog').waitFor()
   await p.getByRole('button',{name:'取消',exact:true}).click();assert.equal(s.calls.filter(c=>c.method==='PATCH').length,0)
   await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await p.getByLabel('操作原因').fill('測試停用')
   await p.getByRole('button',{name:'確認變更'}).click();await p.getByText('會員狀態已更新；恢復帳號後仍須重新登入。',{exact:true}).waitFor()
   const writes=s.calls.filter(c=>c.method==='PATCH');assert.equal(writes.length,1);assert.deepEqual(writes[0].body,{status:'DISABLED',version:0,reason:'測試停用'})
   await p.getByRole('link',{name:'操作紀錄',exact:true}).click();await p.getByText('測試停用',{exact:true}).waitFor()
   await p.getByRole('link',{name:'會員詳情',exact:true}).click()
   await p.getByRole('button',{name:'恢復會員 #2',exact:true}).click();await p.getByLabel('操作原因').fill('恢復測試');await p.getByRole('button',{name:'確認變更'}).click()
   await p.getByText('會員狀態已更新；恢復帳號後仍須重新登入。',{exact:true}).waitFor()
   await p.getByRole('link',{name:'操作紀錄',exact:true}).click()
   await p.getByText('恢復測試',{exact:true}).waitFor();assert.equal(s.calls.filter(c=>c.method==='PATCH').at(-1).body.version,1)
  })
  await scenario('Conflict is shown and never auto-retried; reload uses new version',async(p,s)=>{
   s.conflict=true;await p.goto(base+'/members');await ready(p)
   await p.getByRole('link',{name:'會員詳情 #2',exact:true}).click()
   await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await p.getByLabel('操作原因').fill('test');await p.getByRole('button',{name:'確認變更'}).click()
   await p.getByRole('alert').waitFor();assert.match(await p.getByRole('alert').textContent(),/重新載入/);assert.equal(s.calls.filter(c=>c.method==='PATCH').length,1)
   await p.getByRole('button',{name:'取消',exact:true}).click();await p.getByRole('button',{name:'放棄原因並關閉',exact:true}).click();s.conflict=false
   await p.getByRole('button',{name:'重新載入會員',exact:true}).click()
   await p.getByRole('button',{name:'恢復會員 #2',exact:true}).click();await p.getByLabel('操作原因').fill('Retry');await p.getByRole('button',{name:'確認變更'}).click();await p.getByText('會員狀態已更新；恢復帳號後仍須重新登入。',{exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.method==='PATCH').at(-1).body.version,1)
  })
  await scenario('Load retry slow response and invalid URL filters',async(p,s)=>{
   s.fail=true;await p.goto(base+'/members');await p.getByRole('button',{name:'重試載入'}).waitFor();s.fail=false;await p.getByRole('button',{name:'重試載入'}).click();await ready(p)
   await p.getByLabel('會員搜尋',{exact:true}).fill('slow');await p.getByRole('button',{name:'搜尋',exact:true}).click();await p.waitForURL(/keyword=slow/)
   await p.getByLabel('會員搜尋',{exact:true}).fill('fast');await p.getByRole('button',{name:'搜尋',exact:true}).click();await ready(p);await delay(600)
   assert.equal(await p.getByText('過時會員',{exact:true}).count(),0)
   await p.goto(base+'/members?role=OWNER');await p.getByRole('alert').waitFor();assert.match(await p.getByRole('alert').textContent(),/角色/)
  })
  await scenario('Mobile table dialog and history remain contained',async p=>{
   await p.setViewportSize({width:390,height:844});await p.goto(base+'/members');await ready(p)
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.getByRole('link',{name:'會員詳情 #2',exact:true}).click()
   await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await p.getByRole('dialog').waitFor()
   assert.ok(await p.evaluate(()=>{const r=document.querySelector('dialog').getBoundingClientRect();return r.left>=0 && r.right<=innerWidth}))
   await p.getByRole('button',{name:'取消',exact:true}).click();await p.getByRole('link',{name:'操作紀錄',exact:true}).click();await p.getByText('尚無狀態變更紀錄；不補建歷史。',{exact:true}).waitFor()
  })
  await scenario('Customer denied and storefront never registers Admin member route',async p=>{
   await p.goto(base+'/members');await p.waitForURL(/\/login/);assert.equal(await p.getByRole('heading',{name:'會員管理',exact:true}).count(),0)
   await p.goto('http://127.0.0.1:5186/admin/members');assert.equal(await p.getByRole('heading',{name:'會員管理',exact:true}).count(),0)
  },'CUSTOMER')
  await scenario('Late mutation result cannot appear after login epoch changes',async(p,s)=>{
   s.delay=450;await p.goto(base+'/members');await ready(p)
   await p.getByRole('link',{name:'會員詳情 #2',exact:true}).click()
   await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await p.getByLabel('操作原因').fill('test');await p.getByRole('button',{name:'確認變更'}).click()
   await p.evaluate(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'new',member:{id:3,role:'CUSTOMER'},epoch:'new-epoch'})))
   await p.waitForURL(/\/login/);assert.equal(await p.getByRole('status').count(),0);assert.equal(s.calls.filter(c=>c.method==='PATCH').length,1)
  })
  await scenario('Direct detail reload and list/history navigation retain applied filters',async(p,s)=>{
   await p.setViewportSize({width:390,height:844})
   const query='?keyword=test&status=ACTIVE&role=CUSTOMER&page=1'
   await p.goto(base+'/members/2'+query);await p.getByRole('region',{name:'會員基本資料'}).waitFor()
   assert.equal(s.calls.some(c=>c.path.endsWith('/admin/members')),false)
   await p.reload();await p.getByRole('region',{name:'會員基本資料'}).waitFor()
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.getByRole('link',{name:'操作紀錄',exact:true}).click();await p.getByText('尚無狀態變更紀錄；不補建歷史。',{exact:true}).waitFor()
   await p.getByRole('link',{name:'返回會員列表',exact:true}).click();await ready(p)
   assert.equal(new URL(p.url()).searchParams.get('page'),'1')
   assert.equal(s.calls.filter(c=>c.path.endsWith('/admin/members')).at(-1).params.role,'CUSTOMER')
   await p.goBack();await p.getByRole('heading',{name:'會員操作紀錄 #2',exact:true}).waitFor()
   assert.equal(s.calls.some(c=>c.method==='PATCH'),false)
  })
  await scenario('History pagination reload and return keep separate list page',async(p,s)=>{
   s.historyPages=2;s.audits=[{id:1,reason:'歷程測試',fromStatus:'ACTIVE',toStatus:'DISABLED',actorMemberId:1,statusVersion:1}]
   await p.goto(base+'/members/2/history?page=1');await p.getByText('歷程測試',{exact:true}).waitFor()
   await p.getByRole('button',{name:'紀錄下一頁',exact:true}).click();await p.waitForURL(/historyPage=1/)
   await p.reload();await p.getByText('歷程測試',{exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.path.endsWith('/history')).at(-1).params.page,'1')
   await p.getByRole('link',{name:'返回會員列表',exact:true}).click();await ready(p)
   assert.equal(new URL(p.url()).searchParams.get('page'),'1');assert.equal(new URL(p.url()).searchParams.has('historyPage'),false)
  })
  await scenario('Old query and legacy direct URLs redirect without mutations',async(p,s)=>{
   await p.goto(base+'/members?memberId=2&historyPage=1&keyword=test&page=1');await p.waitForURL(/\/members\/2\/history/)
   await p.getByText('尚無狀態變更紀錄；不補建歷史。',{exact:true}).waitFor()
   assert.equal(new URL(p.url()).searchParams.has('memberId'),false)
   await p.goto(base+'/admin/members/2?keyword=test&page=1');await p.waitForURL(/\/members\/2\?/)
   await p.getByRole('region',{name:'會員基本資料'}).waitFor();assert.equal(new URL(p.url()).pathname,'/members/2')
   assert.equal(s.calls.some(c=>c.method==='PATCH'),false)
  })
  await scenario('Missing detail failed history and invalid IDs safely retry',async(p,s)=>{
   await p.goto(base+'/members/99');await p.getByText('找不到會員',{exact:true}).waitFor()
   assert.equal(await p.getByRole('region',{name:'會員基本資料'}).count(),0)
   s.historyFail=true;await p.goto(base+'/members/2/history');await p.getByText('歷程暫時無法載入',{exact:true}).waitFor()
   s.historyFail=false;await p.getByRole('button',{name:'重新載入會員',exact:true}).click();await p.getByText('尚無狀態變更紀錄；不補建歷史。',{exact:true}).waitFor()
   const count=s.calls.length;await p.goto(base+'/members/9223372036854775808');await p.getByRole('alert').waitFor();assert.equal(s.calls.length,count)
   await p.goto(base+'/members/2/history?historyPage=-1');await p.getByRole('alert').waitFor();assert.equal(s.calls.length,count)
  })
  await scenario('Dirty reason navigation cancelled or confirmed; saving prevents repeat or leave',async(p,s)=>{
   await p.goto(base+'/members');await ready(p);await p.getByRole('link',{name:'會員詳情 #2',exact:true}).click()
   await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await p.getByLabel('操作原因').fill('未完成原因')
   await p.evaluate(()=>history.back());await p.getByRole('button',{name:'繼續編輯',exact:true}).click();await p.waitForURL(base+'/members/2')
   assert.equal(await p.getByLabel('操作原因').inputValue(),'未完成原因')
   await p.evaluate(()=>history.back());await p.getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL(base+'/members');await ready(p)
   assert.equal(s.calls.some(c=>c.method==='PATCH'),false)
   await p.getByRole('link',{name:'會員詳情 #2',exact:true}).click();await p.getByRole('button',{name:'停用會員 #2',exact:true}).click()
   s.delay=500;await p.getByLabel('操作原因').fill('處理中');await p.getByRole('button',{name:'確認變更'}).click()
   assert.equal(await p.getByRole('button',{name:'處理中…',exact:true}).isDisabled(),true)
   await p.goBack();await p.waitForURL(base+'/members/2')
   await p.getByText('會員狀態已更新；恢復帳號後仍須重新登入。',{exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.method==='PATCH').length,1)
  })
  await scenario('Admin protected detail has no state mutation action',async(p,s)=>{
   await p.goto(base+'/members/1');await p.getByText('管理員帳號受保護',{exact:true}).waitFor()
   assert.equal(await p.getByRole('button',{name:'停用會員 #1',exact:true}).count(),0)
   assert.equal(s.calls.some(c=>c.method==='PATCH'),false)
  })
  await scenario('Slow detail cannot overwrite next route and Customer direct routes denied',async(p,s)=>{
   s.detailDelay=450;await p.goto(base+'/members/2')
   await p.getByRole('link',{name:'返回會員列表',exact:true}).click();await ready(p)
   await p.getByRole('link',{name:'會員詳情 #1',exact:true}).click();await p.getByText('管理員帳號受保護',{exact:true}).waitFor();await delay(600)
   assert.equal(await p.getByRole('button',{name:'停用會員 #2',exact:true}).count(),0)
  })
  await scenario('Customer direct member detail and history make no member API request',async(p,s)=>{
   await p.goto(base+'/members/2/history');await p.waitForURL(/\/login/)
   assert.equal(s.calls.some(c=>c.path.includes('/admin/members')),false)
  },'CUSTOMER')
  for(const width of [1440,390]){
   await scenario(`Reason draft ${width}: safe focus, validation, Escape, discard and reload`,async(p,s)=>{
    await p.setViewportSize({width,height:900});await p.goto(base+'/members/2?page=1')
    const editor=p.locator('.member-dialog'),confirmation=p.locator('.confirmation-dialog'),field=p.getByLabel('操作原因',{exact:true})
    await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await editor.waitFor({state:'visible'});assert.equal(await p.evaluate(()=>document.activeElement.textContent),'取消')
    await field.fill('   ');await p.getByRole('button',{name:'確認變更',exact:true}).click();await p.getByText('請填寫 1 至 200 字的操作原因',{exact:true}).waitFor();assert.equal(s.calls.some(c=>c.method==='PATCH'),false)
    await field.fill('保留操作原因');await p.getByRole('button',{name:'取消',exact:true}).click();await confirmation.waitFor({state:'visible'});assert.equal(await field.isDisabled(),true);assert.equal(await p.evaluate(()=>document.activeElement.textContent),'繼續編輯')
    await editor.locator('form').evaluate(f=>f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));assert.equal(s.calls.some(c=>c.method==='PATCH'),false)
    if(process.env.PREVIEW_SCREENSHOT_DIR){await confirmation.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))});await p.screenshot({path:require('node:path').join(process.env.PREVIEW_SCREENSHOT_DIR,`member-reason-confirmation-${width}.png`)})}
    await p.keyboard.press('Escape');await confirmation.waitFor({state:'hidden'});assert.equal(await field.inputValue(),'保留操作原因');assert.equal(await p.evaluate(()=>getComputedStyle(document.body).overflow),'hidden')
    await p.keyboard.press('Escape');await confirmation.waitFor({state:'visible'});await confirmation.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(await field.inputValue(),'保留操作原因')
    await p.evaluate(()=>document.querySelector('button[data-cancel-action]').click());await confirmation.getByRole('button',{name:'放棄原因並關閉',exact:true}).click();await editor.waitFor({state:'hidden'});assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
    await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await field.fill('重新載入原因')
    await p.evaluate(()=>Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='重新載入會員').click());await confirmation.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(await field.inputValue(),'重新載入原因')
    await p.evaluate(()=>Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='重新載入會員').click());await confirmation.getByRole('button',{name:'放棄修改並重新載入',exact:true}).click();await editor.waitFor({state:'hidden'});await p.getByRole('region',{name:'會員基本資料'}).waitFor();assert.equal(s.calls.some(c=>c.method==='PATCH'),false)
   })
   await scenario(`Reason navigation ${width}: route reuse, native unload and session isolation`,async(p,s)=>{
    await p.setViewportSize({width,height:900});await p.goto(base+'/members/2?page=1&keyword=test')
    const editor=p.locator('.member-dialog'),confirmation=p.locator('.confirmation-dialog'),field=p.getByLabel('操作原因',{exact:true})
    await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await field.fill('跨頁草稿')
    assert.equal(await p.evaluate(()=>{const e=new Event('beforeunload',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented}),true)
    async function navigate(){await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');void router.push('/members/2/history?page=1&keyword=test')})}
    await navigate();await confirmation.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(new URL(p.url()).pathname,'/members/2');assert.equal(await field.inputValue(),'跨頁草稿')
    await navigate();await confirmation.getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/members/2/history?**');await editor.waitFor({state:'hidden'});assert.equal(new URL(p.url()).searchParams.get('page'),'1');assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
    await p.getByRole('link',{name:'會員詳情',exact:true}).click();await p.getByRole('button',{name:'停用會員 #2',exact:true}).click();await field.fill('登出草稿');await p.getByRole('button',{name:'取消',exact:true}).click();await confirmation.waitFor({state:'visible'})
    await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()});await p.waitForURL('**/login?**');assert.equal(await p.evaluate(()=>document.body.style.overflow),'');assert.equal(s.calls.some(c=>c.method==='PATCH'),false)
   })
  }
  assert.equal(passed,19);assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close();await storefront.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
