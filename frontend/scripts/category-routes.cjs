const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite')
 const server=await createServer({mode:'admin',server:{host:'127.0.0.1',port:5192,strictPort:true}})
 let browser;const errors=[]
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,role,test){
   const context=await browser.newContext({viewport:{width:390,height:844}})
   await context.addInitScript(role=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'Admin',role},epoch:'category',reason:''})),role)
   const state={calls:[],fail:false,slowOnce:false,readFailure:false,categories:[
    {id:1,name:'根分類',slug:'root',parentId:null,sortOrder:0,isActive:true,productCount:0},
    {id:2,name:'子分類',slug:'child',parentId:1,sortOrder:1,isActive:true,productCount:0},
    {id:3,name:'停用孫分類',slug:'grand',parentId:2,sortOrder:2,isActive:false,productCount:0},
    {id:4,name:'其他分類',slug:'other',parentId:null,sortOrder:3,isActive:true,productCount:0}]}
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>url.pathname.startsWith('/admin-api/'),async r=>{
    const url=new URL(r.request().url()),path=url.pathname,method=r.request().method(),body=method==='GET'?null:r.request().postDataJSON()
    state.calls.push({path,method,body,params:Object.fromEntries(url.searchParams)})
    const ok=data=>r.fulfill({json:{success:true,data}})
    if(path.endsWith('/admin/categories')&&method==='GET'){
     if(state.slowOnce){state.slowOnce=false;await new Promise(resolve=>setTimeout(resolve,400))}
     if(state.readFailure)return r.fulfill({status:500,json:{success:false,error:{message:'分類載入失敗'}}})
     const page=Number(url.searchParams.get('page')||0)
     return ok({content:page===0?state.categories.slice(0,2):state.categories.slice(2),totalPages:2,last:page===1})
    }
    if(path.endsWith('/admin/categories')&&method==='POST'){
     const data={...body,id:5,productCount:0};state.categories.push(data);return ok(data)
    }
    if(path.includes('/admin/categories/')&&method==='PUT'){
     if(state.fail)return r.fulfill({status:409,json:{success:false,error:{message:'不可將子分類或孫分類設為上層分類'}}})
     const id=Number(path.split('/').pop()),data={...body,id,productCount:0};state.categories[state.categories.findIndex(c=>c.id===id)]=data;return ok(data)
    }
    throw new Error('Unexpected API '+path)
   })
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept())
   try{await test(page,state);console.log('PASS '+name)}finally{await context.close()}
  }
  const goto=async(p,path='/categories/1/edit')=>{await p.goto('http://127.0.0.1:5192'+path);await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor()}
  await scenario('Direct edit/reload, inactive descendants and mobile','ADMIN',async(p,s)=>{
   await goto(p);assert.equal(await p.getByLabel('名稱',{exact:true}).inputValue(),'根分類')
   assert.deepEqual(await p.getByLabel('上層分類',{exact:true}).locator('option').allTextContents(),['無（根分類）','其他分類'])
   await p.reload();await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor()
   assert.equal(s.calls.some(c=>c.method!=='GET'),false);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
   await goto(p,'/categories/3/edit');assert.equal(await p.getByLabel('啟用',{exact:true}).isChecked(),false)
  })
  await scenario('Create then reload saved category with selected parent','ADMIN',async(p,s)=>{
   await goto(p,'/categories/new');assert.equal(await p.getByLabel('上層分類',{exact:true}).locator('option').count(),5)
   await p.getByLabel('名稱',{exact:true}).fill('新分類');await p.getByLabel('網址代稱（小寫英數字與連字號）').fill('new-category')
   await p.getByLabel('上層分類',{exact:true}).selectOption('4');await p.getByRole('button',{name:'儲存',exact:true}).click()
   await p.waitForURL('**/categories/5/edit');await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor()
   assert.equal(s.calls.filter(c=>c.method==='POST').length,1);assert.equal(s.calls.find(c=>c.method==='POST').body.parentId,4)
   await p.reload();await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor();assert.equal(await p.getByLabel('名稱',{exact:true}).inputValue(),'新分類')
  })
  await scenario('Update uses DTO fields only and clean return does not prompt','ADMIN',async(p,s)=>{
   await goto(p);await p.getByLabel('名稱',{exact:true}).fill('新版根');await p.getByLabel('排序',{exact:true}).fill('-2')
   await p.getByLabel('啟用',{exact:true}).uncheck();await p.getByRole('button',{name:'儲存',exact:true}).click();await p.getByText('分類已儲存',{exact:true}).waitFor()
   assert.deepEqual(s.calls.filter(c=>c.method==='PUT').at(-1).body,{name:'新版根',slug:'root',parentId:null,sortOrder:-2,isActive:false})
   p.removeAllListeners('dialog');p.on('dialog',d=>{errors.push('Clean form unexpectedly dirty');d.dismiss()})
   await p.getByRole('link',{name:'返回分類列表',exact:true}).click();await p.waitForURL('**/categories')
  })
  await scenario('Hierarchy conflict keeps draft; retry needs confirmation','ADMIN',async(p,s)=>{
   await goto(p);s.fail=true;await p.getByLabel('上層分類',{exact:true}).selectOption('4')
   await p.getByRole('button',{name:'儲存',exact:true}).click();await p.getByText('不可將子分類或孫分類設為上層分類',{exact:true}).waitFor()
   assert.equal(await p.getByLabel('上層分類',{exact:true}).inputValue(),'4')
   p.removeAllListeners('dialog');await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(await p.getByLabel('上層分類',{exact:true}).inputValue(),'4')
   await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'放棄修改並重新載入',exact:true}).click();await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor()
   assert.equal(await p.getByLabel('上層分類',{exact:true}).locator('option:checked').textContent(),'無（根分類）')
  })
  await scenario('Unsaved leave dismiss stays; accept leaves without write','ADMIN',async(p,s)=>{
   await goto(p);await p.getByLabel('名稱',{exact:true}).fill('草稿');p.removeAllListeners('dialog')
   await p.getByRole('link',{name:'返回分類列表',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'繼續編輯',exact:true}).click()
   assert.equal(new URL(p.url()).pathname,'/categories/1/edit')
   await p.getByRole('link',{name:'返回分類列表',exact:true}).click();await p.getByRole('dialog').getByRole('button',{name:'放棄修改並離開',exact:true}).click();await p.waitForURL('**/categories')
   assert.equal(s.calls.some(c=>c.method!=='GET'),false)
  })
  await scenario('List query and history survive category edit return','ADMIN',async(p,s)=>{
   const q='?keyword=cat&isActive=false&page=1';await p.goto('http://127.0.0.1:5192/categories'+q)
   await p.getByRole('button',{name:'編輯',exact:true}).first().click();await p.waitForURL('**/categories/3/edit?**')
   await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor();await p.getByRole('link',{name:'返回分類列表',exact:true}).click()
   await p.getByRole('button',{name:'編輯',exact:true}).first().waitFor()
   const params=s.calls.filter(c=>c.params.size==='20').at(-1).params
   assert.equal(params.page,'1');assert.equal(params.keyword,'cat');assert.equal(params.isActive,'false')
   await p.goBack();await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor();await p.goForward();await p.getByRole('heading',{name:'分類管理',exact:true}).waitFor()
  })
  await scenario('Invalid ID, missing category and failed page have safe retry','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5192/categories/bad/edit');await p.getByText('分類編號格式不正確',{exact:true}).waitFor();assert.equal(s.calls.length,0)
   await p.goto('http://127.0.0.1:5192/categories/99/edit');await p.getByText('找不到分類',{exact:true}).waitFor()
   s.categories.push({id:99,name:'補上分類',slug:'added',parentId:null,sortOrder:0,isActive:true})
   await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor()
   s.readFailure=true;await p.goto('http://127.0.0.1:5192/categories/1/edit');await p.getByText('分類載入失敗',{exact:true}).waitFor()
   assert.equal(await p.getByRole('form',{name:'分類編輯',exact:true}).count(),0)
   s.readFailure=false;await p.getByRole('button',{name:'重新載入',exact:true}).click();await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor()
  })
  await scenario('Slow hierarchy cannot overwrite next category','ADMIN',async(p,s)=>{
   s.slowOnce=true;const started=p.waitForRequest(r=>new URL(r.url()).pathname.endsWith('/admin/categories'))
   await p.goto('http://127.0.0.1:5192/categories/1/edit');await started
   await p.evaluate(async()=>{const {default:router}=await import('/src/router/index.js');void router.push('/categories/2/edit')})
   await p.waitForURL('**/categories/2/edit');await p.getByRole('form',{name:'分類編輯',exact:true}).waitFor();await p.waitForTimeout(500)
   assert.equal(await p.getByLabel('名稱',{exact:true}).inputValue(),'子分類')
  })
  await scenario('Legacy direct URL preserves query','ADMIN',async(p,s)=>{
   await goto(p,'/admin/categories/1/edit?isActive=false&page=1')
   assert.equal(new URL(p.url()).pathname,'/categories/1/edit');assert.equal(new URL(p.url()).searchParams.get('isActive'),'false')
  })
  await scenario('Customer denied with no category API calls','CUSTOMER',async(p,s)=>{
   await p.goto('http://127.0.0.1:5192/categories/new');await p.waitForURL('**/login?**');assert.equal(s.calls.length,0)
  })
  await scenario('Invalid list page rejected; empty catalog can create root','ADMIN',async(p,s)=>{
   await p.goto('http://127.0.0.1:5192/categories?page=-1');await p.getByRole('alert').filter({hasText:'有效範圍內的整數'}).waitFor();assert.equal(s.calls.length,0)
   await p.getByRole('button',{name:'清除篩選',exact:true}).click();await p.getByRole('button',{name:'編輯',exact:true}).first().waitFor()
   s.categories=[];await goto(p,'/categories/new');assert.equal(await p.getByLabel('上層分類',{exact:true}).locator('option').count(),1)
  })
  assert.deepEqual(errors,[])
 }finally{if(browser)await browser.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
