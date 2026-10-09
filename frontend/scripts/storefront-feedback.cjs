const {chromium}=require('playwright'),assert=require('node:assert/strict')
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5212,strictPort:true}})
 await server.listen();let browser;const errors=[]
 try{
  browser=await chromium.launch({channel:'msedge',headless:true})
  for(const [width,reducedMotion] of [[1440,'no-preference'],[390,'reduce']]){
   const ctx=await browser.newContext({viewport:{width,height:850},reducedMotion})
   await ctx.addInitScript(()=>localStorage.setItem('catshop_session',JSON.stringify({token:'test',member:{id:1,name:'會員',role:'CUSTOMER'},epoch:'feedback'})))
   let fail=false
   await ctx.route(url=>url.pathname.startsWith('/api/'),r=>{
    const pathname=new URL(r.request().url()).pathname
    if(pathname==='/api/wishlist/ids')return r.fulfill({json:{success:true,data:[]}})
    if(pathname==='/api/cart'&&fail)return r.fulfill({status:503,json:{success:false,error:{message:'同步暫時失敗'}}})
    return r.fulfill({json:{success:true,data:{items:[{id:1,skuId:1,productName:'測試商品',quantity:1,stock:3,unitPrice:100,subtotal:100}],totalQuantity:1,totalAmount:100}}})
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   await p.goto('http://127.0.0.1:5212/cart')
   const sync=p.getByRole('button',{name:'重新同步購物車',exact:true})
   await sync.waitFor();await p.waitForFunction(()=>!document.querySelector('.page .btn').disabled)
   const count=await sync.evaluate(b=>{b.dispatchEvent(new MouseEvent('click',{bubbles:true}));return b.getAnimations().length})
   assert.equal(count>0,reducedMotion==='no-preference')
   const notice=p.locator('.feedback-notice')
   await notice.waitFor();await p.getByText('購物車已同步最新庫存與價格',{exact:true}).waitFor()
   assert.equal(await notice.getAttribute('role'),'status')
   if(process.env.FEEDBACK_SCREENSHOT_DIR){
    const path=require('node:path');assert.ok(path.isAbsolute(process.env.FEEDBACK_SCREENSHOT_DIR))
    await notice.evaluate(async e=>{await Promise.all(e.getAnimations().map(a=>a.finished))})
    await p.screenshot({path:path.join(process.env.FEEDBACK_SCREENSHOT_DIR,'feedback-success-'+width+'.png'),fullPage:true})
   }
   await p.getByRole('button',{name:'關閉成功提示'}).focus();await p.keyboard.press('Enter');await notice.waitFor({state:'hidden'})
   await sync.click();await notice.waitFor()
   fail=true;await sync.click();const error=p.getByRole('alert').filter({hasText:'同步暫時失敗'})
   await error.waitFor();assert.equal(await p.getByRole('button',{name:'前往結帳',exact:true}).isDisabled(),true)
   if(process.env.FEEDBACK_SCREENSHOT_DIR){
    await error.evaluate(async e=>{await Promise.all(e.getAnimations().map(a=>a.finished))})
    await p.screenshot({path:require('node:path').join(process.env.FEEDBACK_SCREENSHOT_DIR,'feedback-error-'+width+'.png'),fullPage:true})
   }
   assert.equal(await error.evaluate(e=>getComputedStyle(e,'::before').content),'"!"')
   if(reducedMotion==='reduce')assert.equal(await error.evaluate(e=>e.getAnimations().length),0)
   fail=false;await p.getByRole('button',{name:'重試同步',exact:true}).click();await error.waitFor({state:'hidden'});await notice.waitFor()
   // Changing the OS preference must cancel active click effects, not only future clicks.
   await p.emulateMedia({reducedMotion:'reduce'})
   await sync.evaluate(b=>b.dispatchEvent(new MouseEvent('click',{bubbles:true})))
   assert.equal(await sync.evaluate(b=>b.getAnimations().length),0)
   await ctx.close();console.log('PASS feedback: themed persistent error, dismiss/reappear success, keyboard, click motion and reduced motion '+width)
  }
  assert.deepEqual(errors,[])
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
