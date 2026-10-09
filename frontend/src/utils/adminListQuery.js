import {queryText,queryInteger,queryChoice,queryId} from './listQuery.js'

export const adminListKeys={
 products:['keyword','status','categoryId','brand','isActive','page','size','sort','returnTo'],
 orders:['orderNo','status','memberId','from','to','page','size','sort'],
 categories:['keyword','isActive','page','size'],
 members:['keyword','status','role','page','size'],
 coupons:['page','size'],
 inventory:['keyword','outOfStockOnly','page','size'],
 movements:['skuId','reason','page','size','returnTo']
}
export function adminReturnQuery(section,query){
 return Object.fromEntries(adminListKeys[section].filter(key=>typeof query[key]==='string').map(key=>[key,query[key]]))
}
function dateValue(value){
 const text=queryText(value,'',10,'日期')
 if(!text)return ''
 const date=new Date(text+'T00:00:00Z')
 if(!/^\d{4}-\d{2}-\d{2}$/.test(text)||text<'0001-01-01'||text>'9998-12-31'||!Number.isFinite(date.getTime())||date.toISOString().slice(0,10)!==text)throw new Error('日期須為有效的年月日')
 return text
}
export function parseAdminListQuery(section,query){
 if(!adminListKeys[section])throw new Error('不支援此管理列表')
 if(query.sort!==undefined&&!['products','orders'].includes(section))throw new Error('此列表不支援自訂排序')
 const params={page:queryInteger(query.page,0),size:queryInteger(query.size,20,1,100,'每頁筆數')}
 const labels={keyword:'關鍵字',orderNo:'訂單編號',brand:'品牌',status:'狀態',role:'角色',isActive:'啟用狀態'}
 const text=(key,max=200)=>queryText(query[key],'',max,labels[key]||key)
 const choice=(key,values)=>queryChoice(query[key],'',['',...values],labels[key]||key)
 if(['products','categories','members','inventory'].includes(section))params.keyword=text('keyword')
 if(section==='inventory')params.outOfStockOnly=choice('outOfStockOnly',['true','false'])==='true'
 if(section==='movements'){
  if(query.skuId!==undefined)params.skuId=queryId(query.skuId)
  params.reason=choice('reason',['SKU_CREATED','ADMIN_ADJUSTMENT','ORDER_PLACED','ORDER_CANCELLED'])
 }
 if(['products','categories'].includes(section)){
  const active=choice('isActive',['true','false'])
  if(active!=='')params.isActive=active==='true'
 }
 if(section==='products'){
  params.status=choice('status',['ON_SALE','OFF_SHELF','SOLD_OUT']);params.brand=text('brand',100)
  if(query.categoryId!==undefined)params.categoryId=queryId(query.categoryId)
 }
 if(section==='members'){
  params.status=choice('status',['ACTIVE','DISABLED']);params.role=choice('role',['CUSTOMER','ADMIN'])
 }
 if(section==='orders'){
  params.orderNo=text('orderNo');params.status=choice('status',['PENDING','PAID','SHIPPED','COMPLETED','CANCELLED'])
  if(query.memberId!==undefined)params.memberId=queryId(query.memberId)
  params.from=dateValue(query.from);params.to=dateValue(query.to)
  if(params.from&&params.to&&params.from>params.to)throw new Error('開始日期不可晚於結束日期')
 }
 if(['products','orders'].includes(section)&&query.sort!==undefined){
  const fields=section==='products'?['createdAt','name','basePrice','status','id']:['createdAt','orderNo','totalAmount','status','id']
  params.sort=queryChoice(query.sort,'createdAt,desc',fields.flatMap(field=>['asc','desc'].map(dir=>field+','+dir)))
 }
 for(const key of Object.keys(params))if(params[key]==='')delete params[key]
 return params
}
