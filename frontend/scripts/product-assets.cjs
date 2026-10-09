const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite')
 const server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5191,strictPort:true}})
 let browser;const errors=[]
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,role,test,width=390){
   const context=await browser.newContext({viewport:{width,height:844}})
   await context.addInitScript(role=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Admin',role},epoch:'products',reason:''})),role)
   const product=id=>({id,name:'商品'+id,slug:'product-'+id,categoryId:8,basePrice:100,status:'ON_SALE',isActive:true,images:[],skus:[{id:11,skuCode:'SKU-11',specName:'1kg',price:100,stock:10,safetyStock:2,isActive:true,version:0}]})
   const state={calls:[],fail:false,missing:false,slow:false,products:{1:product(1),2:product(2)}}
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route('**/api/images/**',r=>r.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aO1cAAAAASUVORK5CYII=','base64')}))
   await context.route(url=>url.pathname.startsWith('/admin-api/'),async r=>{
    const url=new URL(r.request().url()),path=url.pathname,method=r.request().method()
    const body=method==='GET'||r.request().headers()['content-type']?.includes('multipart')?null:r.request().postDataJSON()
    state.calls.push({path,method,body,params:Object.fromEntries(url.searchParams)})
    const ok=data=>r.fulfill({json:{success:true,data}})
    if(path.endsWith('/admin/categories')){
     const page=Number(url.searchParams.get('page'))
     return ok({content:page===0?[{id:8,name:'分類一',isActive:true}]:[{id:9,name:'分類二',isActive:false}],totalPages:2,last:page===1})
    }
    if(path.endsWith('/admin/products')){
     if(method==='POST'){state.products[2]={...body,id:2,skus:body.skus.map(s=>({...s,id:12,version:0}))};return ok(state.products[2])}
     return ok({content:[state.products[1]],totalPages:2})
    }
    if(/\/admin\/products\/\d+$/.test(path)){
     const id=path.split('/').pop()
     if(state.slow&&id==='1')await new Promise(resolve=>setTimeout(resolve,400))
     if(state.missing)return r.fulfill({status:404,json:{success:false,error:{message:'找不到商品'}}})
     if(method==='PUT'){
      if(state.fail)return r.fulfill({status:409,json:{success:false,error:{message:'商品 slug 已存在'}}})
      Object.assign(state.products[id],body)
     }
     return ok(state.products[id])
    }
    if(path.endsWith('/uploads/images'))return ok({url:'/api/images/upload.png'})
    if(path.endsWith('/images')){
     if(state.fail)return r.fulfill({status:409,json:{success:false,error:{message:'商品圖片已變更，請重新載入後再編輯'}}})
     const id=path.split('/').at(-2);state.products[id].images=body.images.map((img,i)=>({...img,id:100+state.calls.length+i}))
     return ok(state.products[id].images)
    }
    if(path.endsWith('/uploads/images'))return ok({url:'/api/images/upload.png'})
    if(path.endsWith('/admin/skus/11')){
     if(state.fail)return r.fulfill({status:409,json:{success:false,error:{message:'規格資料已更新'}}})
     return ok({...body,version:body.version+1})
    }
    if(path.endsWith('/skus'))return ok({...body,id:13,version:0})
    throw new Error('Unexpected API '+path)
   })
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept())
   try{await test(page,state);console.log('PASS '+name)}finally{await context.close()}
  }
  const goto=async(p,kind='skus',id='1')=>{await p.goto('http://127.0.0.1:5191/products/'+id+'/'+kind);await p.getByRole('heading',{name:kind==='images'?'商品圖片管理':'SKU 規格管理',exact:true}).waitFor();await p.getByText('商品'+id+' · #'+id,{exact:true}).waitFor()}
  await scenario('Direct SKU/image URLs reload, mobile and no writes','ADMIN',async(p,s)=>{
   await goto(p);await p.reload();await p.getByRole('heading',{name:'SKU 價格與庫存',exact:true}).waitFor()
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
   await goto(p,'images');await p.reload();await p.getByRole('form',{name:'商品圖片管理',exact:true}).waitFor()
   assert.equal(s.calls.some(c=>c.method!=='GET'),false);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
  })
  await scenario('SKU version update and conflict preserve draft','ADMIN',async(p,s)=>{
   await goto(p);const row=p.locator('tbody tr').first();await row.locator('input').nth(0).fill('新規格')
   await row.getByRole('button',{name:'儲存',exact:true}).click();await p.getByText('規格已儲存',{exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.path.endsWith('/skus/11')).at(-1).body.version,0)
   s.fail=true;await row.locator('input').nth(0).fill('保留草稿');await row.getByRole('button',{name:'儲存',exact:true}).click()
   await p.getByText('規格資料已更新',{exact:true}).waitFor();assert.equal(await row.locator('input').nth(0).inputValue(),'保留草稿')
   assert.equal(s.calls.filter(c=>c.path.endsWith('/skus/11')).at(-1).body.version,1)
  })
  await scenario('New SKU saved independently','ADMIN',async(p,s)=>{
   await goto(p);await p.getByPlaceholder('新 SKU 編號',{exact:true}).fill('NEW');await p.getByPlaceholder('規格',{exact:true}).fill('3kg')
   await p.getByRole('button',{name:'新增規格',exact:true}).click();await p.getByText('規格已儲存',{exact:true}).waitFor()
   assert.equal(await p.locator('tbody tr').count(),2);assert.equal(await p.getByPlaceholder('新 SKU 編號',{exact:true}).inputValue(),'')
   assert.equal(s.calls.filter(c=>c.method!=='GET').length,1)
  })
  await scenario('Invalid SKU quantity makes no write; unsaved mode switch is blocked','ADMIN',async(p,s)=>{
   await goto(p);const row=p.locator('tbody tr').first();await row.locator('input').nth(2).fill('-1')
   await row.getByRole('button',{name:'儲存',exact:true}).click();await p.getByText('價格不可為負，庫存與安全庫存須為有效的非負整數',{exact:true}).waitFor()
   assert.equal(s.calls.some(c=>c.method!=='GET'),false)
   await p.getByRole('link',{name:'返回商品編輯',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(new URL(p.url()).pathname,'/products/1/skus')
  })
  await scenario('Image reorder, primary removal and isolated snapshot save','ADMIN',async(p,s)=>{
   s.products[1].images=[{id:8,url:'/api/images/a.png',altText:'A',sortOrder:0,isPrimary:true},{id:9,url:'/api/images/b.png',altText:'B',sortOrder:1,isPrimary:false}]
   await goto(p,'images');await p.getByRole('button',{name:'往後',exact:true}).first().click()
   await p.getByRole('button',{name:'移除圖片',exact:true}).nth(1).click()
   assert.equal(await p.locator('input[type=radio]').first().isChecked(),true)
   await p.getByRole('button',{name:'儲存圖片',exact:true}).click();await p.getByText('圖片已儲存',{exact:true}).waitFor()
   const call=s.calls.filter(c=>c.method==='PUT').at(-1)
   assert.equal(call.path,'/admin-api/admin/products/1/images');assert.deepEqual(call.body.expectedImageIds,[8,9]);assert.equal(call.body.images[0].url,'/api/images/b.png')
   assert.equal('name'in call.body,false);assert.equal('skus'in call.body,false)
   await p.getByRole('button',{name:'儲存圖片',exact:true}).click();await p.getByText('圖片已儲存',{exact:true}).waitFor()
   assert.notDeepEqual(s.calls.filter(c=>c.method==='PUT').at(-1).body.expectedImageIds,[8,9])
  })
  await scenario('Image conflict keeps draft and retry cancel keeps it too','ADMIN',async(p,s)=>{
   await goto(p,'images');await p.getByLabel('圖片網址',{exact:true}).fill('/api/images/new.png')
   await p.getByRole('button',{name:'加入圖片清單',exact:true}).click();s.fail=true
   await p.getByRole('button',{name:'儲存圖片',exact:true}).click();await p.getByText('商品圖片已變更，請重新載入後再編輯',{exact:true}).waitFor()
   assert.equal(await p.locator('.image-row').count(),1)
   await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(await p.locator('.image-row').count(),1)
  })
  await scenario('Valid upload stays draft; invalid file makes no upload','ADMIN',async(p,s)=>{
   await goto(p,'images');await p.locator('input[type=file]').setInputFiles({name:'bad.txt',mimeType:'text/plain',buffer:Buffer.from('bad')})
   await p.getByText('請選擇 5 MB 以下的 PNG／JPEG 圖片',{exact:true}).waitFor();assert.equal(s.calls.some(c=>c.path.includes('/uploads/')),false)
   await p.locator('input[type=file]').setInputFiles({name:'test.png',mimeType:'image/png',buffer:Buffer.from('png')})
   await p.getByText('圖片已上傳，儲存圖片清單後生效',{exact:true}).waitFor()
   assert.equal(await p.getByLabel('圖片網址',{exact:true}).inputValue(),'/api/images/upload.png')
   assert.equal(s.calls.some(c=>c.method==='PUT'),false)
  })
  await scenario('Unsaved return blocks and query survives confirmed leave','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5191/products/1/images?brand=Acme&isActive=false&page=1')
   await p.getByRole('form',{name:'商品圖片管理',exact:true}).waitFor();await p.getByLabel('圖片網址',{exact:true}).fill('/api/images/draft.png')
   await p.getByRole('link',{name:'返回商品編輯',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(new URL(p.url()).pathname,'/products/1/images')
   await p.getByRole('link',{name:'返回商品編輯',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/products/1/edit?**')
   assert.equal(new URL(p.url()).searchParams.get('page'),'1');assert.equal(new URL(p.url()).searchParams.get('isActive'),'false')
   assert.equal(s.calls.some(c=>c.method!=='GET'),false)
  })
  await scenario('Slow SKU route cannot overwrite next product','ADMIN',async(p,s)=>{
   s.slow=true;const started=p.waitForRequest(r=>new URL(r.url()).pathname==='/admin-api/admin/products/1');await p.goto('http://127.0.0.1:5191/products/1/skus');await started
   await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');void router.push('/products/2/skus')});await p.waitForURL('**/products/2/skus')
   await p.getByText('商品2 · #2',{exact:true}).waitFor();await p.waitForTimeout(500);assert.equal(await p.getByText('商品1 · #1',{exact:true}).count(),0)
  })
  await scenario('Invalid ID and 404 safe retry','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5191/products/bad/images');await p.getByText('商品編號格式不正確',{exact:true}).waitFor();assert.equal(s.calls.length,0)
   s.missing=true;await p.goto('http://127.0.0.1:5191/products/1/images');await p.getByText('找不到商品',{exact:true}).waitFor()
   s.missing=false;await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('form',{name:'商品圖片管理',exact:true}).waitFor()
  })
  await scenario('Legacy asset URL redirects and refuses customer','CUSTOMER',async(p,s)=>{
   await p.goto('http://127.0.0.1:5191/admin/products/1/skus?page=1');await p.waitForURL('**/login?**')
   assert.equal(new URL(p.url()).searchParams.get('redirect'),'/products/1/skus?page=1');assert.equal(s.calls.length,0)
  })
  for(const width of [1440,390])for(const kind of ['skus','images'])await scenario(`Asset draft ${kind} ${width}: focus, Escape, reload, route reuse and logout`,'ADMIN',async(p,s)=>{
   await goto(p,kind)
   const field=kind==='images'?p.getByLabel('圖片網址',{exact:true}):p.getByLabel('SKU-11 規格名稱',{exact:true})
   const baseline=await field.inputValue(),draft=kind==='images'?'/api/images/draft.png':'未儲存規格'
   await field.fill(draft);const dialog=p.getByRole('dialog'),reload=p.getByRole('button',{name:'重新載入',exact:true})
   await reload.click();await dialog.waitFor();assert.equal(await p.evaluate(()=>document.activeElement.textContent),'繼續編輯');assert.equal(await field.isDisabled(),true)
   await p.locator('.page form').evaluateAll(forms=>forms.forEach(f=>f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))))
   assert.equal(s.calls.some(c=>c.method!=='GET'),false)
   if(process.env.PREVIEW_SCREENSHOT_DIR){await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))});await p.screenshot({path:require('node:path').join(process.env.PREVIEW_SCREENSHOT_DIR,`admin-draft-${kind}-${width}.png`)})}
   await p.keyboard.press('Escape');await dialog.waitFor({state:'hidden'});assert.equal(await field.inputValue(),draft)
   await reload.click();await dialog.getByRole('button',{name:'放棄修改並重新載入',exact:true}).click();await field.waitFor();assert.equal(await field.inputValue(),baseline)
   await field.fill(draft);assert.equal(await p.evaluate(()=>{const e=new Event('beforeunload',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented}),true)
   async function navigate(url){await p.evaluate(async url=>{const {default:router}=await import('/src/router/index.js');void router.push(url)},url)}
   const alternate=`/products/2/${kind==='images'?'skus':'images'}`
   await navigate(alternate);await dialog.getByRole('button',{name:'繼續編輯',exact:true}).click();assert.equal(new URL(p.url()).pathname,`/products/1/${kind}`);assert.equal(await field.inputValue(),draft)
   await navigate(alternate);await dialog.getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL(u=>u.pathname===alternate);await p.getByText('商品2 · #2',{exact:true}).waitFor()
   const nextField=kind==='images'?p.getByLabel('SKU-11 規格名稱',{exact:true}):p.getByLabel('圖片網址',{exact:true});await nextField.fill(draft)
   await reload.click();await dialog.waitFor();await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()})
   await p.waitForURL(u=>u.pathname==='/login');await dialog.waitFor({state:'hidden'});assert.equal(await p.evaluate(()=>document.body.style.overflow),'');assert.equal(s.calls.some(c=>c.method!=='GET'),false)
  },width)
  assert.deepEqual(errors,[])
 }finally{if(browser)await browser.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
