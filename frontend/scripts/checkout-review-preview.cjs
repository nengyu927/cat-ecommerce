// Read-only live preview. Cancel review; never call order creation.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path')
async function main(){
 const output=process.env.PREVIEW_SCREENSHOT_DIR
 assert.ok(output&&path.isAbsolute(output))
 const browser=await chromium.launch({channel:'msedge',headless:true})
 try{
  for(const width of [1440,390]){
   const ctx=await browser.newContext({viewport:{width,height:900}}),writes=[],errors=[]
   ctx.setDefaultTimeout(15000)
   await ctx.route(url=>url.pathname==='/api/orders',r=>{
    if(r.request().method()==='POST'){writes.push('order');return r.abort()}
    return r.continue()
   })
   const p=await ctx.newPage();p.on('pageerror',e=>errors.push(e.message))
   await p.goto('http://127.0.0.1:5173/login');await p.getByRole('button',{name:'一鍵登入會員（展示用）',exact:true}).click();await p.waitForURL('**/')
   await p.goto('http://127.0.0.1:5173/checkout')
   await p.getByLabel('收件人 *',{exact:true}).fill('預覽收件人')
   await p.getByLabel('收件電話 *',{exact:true}).fill('0912345678')
   await p.getByLabel('收件地址 *',{exact:true}).fill('台北市展示地址')
   const button=p.getByRole('button',{name:'確認下單',exact:true})
   // Never add goods merely for a preview. Empty/invalid carts keep their protection.
   if(await button.isEnabled()){
    await button.click();const dialog=p.getByRole('dialog',{name:'確認此次訂單',exact:true});await dialog.waitFor()
    await dialog.evaluate(async d=>{await Promise.all(d.getAnimations().map(a=>a.finished))})
    await p.screenshot({path:path.join(output,`checkout-review-live-${width}.png`)})
    await dialog.getByRole('button',{name:'返回修改',exact:true}).click();await dialog.waitFor({state:'hidden'})
    assert.equal(await p.evaluate(()=>sessionStorage.getItem('catshop_checkout_attempt')),null)
    console.log(`PASS live review ${width}: summary, cancel, no order POST or draft storage`)
   }else console.log(`PASS live review ${width}: cart protection retained; no preview goods added`)
   await p.getByRole('button',{name:'登出',exact:true}).click()
   assert.deepEqual(writes,[]);assert.deepEqual(errors,[]);await ctx.close()
  }
 }finally{await browser.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
