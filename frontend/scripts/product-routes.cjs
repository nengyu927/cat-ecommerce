const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite')
 const server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5189,strictPort:true}})
 let browser;const errors=[]
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,role,test){
   const context=await browser.newContext({viewport:{width:390,height:844}})
   await context.addInitScript(role=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Admin',role},epoch:'products',reason:''})),role)
   const product=id=>({id,name:'商品'+id,slug:'product-'+id,categoryId:8,basePrice:100,status:'ON_SALE',isActive:true,images:[],skus:[{id:11,skuCode:'SKU-11',specName:'1kg',price:100,stock:10,safetyStock:2,isActive:true,version:0}]})
   const state={calls:[],fail:false,missing:false,slow:false,products:{1:product(1),2:product(2)}}
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route('**/api/images/**',r=>r.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aO1cAAAAASUVORK5CYII=','base64')}))
   await context.route(url=>url.pathname.startsWith('/admin-api/'),async r=>{
    const url=new URL(r.request().url()),path=url.pathname,method=r.request().method()
    const body=method==='GET'?null:r.request().postDataJSON()
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
  const goto=async(p,url='/products/1/edit')=>{await p.goto('http://127.0.0.1:5189'+url);await p.getByLabel('名稱',{exact:true}).waitFor()}
  const mainSave=p=>p.locator('form').first().getByRole('button',{name:'儲存',exact:true})
  await scenario('Direct edit, reload, category pagination and mobile','ADMIN',async(p,s)=>{
   await goto(p);assert.equal(await p.getByLabel('名稱',{exact:true}).inputValue(),'商品1')
   assert.equal(await p.getByLabel('分類',{exact:true}).locator('option').count(),2,await p.getByLabel('分類',{exact:true}).evaluate(el=>el.outerHTML))
   await p.reload();await p.getByLabel('名稱',{exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.method!=='GET').length,0)
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
  })
  await scenario('Create requires first SKU and navigates to saved product','ADMIN',async(p,s)=>{
   await goto(p,'/products/new');await p.getByLabel('名稱',{exact:true}).fill('新商品')
   await p.getByLabel('網址代稱（小寫英數字與連字號）').fill('new-product')
   await mainSave(p).click();assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
   await p.getByPlaceholder('SKU 編號',{exact:true}).fill('NEW-1');await p.getByPlaceholder('規格名稱',{exact:true}).fill('2kg')
   await mainSave(p).click();await p.waitForURL('**/products/2/edit')
   await p.getByLabel('名稱',{exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.method==='POST')[0].body.skus[0].skuCode,'NEW-1')
   assert.equal(await p.getByLabel('名稱',{exact:true}).inputValue(),'新商品')
  })
  await scenario('Update whitelists DTO and retains failed draft','ADMIN',async(p,s)=>{
   await goto(p);s.fail=true;await p.getByLabel('名稱',{exact:true}).fill('草稿')
   await mainSave(p).click();await p.getByRole('alert').getByText('商品 slug 已存在',{exact:true}).waitFor()
   assert.equal(await p.getByLabel('名稱',{exact:true}).inputValue(),'草稿')
   s.fail=false;await mainSave(p).click();await p.getByText('已儲存',{exact:true}).waitFor()
   const body=s.calls.filter(c=>c.method==='PUT').at(-1).body
   assert.equal(body.name,'草稿');assert.equal('skus'in body,false);assert.equal('imageUrl'in body,false);assert.equal('id'in body,false)
  })
  await scenario('Unsaved leave cancel and confirm','ADMIN',async(p,s)=>{
   await goto(p);await p.getByLabel('名稱',{exact:true}).fill('未儲存');p.removeAllListeners('dialog')
   await p.getByRole('link',{name:'返回商品列表',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(new URL(p.url()).pathname,'/products/1/edit');assert.equal(await p.getByLabel('名稱',{exact:true}).inputValue(),'未儲存')
   await p.getByRole('link',{name:'返回商品列表',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/products')
   assert.equal(s.calls.some(c=>c.method!=='GET'),false)
  })
  await scenario('Edit links assets and main update never carries image snapshot','ADMIN',async(p,s)=>{
   await goto(p);assert.equal(await p.getByRole('link',{name:'管理 SKU',exact:true}).count(),1)
   assert.equal(await p.getByRole('link',{name:'管理圖片',exact:true}).count(),1)
   await mainSave(p).click();await p.getByText('已儲存',{exact:true}).waitFor()
   assert.equal('images' in s.calls.filter(c=>c.method==='PUT').at(-1).body,false)
  })
  await scenario('List query survives edit return and browser history','ADMIN',async(p,s)=>{
   const q='?keyword=cat&status=ON_SALE&categoryId=8&brand=Acme&isActive=false&page=1'
   await p.goto('http://127.0.0.1:5189/products'+q)
   await p.getByRole('button',{name:'編輯',exact:true}).click();await p.waitForURL('**/products/1/edit?**')
   await p.getByRole('link',{name:'返回商品列表',exact:true}).click();await p.getByRole('heading',{name:'商品管理',exact:true}).waitFor()
   const params=s.calls.filter(c=>c.path.endsWith('/admin/products')).at(-1).params
   assert.equal(params.page,'1');assert.equal(params.isActive,'false');assert.equal(params.brand,'Acme')
   await p.goBack();await p.getByLabel('名稱',{exact:true}).waitFor();await p.goForward();await p.getByRole('heading',{name:'商品管理',exact:true}).waitFor()
  })
  await scenario('Slow detail cannot overwrite the next product','ADMIN',async(p,s)=>{
   s.slow=true;const started=p.waitForRequest(r=>new URL(r.url()).pathname.endsWith('/admin/products/1'))
   await p.goto('http://127.0.0.1:5189/products/1/edit');await started
   await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');void router.push('/products/2/edit')})
   await p.waitForURL('**/products/2/edit');await p.getByLabel('名稱',{exact:true}).waitFor();await p.waitForTimeout(500)
   assert.equal(await p.getByLabel('名稱',{exact:true}).inputValue(),'商品2')
  })
  await scenario('Invalid ID and missing product safely retry','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5189/products/bad/edit');await p.getByText('商品編號格式不正確',{exact:true}).waitFor();assert.equal(s.calls.length,0)
   s.missing=true;await p.goto('http://127.0.0.1:5189/products/1/edit');await p.getByText('找不到商品',{exact:true}).waitFor()
   s.missing=false;await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByLabel('名稱',{exact:true}).waitFor()
  })
  await scenario('Legacy redirect and customer rejection','CUSTOMER',async(p,s)=>{
   await p.goto('http://127.0.0.1:5189/admin/products/1/edit?page=1');await p.waitForURL('**/login?**')
   assert.equal(new URL(p.url()).searchParams.get('redirect'),'/products/1/edit?page=1');assert.equal(s.calls.length,0)
  })
  await scenario('Invalid product list query rejected without API','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5189/products?isActive=no');await p.getByRole('alert').filter({hasText:'不支援此值'}).waitFor()
   assert.equal(s.calls.length,0);await p.getByRole('button',{name:'清除篩選',exact:true}).click();await p.getByText('商品1',{exact:true}).waitFor()
  })
  assert.deepEqual(errors,[])
 }finally{if(browser)await browser.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
