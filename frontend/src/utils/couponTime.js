// Coupon API stores UTC LocalDateTime values without an offset.
export function toCouponInput(value) {
 const utc=new Date(/[zZ]$|[+-][0-9]{2}:[0-9]{2}$/.test(value)?value:value+'Z')
 if(!Number.isFinite(utc.getTime()))throw new Error('優惠券日期格式不正確')
 return new Date(utc.getTime()-utc.getTimezoneOffset()*60000).toISOString().slice(0,23)
}
export function couponInputToUtc(value) {
 if(!/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}(\.[0-9]{1,3})?)?$/.test(value))throw new Error('請填寫有效的本地日期時間')
 const date=new Date(value)
 if(!Number.isFinite(date.getTime()))throw new Error('請填寫有效的本地日期時間')
 const api=date.toISOString().slice(0,-1)
 const normalized=value.length===16?value+':00.000':value.includes('.')?value.split('.')[0]+'.'+value.split('.')[1].padEnd(3,'0'):value+'.000'
 // Reject nonexistent local times instead of silently rolling dates through a DST gap.
 if(toCouponInput(api)!==normalized)throw new Error('本地日期時間不存在，請重新選擇')
 return api
}
