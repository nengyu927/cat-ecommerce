import assert from 'node:assert/strict'
import {emptyPageReplacement} from '../src/utils/listQuery.js'
import {parseAdminListQuery as parse,adminReturnQuery} from '../src/utils/adminListQuery.js'
let count=0
for(const section of ['products','orders','categories','members','coupons']){
 assert.deepEqual(parse(section,{}),{page:0,size:20});count++
 for(const query of [{page:['0','1']},{size:['20','50']},{page:'01'},{page:'-1'},{page:'2147483648'},{size:'0'},{size:'101'},{size:null}]){assert.throws(()=>parse(section,query));count++}
}
for(const [section,query] of [
 ['products',{categoryId:'9223372036854775808'}],['products',{categoryId:['1','2']}],['products',{brand:['a','b']}],['products',{isActive:['true','false']}],['products',{status:'PAID'}],['products',{sort:'password,asc'}],['products',{sort:'name,asc,extra'}],
 ['orders',{from:'2026-02-30'}],['orders',{from:'0000-01-01'}],['orders',{to:'9999-12-31'}],['orders',{from:'2026-02-02',to:'2026-02-01'}],['orders',{memberId:'0'}],['orders',{orderNo:['a','b']}],['orders',{sort:'name,asc'}],
 ['members',{keyword:['a','b']}],['members',{keyword:'x'.repeat(201)}],['members',{role:'ROOT'}],['members',{status:['ACTIVE','DISABLED']}],['categories',{isActive:'1'}],['coupons',{sort:'id,asc'}]
]){assert.throws(()=>parse(section,query));count++}
assert.deepEqual(parse('products',{page:'1',size:'50',sort:'basePrice,asc',isActive:'false',categoryId:'9223372036854775807'}),{page:1,size:50,isActive:false,categoryId:'9223372036854775807',sort:'basePrice,asc'});count++
assert.equal(parse('orders',{from:'2024-02-29',to:'2024-02-29'}).from,'2024-02-29');count++
assert.deepEqual(adminReturnQuery('products',{size:'50',sort:'name,desc',page:'2',historyPage:'1'}),{page:'2',size:'50',sort:'name,desc'});count++
for(const section of ['inventory','movements']){
 for(const query of [{page:['0','1']},{size:'101'},{page:'1e2'},{page:'01'},{sort:'id,asc'}]){assert.throws(()=>parse(section,query));count++}
}
for(const [section,query] of [['inventory',{keyword:['a','b']}],['inventory',{outOfStockOnly:['true','false']}],['movements',{skuId:['1','2']}],['movements',{reason:['SKU_CREATED','ORDER_PLACED']}],['movements',{reason:'OTHER'}]]){assert.throws(()=>parse(section,query));count++}
assert.deepEqual(parse('inventory',{size:'50',outOfStockOnly:'false'}),{page:0,size:50,outOfStockOnly:false});count++
assert.deepEqual(parse('movements',{size:'50',skuId:'9223372036854775807',reason:'SKU_CREATED'}),{page:0,size:50,skuId:'9223372036854775807',reason:'SKU_CREATED'});count++
for(const [page,result,expected] of [
 [99,{content:[],totalPages:2},1],[99,{content:[],totalPages:0},0],[0,{content:[],totalPages:0},null],
 [1,{content:[],totalPages:2},null],[99,{content:[1],totalPages:2},null],[99,{content:[],totalPages:undefined},null]
]){assert.equal(emptyPageReplacement(page,result),expected);count++}
console.log('PASS Admin query: '+count+' assertions')
