const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5211,strictPort:true}})
 await server.listen();let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const width of [1440,390]){
   const ctx=await browser.newContext({viewport:{width,height:850}})
   await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'drawer'})))
   let quantity=1,fail=false,writeFail=false
   const data=()=>({items:quantity?[{id:1,skuId:1,productName:'示範乾糧',specName:'小包',imageUrl:'/product-images/royal-canin-kitten-dry.png',quantity,stock:3,unitPrice:100,subtotal:quantity*100}]:[],totalQuantity:quantity,totalAmount:quantity*100})
   await ctx.route(url=>url.pathname.startsWith('/api/'),r=>{
    const pathname=new URL(r.request().url()).pathname
    if(pathname==='/api/wishlist/ids')return r.fulfill({json:{success:true,data:[]}})
    if(pathname==='/api/cart'&&fail)return r.fulfill({status:503,json:{success:false,error:{message:'同步暫時失敗'}}})
    if(pathname==='/api/cart/items/1'){
     if(writeFail)return r.fulfill({status:409,json:{success:false,error:{message:'庫存已變更'}}})
     quantity=r.request().method()==='DELETE'?0:r.request().postDataJSON().quantity
    }
    return r.fulfill({json:{success:true,data:data()}})
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   await p.goto('http://127.0.0.1:5211/cart')
await p.locator('header button.cart-link').click()
   const drawer=p.getByRole('dialog',{name:'購物車預覽'})
   await drawer.waitFor();assert.ok(new URL(p.url()).pathname==='/cart')
   // Native modal prevents focusing background controls; Edge may momentarily focus browser chrome (body).
   for(let i=0;i<12;i++){await p.keyboard.press('Tab');assert.equal(await drawer.evaluate(d=>document.activeElement===document.body||d.contains(document.activeElement)),true)}
   const plus=drawer.getByRole('button',{name:'增加數量：示範乾糧',exact:true})
   await plus.waitFor();await p.waitForFunction(()=>!document.querySelector('.drawer-quantity button:last-child').disabled)
   assert.ok(await drawer.locator('img').evaluate(i=>i.complete&&i.naturalWidth>0))
   await plus.click();await drawer.getByText('NT$ 200',{exact:true}).waitFor()
   await plus.click();await drawer.getByText('NT$ 300',{exact:true}).waitFor();assert.equal(await plus.isDisabled(),true)
   writeFail=true;await drawer.getByRole('button',{name:'移除：示範乾糧'}).click()
   await drawer.getByRole('alert').waitFor();assert.equal(await drawer.getByRole('button',{name:'前往結帳',exact:true}).isDisabled(),true)
   writeFail=false;await drawer.getByRole('button',{name:'重試同步購物車'}).click()
   await p.waitForFunction(()=>!document.querySelector('.drawer-remove').disabled)
   await drawer.getByRole('button',{name:'移除：示範乾糧'}).click();await drawer.getByText('購物車還空著',{exact:true}).waitFor()
   await p.keyboard.press('Escape');await drawer.waitFor({state:'hidden'})
   assert.equal(await p.evaluate(()=>document.body.style.overflow),'')
   fail=true;await p.locator('header button.cart-link').click();await drawer.getByRole('alert').waitFor()
   fail=false;await drawer.getByRole('button',{name:'重試同步購物車'}).click();await drawer.getByText('購物車還空著',{exact:true}).waitFor()
   await p.evaluate(async()=>{const {useAuthStore}=await import('/src/stores/auth.js');useAuthStore().logout()})
   await drawer.waitFor({state:'hidden'});await p.waitForURL('**/login?redirect=/cart');await p.locator('header button.cart-link').click();await drawer.getByText('登入後查看購物車',{exact:true}).waitFor()
   assert.equal(await drawer.locator('.drawer-item').count(),0)
   await p.keyboard.press('Escape');await ctx.close();console.log('PASS drawer: image, quantity/stock, remove, failure/retry, Esc/scroll, logout/guest '+width)
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
