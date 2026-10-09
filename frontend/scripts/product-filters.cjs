const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
async function main() {
 const { createServer } = await import('vite')
 const server = await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5180,strictPort:true}})
 let browser; let passed=0
 const errors=[]
 try {
  await server.listen(); browser=await chromium.launch({channel:'msedge',headless:true})
  async function scenario(name,test) {
   const context=await browser.newContext(), page=await context.newPage(), state={requests:[],failBrands:false,slow:false}
   page.setDefaultTimeout(10000)
   page.on('pageerror',error=>errors.push(error.message))
   await page.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),route=>route.abort())
   await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
    const url=new URL(route.request().url()), path=url.pathname
    const success=data=>route.fulfill({json:{success:true,code:200,data}})
    if(path==='/api/products/brands') return state.failBrands ? route.fulfill({status:503,json:{success:false,message:'品牌暫時無法載入'}}) : success(['Royal','Plus'])
    if(path==='/api/categories') return success([{id:1,name:'Food',slug:'food'}])
    if(path==='/api/categories/slug/food') return success({id:1,name:'Food',slug:'food'})
    if(path==='/api/products') {
     state.requests.push(Object.fromEntries(url.searchParams)); const brand=url.searchParams.get('brand')
     if(state.slow && brand==='Royal') await delay(350)
     const pageNumber=Number(url.searchParams.get('page')||0)
     return success({content:[{id:1,name:brand==='Royal'?'Royal result':brand==='Plus'?'Plus result':'All result',brand:brand||'Royal',basePrice:100}],page:pageNumber,size:12,totalElements:25,totalPages:3,last:pageNumber===2})
    }
    return success([])
   })
   try { await test(page,state); passed++; console.log('PASS: '+name) }
   catch (error) { console.error('Scenario: '+name+'\n'+await page.locator('body').innerText()); throw error }
   finally { await context.close() }
  }
  const base='http://127.0.0.1:5180'
  await scenario('Combined filters, pagination, reload and clear preserve applied URL state',async(page,state)=>{
   await page.goto(base+'/products?brand=Royal&minPrice=100&maxPrice=200')
   await page.locator('.product-name').filter({hasText:'Royal result'}).waitFor()
   await page.getByRole('button',{name:'下一頁',exact:true}).click(); await page.waitForURL(/page=1/)
   await page.reload(); await page.locator('.product-name').filter({hasText:'Royal result'}).waitFor()
   assert.equal(await page.getByLabel('最低價格',{exact:true}).inputValue(),'100')
   assert.equal(state.requests.at(-1).brand,'Royal'); assert.equal(state.requests.at(-1).page,'1')
   await page.getByRole('button',{name:'清除條件',exact:true}).click(); await page.waitForURL(url=>!url.searchParams.has('brand'))
   await page.locator('.product-name').filter({hasText:'All result'}).waitFor()
   assert.equal(state.requests.at(-1).minPrice,undefined)
  })
  await scenario('Invalid price bounds block requests and remain editable',async(page,state)=>{
   await page.goto(base+'/products'); await page.locator('.product-name').waitFor()
   const count=state.requests.length
   await page.getByLabel('最低價格',{exact:true}).fill('200'); await page.getByLabel('最高價格',{exact:true}).fill('100')
   await page.getByRole('button',{name:'搜尋',exact:true}).click()
   await page.getByRole('alert').filter({hasText:'最低價格不可高於最高價格'}).waitFor()
   assert.equal(state.requests.length,count)
   await page.getByLabel('最低價格',{exact:true}).fill('0'); await page.getByRole('button',{name:'搜尋',exact:true}).click()
   await page.waitForURL(/minPrice=0/); await page.locator('.product-name').waitFor(); assert.equal(state.requests.at(-1).minPrice,'0')
  })
  await scenario('Category page filters retain the selected category',async(page,state)=>{
   await page.goto(base+'/category/food'); await page.getByRole('heading',{name:'Food',exact:true}).waitFor()
   await page.getByLabel('商品品牌',{exact:true}).selectOption('Royal')
   await page.waitForURL(url=>url.pathname==='/category/food' && url.searchParams.get('brand')==='Royal')
   await page.locator('.product-name').filter({hasText:'Royal result'}).waitFor()
   assert.equal(state.requests.at(-1).categoryId,'1')
   await page.getByRole('heading',{name:'Food',exact:true}).waitFor()
  })
  await scenario('Brand menu failure is visible and retry recovers',async(page,state)=>{
   state.failBrands=true; await page.goto(base+'/products'); await page.getByRole('button',{name:'重試選單',exact:true}).waitFor()
   state.failBrands=false; await page.getByRole('button',{name:'重試選單',exact:true}).click()
   await page.getByLabel('商品品牌',{exact:true}).selectOption('Royal'); await page.locator('.product-name').filter({hasText:'Royal result'}).waitFor()
  })
  await scenario('Slow old query cannot overwrite a newer filter result',async(page,state)=>{
   state.slow=true; await page.goto(base+'/products'); await page.locator('.product-name').waitFor()
   await page.getByLabel('商品品牌',{exact:true}).selectOption('Royal')
   await page.waitForURL(/brand=Royal/); await page.getByLabel('商品品牌',{exact:true}).selectOption('Plus')
   await page.locator('.product-name').filter({hasText:'Plus result'}).waitFor(); await delay(500)
   assert.equal(await page.locator('.product-name').textContent(),'Plus result')
  })
  await scenario('Stock checkbox combines filters, persists across paging/reload and clears',async(page,state)=>{
   await page.goto(base+'/products?brand=Royal&minPrice=100&maxPrice=200&page=1')
   await page.locator('.product-name').waitFor(); await page.getByRole('checkbox',{name:'只看現貨',exact:true}).check()
   await page.waitForURL(url=>url.searchParams.get('inStock')==='true' && !url.searchParams.has('page'))
   await page.locator('.product-name').waitFor(); assert.equal(state.requests.at(-1).inStock,'true')
   assert.equal(state.requests.at(-1).brand,'Royal'); assert.equal(state.requests.at(-1).minPrice,'100')
   await page.getByRole('button',{name:'下一頁',exact:true}).click(); await page.waitForURL(/page=1/)
   await page.reload(); await page.locator('.product-name').waitFor(); assert.equal(await page.getByRole('checkbox',{name:'只看現貨',exact:true}).isChecked(),true)
   await page.getByRole('button',{name:'清除條件',exact:true}).click(); await page.waitForURL(url=>!url.searchParams.has('inStock'))
   await page.locator('.product-name').waitFor(); assert.equal(state.requests.at(-1).inStock,undefined)
  })
  await scenario('Malformed stock URL is visible and can be cleared',async(page,state)=>{
   await page.goto(base+'/products?inStock=garbage'); await page.getByRole('alert').filter({hasText:'現貨條件須為 true 或 false'}).waitFor()
   assert.equal(state.requests.length,0); await page.getByRole('button',{name:'清除條件',exact:true}).click()
   await page.locator('.product-name').waitFor()
  })
  assert.deepEqual(errors,[]); assert.equal(passed,7)
 } finally { await browser?.close(); await server.close() }
}
main().catch(error=>{console.error(error);process.exitCode=1})
