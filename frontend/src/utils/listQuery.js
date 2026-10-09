export function queryText(value,fallback='',max=200,label='查詢條件'){
 if(value===undefined)return fallback
 if(typeof value!=='string'||value.length>max)throw new Error(label+'格式不正確')
 return value
}
export function emptyPageReplacement(page,result){
 if(page>0&&result.content.length===0&&Number.isInteger(result.totalPages)&&result.totalPages>=0&&page>=result.totalPages)
  return Math.max(0,result.totalPages-1)
 return null
}
export function queryInteger(value,fallback,min=0,max=2147483647,label='頁碼'){
 if(value===undefined)return fallback
 const text=queryText(value,'',16,label)
 if(!/^(0|[1-9][0-9]*)$/.test(text)||!Number.isSafeInteger(Number(text))||Number(text)<min||Number(text)>max)throw new Error(label+'須為有效範圍內的整數')
 return Number(text)
}
export function queryChoice(value,fallback,choices,label='排序'){
 const text=queryText(value,fallback,200,label)
 if(!choices.includes(text))throw new Error(label+'不支援此值')
 return text
}
export function queryId(value){
 if(value===undefined)return null
 const text=queryText(value,'',19,'分類編號')
 if(!/^[1-9][0-9]*$/.test(text)||BigInt(text)>9223372036854775807n)throw new Error('分類編號須為正整數')
 return text
}
export function safeListReturn(router,value,names,fallback){
 if(typeof value!=='string'||value.length>2000||!value.startsWith('/')||value.startsWith('//')||/[\\\\\r\n]/.test(value))return fallback
 const target=router.resolve(value)
 return names.includes(target.name)?{name:target.name,params:target.params,query:target.query,hash:target.hash}:fallback
}
