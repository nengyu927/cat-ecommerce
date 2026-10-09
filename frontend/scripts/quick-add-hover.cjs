const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5214,strictPort:true}})
 await server.listen();let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const mobile of [false,true]){
   const ctx=await browser.newContext({viewport:{width:mobile?390:1440,height:900},hasTouch:mobile,isMobile:mobile})
   await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'quickadd'})))
   const product={id:1,name:'快速商品',basePrice:100,primaryImageUrl:'/product-images/royal-canin-kitten-dry.png'}
   let skus=[{id:11,specName:'小包',price:100,stock:3}],count=0,fail=false,detailGate=null
   const writes=[],pageData={content:[product],number:0,size:12,totalPages:1,totalElements:1}
   const cart=()=>({items:count?[{id:1,skuId:11,productName:product.name,specName:'小包',imageUrl:product.primaryImageUrl,quantity:count,stock:10,unitPrice:100,subtotal:count*100}]:[],totalQuantity:count,totalAmount:count*100})
   await ctx.route(url=>url.pathname.startsWith('/api/'),async r=>{
    const pathname=new URL(r.request().url()).pathname,ok=data=>r.fulfill({json:{success:true,data}})
    if(pathname==='/api/cart/items'){
     writes.push(r.request().postDataJSON())
     if(fail)return r.fulfill({status:409,json:{success:false,error:{message:'庫存變動，請重試'}}})
     count++;return ok(cart())
    }
    if(pathname==='/api/cart')return ok(cart())
    if(pathname==='/api/products/1'){if(detailGate)await detailGate.wait;return ok({...product,skus})}
    if(pathname.endsWith('/related'))return ok([])
    if(pathname==='/api/products')return ok(pageData)
    return ok([])
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   await p.goto('http://127.0.0.1:5214/products')
   await p.locator('.product-body').waitFor()
   assert.equal(await p.locator('.product-body a').count(),0)
   if(!mobile){
    await p.locator('.member-icon-link').hover()
    const menu=p.getByRole('navigation',{name:'會員功能選單'})
    await menu.waitFor();await menu.getByRole('link',{name:'收件地址',exact:true}).hover();assert.equal(await menu.isVisible(),true)
    await p.keyboard.press('Escape');await menu.waitFor({state:'hidden'})
    await p.locator('.cart-link').hover();const panel=p.getByRole('region',{name:'購物車清單'})
    await panel.waitFor();await panel.hover();assert.equal(await panel.isVisible(),true)
    await p.getByRole('heading',{name:'所有商品',exact:true}).click();await panel.waitFor({state:'hidden'})
   }
   const add=p.getByRole('button',{name:'加入購物車：快速商品',exact:true})
   await add.evaluate(b=>{b.click();b.click()})
   await p.getByRole('dialog',{name:'購物車預覽'}).waitFor();assert.deepEqual(writes,[{skuId:11,quantity:1}])
   assert.equal(new URL(p.url()).pathname,'/products');await p.keyboard.press('Escape')
   skus=[...skus,{id:12,specName:'大包',price:200,stock:2},{id:13,specName:'缺貨包',price:300,stock:0}]
   await add.click();const modal=p.getByRole('dialog',{name:'選擇商品規格'})
   await modal.waitFor();assert.equal(await modal.getByRole('button',{name:'確認加入購物車',exact:true}).isDisabled(),true)
   assert.equal(await modal.locator('option[value="13"]').isDisabled(),true)
   await modal.getByRole('button',{name:'取消',exact:true}).click();assert.equal(writes.length,1)
   await add.click();await modal.getByLabel('規格與價格').selectOption('12');await modal.getByRole('button',{name:'確認加入購物車',exact:true}).click()
   await p.getByRole('dialog',{name:'購物車預覽'}).waitFor();assert.deepEqual(writes[1],{skuId:12,quantity:1});await p.keyboard.press('Escape')
   skus=[skus[0]];fail=true;await add.click();await p.getByRole('alert').filter({hasText:'庫存變動'}).waitFor()
   assert.equal(await p.getByRole('dialog',{name:'購物車預覽'}).isVisible(),false)
   fail=false;let release;detailGate={wait:new Promise(done=>{release=done})}
   const request=p.waitForRequest(r=>new URL(r.url()).pathname==='/api/products/1')
   await add.click();await request
   await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().clearSession()})
   release();detailGate=null;await p.waitForFunction(()=>!document.querySelector('.quick-add-button').disabled)
   assert.equal(writes.length,3)
   await add.click();await p.waitForURL('**/login?redirect=/products')
   await ctx.close();console.log('PASS quick add/header: single/multiple SKU, no silent choice, duplicate/stock failure, logout/guest, image-only detail and hover '+(mobile?'touch':'desktop'))
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
