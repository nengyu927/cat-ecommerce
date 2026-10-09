const { chromium } = require('playwright'), assert = require('node:assert/strict')
async function main() {
 const { createServer } = await import('vite')
 const admin = await createServer({mode:'admin',server:{host:'127.0.0.1',port:5187,strictPort:true}})
 const front = await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5188,strictPort:true}})
 let browser; const errors=[]
 try {
  await admin.listen(); await front.listen(); browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,role,test,width=390) {
   const context=await browser.newContext({viewport:{width,height:844}})
   await context.addInitScript(role=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Test',role},epoch:'shipments',reason:''})),role)
   const state={calls:[],record:null,posts:[],fail:false,slow:false,detailFailure:false,order:{id:1,orderNo:'SHIP-1',memberName:'會員',memberEmail:'test@example.com',status:'PENDING',paymentMethod:'COD',items:[],createdAt:'2026-10-08T12:00:00',totalAmount:100,shippingFee:0}}
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>/^\/(api|admin-api)\//.test(url.pathname),async r=>{
    const path=new URL(r.request().url()).pathname, method=r.request().method()
    state.calls.push({path,method,params:Object.fromEntries(new URL(r.request().url()).searchParams)})
    const ok=data=>r.fulfill({json:{success:true,data}})
    if(path.endsWith('/status')){state.order.status=r.request().postDataJSON().status;return ok(state.order)}
    if(path.endsWith('/shipment')) {
     if(method==='POST') {
      state.posts.push(r.request().postDataJSON())
      if(state.fail) return r.fulfill({status:409,json:{success:false,error:{message:'訂單狀態已變更'}}})
      state.record={...r.request().postDataJSON(),shippedAt:'2026-10-08T12:00:00'};state.order.status='SHIPPED'
     }
     if(state.slow && path.includes('/1/')) await new Promise(resolve=>setTimeout(resolve,400))
     return ok(state.record)
    }
    if(/\/orders\/\d+$/.test(path)) {
     if(state.detailFailure)return r.fulfill({status:404,json:{success:false,error:{message:'找不到訂單'}}})
     const id=Number(path.split('/').pop())
     if(state.slow && id===1) await new Promise(resolve=>setTimeout(resolve,400))
     return ok({...state.order,id,orderNo:`SHIP-${id}`})
    }
    if(path.endsWith('/admin/orders')) return ok({content:[state.order],totalPages:1})
    if(path.endsWith('/wishlist/ids')) return ok([])
    return ok({items:[],content:[],totalQuantity:0})
   })
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept())
   try { await test(page,state); console.log('PASS '+name) } finally { await context.close() }
  }
  await scenario('Admin manual shipment and immutable display','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5187/orders');await p.getByRole('link',{name:'詳情',exact:true}).click();await p.getByRole('link',{name:'登錄出貨',exact:true}).click()
   await p.getByLabel('物流公司',{exact:true}).fill(' 測試宅配 ');await p.getByLabel('物流單號',{exact:true}).fill(' TEST123 ')
   await p.getByRole('button',{name:'確認出貨',exact:true}).click()
   await p.getByRole('dialog').getByRole('button',{name:'確認登錄出貨',exact:true}).click()
   await p.getByText('已登錄出貨',{exact:true}).waitFor()
   assert.deepEqual(s.posts,[{carrier:'測試宅配',trackingNumber:'TEST123'}])
   assert.equal(await p.getByRole('form',{name:'出貨登錄'}).count(),0)
   await p.getByRole('region',{name:'出貨紀錄'}).getByText(/TEST123/).waitFor()
  })
  await scenario('Admin conflict stays visible without pretending shipment succeeded','ADMIN',async(p,s)=>{
   s.fail=true;await p.goto('http://127.0.0.1:5187/orders');await p.getByRole('link',{name:'詳情',exact:true}).click();await p.getByRole('link',{name:'登錄出貨',exact:true}).click()
   await p.getByLabel('物流公司',{exact:true}).fill('宅配');await p.getByLabel('物流單號',{exact:true}).fill('TEST')
   await p.getByRole('button',{name:'確認出貨',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'確認登錄出貨',exact:true}).click();await p.getByRole('alert').getByText('訂單狀態已變更',{exact:true}).waitFor()
   assert.equal(s.posts.length,1);assert.equal(s.record,null)
   assert.equal(await p.getByLabel('物流單號',{exact:true}).inputValue(),'TEST')
   await p.getByRole('button',{name:'重新載入',exact:true}).click()
   await p.getByRole('dialog').getByRole('button',{name:'放棄修改並重新載入',exact:true}).click()
   await p.getByRole('heading',{name:'SHIP-1',exact:true}).waitFor()
   assert.equal(await p.getByLabel('物流單號',{exact:true}).inputValue(),'')
  })
  await scenario('Member shipment display and mobile layout','CUSTOMER',async(p,s)=>{
   s.order.status='SHIPPED';s.record={carrier:'宅配',trackingNumber:'TEST123',shippedAt:'2026-10-08T12:00:00'}
   await p.goto('http://127.0.0.1:5188/orders/1');await p.getByRole('region',{name:'出貨紀錄'}).getByText('TEST123',{exact:true}).waitFor()
   assert.equal(await p.getByRole('button',{name:'取消訂單',exact:true}).count(),0)
   await p.getByText(/非物流即時追蹤/).waitFor()
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
  })
  await scenario('Member stale detail cannot overwrite a new route','CUSTOMER',async(p,s)=>{
   s.slow=true;await p.goto('http://127.0.0.1:5188/orders/1');await p.waitForTimeout(70)
   await p.evaluate(()=>{history.pushState({},'', '/orders/2');dispatchEvent(new PopStateEvent('popstate'))})
   await p.getByRole('heading',{name:'訂單 SHIP-2',exact:true}).waitFor();await p.waitForTimeout(500)
   assert.equal(await p.getByRole('heading',{name:'訂單 SHIP-1',exact:true}).count(),0)
  })
  await scenario('Admin direct URL, reload and no writes on navigation','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5187/orders/1');await p.getByRole('heading',{name:'SHIP-1',exact:true}).waitFor()
   assert.equal(await p.getByRole('form',{name:'出貨登錄'}).count(),0)
   await p.reload();await p.getByRole('heading',{name:'SHIP-1',exact:true}).waitFor()
   await p.getByRole('link',{name:'登錄出貨',exact:true}).click();await p.getByLabel('物流公司',{exact:true}).waitFor()
   await p.reload();await p.getByLabel('物流公司',{exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.method!=='GET').length,0)
   const overflow=await p.evaluate(()=>Array.from(document.querySelectorAll('*')).filter(el=>el.getBoundingClientRect().right>innerWidth).slice(0,15).map(el=>({tag:el.tagName,cls:el.className,width:el.getBoundingClientRect().width,right:el.getBoundingClientRect().right})))
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,JSON.stringify(overflow))
  })
  await scenario('Admin slow detail cannot overwrite the next order','ADMIN',async(p,s)=>{
   s.slow=true;const started=p.waitForRequest(r=>new URL(r.url()).pathname==='/admin-api/admin/orders/1');await p.goto('http://127.0.0.1:5187/orders/1');await started
   await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');void router.push('/orders/2')});await p.waitForURL('**/orders/2')
   await p.getByRole('heading',{name:'SHIP-2',exact:true}).waitFor();await p.waitForTimeout(500)
   assert.equal(await p.getByRole('heading',{name:'SHIP-1',exact:true}).count(),0)
  })
  await scenario('Admin list query survives detail, shipment, return and history','ADMIN',async(p,s)=>{
   const query='?status=PENDING&orderNo=SHIP&memberId=1&from=2026-10-01&to=2026-10-31&page=1'
   await p.goto('http://127.0.0.1:5187/orders'+query)
   await p.getByRole('link',{name:'詳情',exact:true}).click();await p.waitForURL('**/orders/1?**')
   await p.getByRole('link',{name:'登錄出貨',exact:true}).click()
   await p.getByRole('link',{name:'返回訂單列表',exact:true}).click()
   await p.getByRole('heading',{name:'訂單管理',exact:true}).waitFor()
   assert.equal(new URL(p.url()).searchParams.get('page'),'1')
   const last=s.calls.filter(c=>c.path.endsWith('/admin/orders')).at(-1)
   assert.equal(last.params.status,'PENDING');assert.equal(last.params.orderNo,'SHIP');assert.equal(last.params.page,'1')
   await p.goBack();await p.getByLabel('物流公司',{exact:true}).waitFor()
   await p.goForward();await p.getByRole('heading',{name:'訂單管理',exact:true}).waitFor()
  })
  await scenario('Unsaved shipment blocks cancelled navigation; confirmed leave succeeds','ADMIN',async(p,s)=>{
   p.removeAllListeners('dialog')
   await p.goto('http://127.0.0.1:5187/orders/1/shipment');await p.getByLabel('物流公司',{exact:true}).fill('未儲存物流')
   await p.getByRole('link',{name:'返回訂單詳情',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(new URL(p.url()).pathname,'/orders/1/shipment');assert.equal(await p.getByLabel('物流公司',{exact:true}).inputValue(),'未儲存物流')
   await p.getByRole('link',{name:'返回訂單詳情',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'放棄修改並離開',exact:true}).click()
   await p.waitForURL('**/orders/1');assert.equal(s.posts.length,0)
  })
  await scenario('Legacy Admin detail redirects preserve query and forbidden shipping remains readonly','ADMIN',async(p,s)=>{
   s.order.status='CANCELLED'
   await p.goto('http://127.0.0.1:5187/admin/orders/1/shipment?page=2')
   await p.getByText('目前訂單狀態不可出貨。請返回詳情查看最新狀態。',{exact:true}).waitFor()
   assert.equal(new URL(p.url()).pathname,'/orders/1/shipment');assert.equal(new URL(p.url()).searchParams.get('page'),'2')
   assert.equal(s.posts.length,0)
  })
  await scenario('Invalid ID and missing order have safe retry without old details','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5187/orders/not-a-number');await p.getByRole('alert').getByText('訂單編號格式不正確',{exact:true}).waitFor()
   assert.equal(s.calls.some(c=>c.path.includes('not-a-number')),false)
   s.detailFailure=true;await p.goto('http://127.0.0.1:5187/orders/1');await p.getByRole('alert').getByText('找不到訂單',{exact:true}).waitFor()
   assert.equal(await p.getByRole('heading',{name:'SHIP-1',exact:true}).count(),0)
   s.detailFailure=false;await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('heading',{name:'SHIP-1',exact:true}).waitFor()
  })
  await scenario('Customer cannot access Admin operation route','CUSTOMER',async(p,s)=>{
   await p.goto('http://127.0.0.1:5187/orders/1/shipment');await p.waitForURL('**/login?**')
   assert.equal(s.calls.some(c=>c.path.includes('/admin/orders/1')),false)
  })
  await scenario('Admin invalid list query is rejected without list request','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5187/orders?page=-1')
   await p.getByRole('alert').filter({hasText:'有效範圍內的整數'}).waitFor()
   assert.equal(s.calls.some(c=>c.path.endsWith('/admin/orders')),false)
   await p.getByRole('button',{name:'清除篩選',exact:true}).click();await p.getByRole('link',{name:'詳情',exact:true}).waitFor()
  })
  for(const width of [1440,390]){
   await scenario(`Shipment draft ${width}: focus, retain/reload, route reuse and logout`,'ADMIN',async(p,s)=>{
    await p.goto('http://127.0.0.1:5187/orders/1/shipment?page=1');const field=p.getByLabel('物流公司',{exact:true}),dialog=p.getByRole('dialog'),reload=p.getByRole('button',{name:'重新載入',exact:true})
    await field.fill('草稿宅配');await p.getByLabel('物流單號',{exact:true}).fill('DRAFT')
    await reload.click();await dialog.waitFor();assert.equal(await field.isDisabled(),true);assert.equal(await p.evaluate(()=>document.activeElement.textContent),'繼續編輯')
    await p.getByRole('form',{name:'出貨登錄'}).evaluate(f=>f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));assert.equal(s.posts.length,0)
    await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(await field.inputValue(),'草稿宅配')
    await reload.click();await dialog.getByRole('button',{name:'放棄修改並重新載入',exact:true}).click();await field.waitFor();assert.equal(await field.inputValue(),'')
    await field.fill('草稿宅配');assert.equal(await p.evaluate(()=>{const e=new Event('beforeunload',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented}),true)
    async function navigate(){await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');void router.push('/orders/2/shipment?page=1')})}
    await navigate();await dialog.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(new URL(p.url()).pathname,'/orders/1/shipment');assert.equal(await field.inputValue(),'草稿宅配')
    await navigate();await dialog.getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/orders/2/shipment?**');await p.getByRole('heading',{name:'SHIP-2',exact:true}).waitFor();assert.equal(await field.inputValue(),'')
    await field.fill('新草稿');await reload.click();await dialog.waitFor();await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()});await p.waitForURL('**/login?**');await dialog.waitFor({state:'hidden'});assert.equal(s.posts.length,0);assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
   },width)
   await scenario(`Shipment approval ${width}: summary, duplicate prevention, cancellation and one write`,'ADMIN',async(p,s)=>{
    await p.goto('http://127.0.0.1:5187/orders/1/shipment?page=1');await p.getByLabel('物流公司',{exact:true}).fill(' 測試宅配 ');await p.getByLabel('物流單號',{exact:true}).fill(' TRACK-1 ')
    const form=p.getByRole('form',{name:'出貨登錄'}),dialog=p.getByRole('dialog')
    await form.getByRole('button',{name:'確認出貨',exact:true}).click();await dialog.waitFor();assert.equal(await p.evaluate(()=>document.activeElement.textContent),'返回修改');await dialog.getByText(/物流單號：TRACK-1/).waitFor();assert.equal(s.posts.length,0)
    await form.evaluate(f=>{f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))});assert.equal(s.posts.length,0)
    if(process.env.PREVIEW_SCREENSHOT_DIR){await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))});await p.screenshot({path:require('node:path').join(process.env.PREVIEW_SCREENSHOT_DIR,`shipment-confirmation-${width}.png`)})}
    await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(await p.getByLabel('物流單號',{exact:true}).inputValue(),' TRACK-1 ')
    await form.getByRole('button',{name:'確認出貨',exact:true}).click();s.slow=true;await dialog.getByRole('button',{name:'確認登錄出貨',exact:true}).click()
    await form.evaluate(f=>f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));await p.getByText('已登錄出貨',{exact:true}).waitFor();await p.waitForURL('**/orders/1?**');assert.equal(new URL(p.url()).searchParams.get('page'),'1');assert.deepEqual(s.posts,[{carrier:'測試宅配',trackingNumber:'TRACK-1'}])
   },width)
   await scenario(`Order status ${width}: explicit approval and cancellation`,'ADMIN',async(p,s)=>{
    await p.goto('http://127.0.0.1:5187/orders/1');const dialog=p.getByRole('dialog'),cancel=p.getByRole('button',{name:'已取消',exact:true})
    await cancel.click();await dialog.getByText('取消後將回補庫存與優惠券名額。',{exact:true}).waitFor();assert.equal(s.calls.some(c=>c.method!=='GET'),false)
    await dialog.getByRole('button',{name:'保留原狀態',exact:true}).click();assert.equal(s.order.status,'PENDING')
    await cancel.click();await dialog.getByRole('button',{name:'確認已取消',exact:true}).click();await p.getByText('訂單狀態已更新',{exact:true}).waitFor();assert.equal(s.order.status,'CANCELLED');assert.equal(s.calls.filter(c=>c.method!=='GET').length,1)
   },width)
   await scenario(`Shipment pending approval ${width}: logout prevents stale write`,'ADMIN',async(p,s)=>{
    await p.goto('http://127.0.0.1:5187/orders/1/shipment');await p.getByLabel('物流公司',{exact:true}).fill('宅配');await p.getByLabel('物流單號',{exact:true}).fill('TEST');await p.getByRole('button',{name:'確認出貨',exact:true}).click();await p.getByRole('dialog').waitFor()
    await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()});await p.waitForURL('**/login?**');await p.getByRole('dialog').waitFor({state:'hidden'});assert.equal(s.posts.length,0);assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
   },width)
  }
  assert.deepEqual(errors,[])
 } finally { if(browser)await browser.close();await admin.close();await front.close() }
}
main().catch(e=>{console.error(e);process.exitCode=1})
