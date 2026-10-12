const { chromium } = require('playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
const catalog = require('../../backend/demo/catalog-expansion.json')
const categories = [
  { id:1,name:'飼料',slug:'food' },{id:2,name:'保健品',slug:'health'},{id:3,name:'玩具',slug:'toys'},{id:4,name:'零食',slug:'treats'},{id:5,name:'貓砂',slug:'litter'},
  {id:6,name:'主食罐',slug:'wet-food',parentId:1},{id:7,name:'乾糧',slug:'dry-food',parentId:1}
]
for(const [index,category] of catalog.categories.entries())categories.push({...category,id:index+8,parentId:categories.find(item=>item.slug===category.parent)?.id||null})
const products=catalog.products.map((product,index)=>({...product,id:index+11,categoryId:categories.find(category=>category.slug===product.category).id,brand:'喵日常',basePrice:product.price,primaryImageUrl:'/product-images/'+product.image,status:'ON_SALE',isActive:true,skus:[{id:index+101,skuCode:'MD-'+index,specName:product.spec,price:product.price,stock:40,isActive:true}]}))
for(let index=0;index<10;index++)products.push({id:index+1,name:'日常主食 '+(index+1),slug:'original-'+index,categoryId:index%2?7:6,basePrice:100+index*50,brand:'日常選品',primaryImageUrl:'/product-images/seeds-wet-food.png',status:'ON_SALE',isActive:true})
function descendants(id){const ids=new Set([Number(id)]);for(let n=0;n<categories.length;n++)for(const category of categories)if(ids.has(category.parentId))ids.add(category.id);return ids}
function tree(parent=null){return categories.filter(category=>(category.parentId||null)===parent).map(category=>({...category,isActive:true,children:tree(category.id)}))}
async function main(){
 const {createServer}=await import('vite'),server=await createServer({mode:'storefront',server:{host:'127.0.0.1',port:5235,strictPort:true}})
 const output=path.resolve(__dirname,'../../.local/storefront-premium');fs.mkdirSync(output,{recursive:true})
 let browser;const errors=[]
 try{
  await server.listen();browser=await chromium.launch({channel:'msedge',headless:true})
  for(const width of [1440,390]){
   const context=await browser.newContext({viewport:{width,height:1000},reducedMotion:'reduce'}),calls=[]
   let failCategories=false
   await context.route(url=>!['127.0.0.1','localhost'].includes(url.hostname),r=>r.abort())
   await context.route(url=>url.pathname.startsWith('/api/'),async r=>{
    const url=new URL(r.request().url()),endpoint=url.pathname;calls.push({endpoint,method:r.request().method(),query:Object.fromEntries(url.searchParams)})
    const ok=data=>r.fulfill({json:{success:true,data}})
    if(endpoint==='/api/categories')return failCategories?r.fulfill({status:503,json:{success:false,error:{message:'分類暫時無法載入'}}}):ok(tree())
    if(endpoint.startsWith('/api/categories/slug/')){
     const category=categories.find(item=>item.slug===endpoint.split('/').pop());return category?ok(category):r.fulfill({status:404,json:{success:false,error:{message:'找不到分類'}}})
    }
    if(endpoint==='/api/products'){
     const id=url.searchParams.get('categoryId'),page=Number(url.searchParams.get('page')||0),size=Number(url.searchParams.get('size')||12)
     const ids=id?descendants(id):null,rows=products.filter(product=>!ids||ids.has(product.categoryId))
     return ok({content:rows.slice(page*size,(page+1)*size),page,size,totalPages:Math.ceil(rows.length/size),totalElements:rows.length,last:(page+1)*size>=rows.length})
    }
    if(endpoint==='/api/products/brands')return ok(['喵日常','日常選品'])
    if(endpoint.endsWith('/related'))return ok([])
    if(/^\/api\/products\/\d+$/.test(endpoint))return ok(products.find(product=>product.id===Number(endpoint.split('/').pop())))
    return ok([])
   })
   const p=await context.newPage();p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message))
   await p.goto('http://127.0.0.1:5235/');await p.locator('.product-card').first().waitFor()
   assert.equal(await p.locator('.campaign-card').count(),6);assert.equal(await p.locator('.premium-category').count(),10)
   assert.equal(await p.getByRole('button',{name:'播放活動輪播',exact:true}).count(),1)
   await p.getByRole('button',{name:'查看企劃：主子的餐桌日常',exact:true}).click();await p.getByRole('heading',{name:'每一餐，都值得好好準備。',exact:true}).waitFor()
   await p.getByRole('button',{name:'查看企劃：一起慢下來的家',exact:true}).click()
   assert.equal(calls.find(call=>call.endpoint==='/api/products').query.size,'48')
   for(const selector of ['.campaign-card:last-child','.premium-category:last-child','.brand-story','.care-journal','.premium-member-banner'])await p.locator(selector).scrollIntoViewIfNeeded()
   await p.evaluate(()=>window.scrollTo(0,0));await p.locator('.editorial-image img').evaluate(image=>image.decode())
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
   const broken=await p.locator('img').evaluateAll(images=>images.filter(image=>image.complete&&image.naturalWidth===0).map(image=>image.src));assert.deepEqual(broken,[])
   await p.screenshot({path:path.join(output,'home-'+width+'.png'),fullPage:true})
   await p.screenshot({path:path.join(output,'home-top-'+width+'.png')})
   const rail=p.locator('.product-rail').first();await rail.scrollIntoViewIfNeeded();await rail.focus();await rail.press('ArrowRight');assert.ok(await rail.evaluate(element=>element.scrollLeft>0))
   console.log('PASS home '+width+': six campaigns, ten categories, images, reduced motion, accessible rail, mobile overflow')
   await p.goto('http://127.0.0.1:5235/collections/quiet-home');await p.locator('.product-card').first().waitFor()
   assert.ok(await p.locator('.product-card').count()>=9)
   await p.getByRole('button',{name:'價格由低到高',exact:true}).click()
   const prices=await p.locator('.product-price').allTextContents();const values=prices.map(price=>Number(price.replace(/[^0-9]/g,'')));assert.deepEqual(values,[...values].sort((a,b)=>a-b))
   assert.equal(await p.locator('.collection-chips').first().getByRole('link').count(),3)
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
   await p.screenshot({path:path.join(output,'collection-'+width+'.png'),fullPage:true})
   await p.locator('.collection-chips').last().getByRole('link',{name:'午後舒眠計畫 ↗',exact:true}).click();await p.getByRole('heading',{name:'把午後，留給一場好眠。',exact:true}).waitFor();await p.locator('.product-card').first().waitFor()
   assert.equal(await p.locator('.product-card').count(),3)
   console.log('PASS collection '+width+': related products, price sorting, category links, route reuse')
   await p.goto('http://127.0.0.1:5235/categories');await p.locator('.directory-card').first().waitFor();assert.equal(await p.locator('.directory-card').count(),10)
   assert.equal(await p.locator('.directory-card').first().getByRole('link',{name:'乾糧 ↗',exact:true}).count(),1)
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
   await p.screenshot({path:path.join(output,'categories-'+width+'.png'),fullPage:true})
   failCategories=true;await p.goto('http://127.0.0.1:5235/categories');await p.getByRole('alert').waitFor();failCategories=false;await p.getByRole('button',{name:'重試載入',exact:true}).click();await p.locator('.directory-card').first().waitFor()
   console.log('PASS categories '+width+': hierarchy, responsive directory, failure and retry')
   await p.goto('http://127.0.0.1:5235/collections/not-a-collection');await p.getByRole('heading',{name:'找不到這份生活企劃'}).waitFor()
   assert.equal(calls.some(call=>call.method!=='GET'),false);await context.close()
  }
  assert.deepEqual(errors,[])
  assert.equal(products.length,34);assert.equal(categories.length,15)
  for(const product of catalog.products)assert.ok(fs.existsSync(path.resolve(__dirname,'../public/product-images/'+product.image)),product.image)
  console.log('PASS expanded catalog assets and read-only browsing; screenshots: '+output)
 }finally{await browser?.close();await server.close()}
}
main().catch(e=>{console.error(e);process.exitCode=1})
