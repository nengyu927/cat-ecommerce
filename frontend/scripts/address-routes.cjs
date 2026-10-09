const {chromium}=require('playwright'),assert=require('node:assert/strict')
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms))
const seed=id=>({id,receiverName:'收件人'+id,phone:'0912345678',city:'台北市',district:'大安區',line1:'測試路'+id,line2:'',postalCode:'100',isDefault:id===1,memberId:999})
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5197,strictPort:true}})
 let browser;const errors=[]
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  const base='http://127.0.0.1:5197'
  async function scenario(name,test,logged=true){
   const context=await browser.newContext({viewport:{width:390,height:844}})
   if(logged)await context.addInitScript(()=>{if(!localStorage.getItem('catshop_session'))localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',phone:'0912345678',role:'CUSTOMER'},epoch:'address-test',reason:''}))})
   const state={calls:[],addresses:[seed(1),seed(2)],fail:false,failSave:false,failDelete:false,slow:false,writeDelay:0}
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>url.pathname.startsWith('/api/'),async r=>{
    const path=new URL(r.request().url()).pathname,method=r.request().method(),body=method==='GET'?null:r.request().postDataJSON()
    state.calls.push({path,method,body});const ok=data=>r.fulfill({json:{success:true,data}})
    if(path.includes('/members/me/addresses')){
     if(state.writeDelay&&method!=='GET')await delay(state.writeDelay)
     if(state.fail&&method==='GET')return r.fulfill({status:503,json:{success:false,error:{message:'地址無法載入'}}})
     const id=Number(path.split('/').pop()),item=state.addresses.find(a=>a.id===id)
     if(method==='GET'&&path.endsWith('/addresses'))return ok(state.addresses)
     if(method==='GET'){const copy=item?{...item}:null;if(state.slow&&id===1)await delay(500);return copy?ok(copy):r.fulfill({status:404,json:{success:false,error:{message:'找不到地址'}}})}
     if(method==='DELETE'){
      if(state.failDelete)return r.fulfill({status:400,json:{success:false,error:{message:'刪除地址失敗'}}})
      state.addresses=state.addresses.filter(a=>a.id!==id);return ok(null)
     }
     if(state.failSave)return r.fulfill({status:400,json:{success:false,error:{message:'地址資料有誤'},errors:[{field:'line1',reason:'地址內容有誤'}]}})
     if(body.isDefault)state.addresses.forEach(a=>a.isDefault=false)
     if(method==='POST'){const created={...body,id:Math.max(0,...state.addresses.map(a=>a.id))+1};state.addresses.push(created);return ok(created)}
     Object.assign(item,body);return ok(item)
    }
    if(path==='/api/members/me')return ok({id:1,name:'會員',phone:'0912345678',role:'CUSTOMER'})
    if(path.endsWith('/wishlist/ids'))return ok([])
    if(path.endsWith('/cart'))return ok({items:[],totalQuantity:0,totalAmount:0})
    return ok(null)
   })
   const p=await context.newPage();p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message))
   try{await test(p,state,context);console.log('PASS '+name)}finally{await context.close()}
  }
  const form=p=>p.getByRole('form',{name:'收件地址編輯',exact:true}).waitFor()
  await scenario('Direct edit reload, safe DTO, default switch and mobile',async(p,s)=>{
   await p.goto(base+'/account/addresses/2/edit');await form(p);await p.reload();await form(p)
   assert.equal(await p.getByLabel('收件人',{exact:true}).inputValue(),'收件人2')
   await p.getByLabel('設為預設地址',{exact:true}).check();await p.getByRole('button',{name:'儲存地址',exact:true}).click()
   await p.getByText('地址已儲存',{exact:true}).waitFor()
   assert.deepEqual(Object.keys(s.calls.find(c=>c.method==='PUT').body).sort(),['receiverName','phone','city','district','line1','line2','postalCode','isDefault'].sort())
   assert.equal(s.addresses.filter(a=>a.isDefault).length,1);assert.equal(s.addresses[0].isDefault,false)
   assert.equal(s.calls.some(c=>c.path.endsWith('/addresses')&&c.method==='GET'),false)
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.getByRole('link',{name:'返回地址列表',exact:true}).click();await p.getByRole('link',{name:'編輯地址 #2',exact:true}).waitFor()
   assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  })
  await scenario('New first address defaults true, creates once and reloads edit URL',async(p,s)=>{
   s.addresses=[];await p.goto(base+'/account/addresses/new');await form(p)
   assert.equal(await p.getByLabel('設為預設地址',{exact:true}).isChecked(),true)
   assert.equal(await p.getByLabel('收件人',{exact:true}).inputValue(),'會員')
   await p.getByLabel('縣市',{exact:true}).fill('台南市');await p.getByLabel('地址',{exact:true}).fill('新地址')
   await p.getByRole('button',{name:'儲存地址',exact:true}).click();await p.waitForURL('**/addresses/1/edit');await form(p)
   await p.reload();await form(p);assert.equal(s.calls.filter(c=>c.method==='POST').length,1)
  })
  await scenario('New address with existing rows does not automatically replace default',async(p,s)=>{
   await p.goto(base+'/account/addresses/new');await form(p)
   assert.equal(await p.getByLabel('設為預設地址',{exact:true}).isChecked(),false)
   assert.equal(s.addresses[0].isDefault,true)
  })
  await scenario('Server field errors keep draft and confirmed retry restores latest address',async(p,s)=>{
   s.failSave=true;await p.goto(base+'/account/addresses/1/edit');await p.getByLabel('地址',{exact:true}).fill('保留草稿')
   await p.getByRole('button',{name:'儲存地址',exact:true}).click();await p.locator('#address-line1-error').waitFor()
   assert.equal(await p.getByLabel('地址',{exact:true}).getAttribute('aria-invalid'),'true')
await p.getByRole('button',{name:'重新載入地址',exact:true}).click();await p.getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(await p.getByLabel('地址',{exact:true}).inputValue(),'保留草稿')
   await p.getByRole('button',{name:'重新載入地址',exact:true}).click();await p.getByRole('button',{name:'放棄修改並重新載入',exact:true}).click()
   await p.waitForFunction(()=>document.querySelector('[aria-label="收件地址編輯"] input[maxlength="200"]')?.value==='測試路1')
  })
  await scenario('Unsaved leave dismiss/accept and saving prevents duplicates or navigation',async(p,s)=>{
   await p.goto(base+'/account/addresses/1/edit');await p.getByLabel('地址',{exact:true}).fill('未存')
   await p.getByRole('link',{name:'返回地址列表',exact:true}).click();await p.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.match(p.url(),/1\/edit$/)
   await p.getByRole('link',{name:'返回地址列表',exact:true}).click();await p.getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/addresses')
   assert.equal(s.calls.some(c=>c.method==='PUT'),false)
   await p.getByRole('link',{name:'編輯地址 #1',exact:true}).click();await form(p);s.writeDelay=500
   await p.getByRole('button',{name:'儲存地址',exact:true}).evaluate(b=>{b.click();b.click()})
   await p.getByRole('link',{name:'返回地址列表',exact:true}).click();assert.match(p.url(),/1\/edit$/)
   await p.getByText('地址已儲存',{exact:true}).waitFor();assert.equal(s.calls.filter(c=>c.method==='PUT').length,1)
  })
  await scenario('Delete confirmation, failure retry and no default auto-promotion',async(p,s)=>{
   await p.goto(base+'/account/addresses');await p.getByRole('button',{name:'刪除地址 #1',exact:true}).waitFor()
   await p.getByRole('button',{name:'刪除地址 #1',exact:true}).click();await p.getByRole('button',{name:'保留不變',exact:true}).click();assert.equal(s.calls.some(c=>c.method==='DELETE'),false)
   s.failDelete=true;await p.getByRole('button',{name:'刪除地址 #1',exact:true}).click();await p.getByRole('button',{name:'確認刪除',exact:true}).click();await p.getByText('刪除地址失敗',{exact:true}).waitFor()
   s.failDelete=false;await p.getByRole('button',{name:'刪除地址 #1',exact:true}).click();await p.getByRole('button',{name:'確認刪除',exact:true}).click();await p.getByText('地址已刪除',{exact:true}).waitFor()
   assert.equal(s.addresses.filter(a=>a.isDefault).length,0);assert.equal(await p.getByRole('link',{name:'編輯地址 #1',exact:true}).count(),0)
  })
  await scenario('Missing/invalid ID, list failure and safe retry',async(p,s)=>{
   await p.goto(base+'/account/addresses/99/edit');await p.getByText('找不到地址',{exact:true}).waitFor();assert.equal(await p.getByRole('form').count(),0)
   s.addresses.push(seed(99));await p.getByRole('button',{name:'重新載入地址',exact:true}).click();await form(p)
   await p.goto(base+'/account/addresses/0/edit');await p.getByText('地址編號須為有效的正整數',{exact:true}).waitFor()
   assert.equal(s.calls.some(c=>c.path.endsWith('/addresses/0')),false)
   s.fail=true;await p.goto(base+'/account/addresses');await p.getByText('地址無法載入',{exact:true}).waitFor()
   s.fail=false;await p.getByRole('button',{name:'重新載入地址',exact:true}).click();await p.getByRole('link',{name:'編輯地址 #1',exact:true}).waitFor()
  })
  await scenario('Slow detail does not overwrite another address route',async(p,s)=>{
   s.slow=true;await p.goto(base+'/account/addresses/1/edit');await p.getByRole('link',{name:'返回地址列表',exact:true}).click()
   await p.getByRole('link',{name:'編輯地址 #2',exact:true}).click();await form(p);await delay(600)
   assert.equal(await p.getByLabel('收件人',{exact:true}).inputValue(),'收件人2')
  })
  await scenario('Other-tab logout discards dirty address without blocking login',async(p,s,context)=>{
   await p.goto(base+'/account/addresses/1/edit');await p.getByLabel('地址',{exact:true}).fill('私人草稿')
   const other=await context.newPage();await other.goto(base+'/account');await other.getByRole('button',{name:'登出',exact:true}).click();await p.waitForURL(/\/login/)
   assert.equal(s.calls.some(c=>c.method==='PUT'),false);assert.equal(await p.getByRole('form',{name:'收件地址編輯',exact:true}).count(),0)
  })
  await scenario('Unauthenticated list/new/edit require login without address APIs',async(p,s)=>{
   for(const path of ['/account/addresses','/account/addresses/new','/account/addresses/1/edit']){await p.goto(base+path);await p.waitForURL(/\/login/)}
   assert.equal(s.calls.some(c=>c.path.includes('/members/me/addresses')),false)
  },false)
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
