<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import ListPagination from '@/components/ListPagination.vue'
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import {parseAdminListQuery} from '@/utils/adminListQuery'
import {safeListReturn,emptyPageReplacement} from '@/utils/listQuery'
import { useRoute, useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
const route=useRoute(), router=useRouter()
const returnTarget=computed(()=>safeListReturn(router,route.query.returnTo,['admin-inventory'],{name:'admin-inventory'}))
const skuId=ref(''), reason=ref(''), loading=ref(false), error=ref(''), result=ref(null)
const reasons={SKU_CREATED:'建立規格',ADMIN_ADJUSTMENT:'人工調整',ORDER_PLACED:'下單扣庫存',ORDER_CANCELLED:'取消訂單還庫存'}
let version=0
async function load() {
 const current=++version; loading.value=true; error.value='';result.value=null
 try {
  const params=parseAdminListQuery('movements',route.query)
  skuId.value=params.skuId||'';reason.value=params.reason||''
  const data=await adminApi.movements(params)
  if(current!==version)return
  const replacement=emptyPageReplacement(params.page,data)
  if(replacement!==null){await router.replace({name:route.name,query:{...route.query,page:replacement},hash:route.hash});return}
  if(current===version) result.value=data
 } catch(e) { if(current===version) error.value=e.message }
 finally { if(current===version) loading.value=false }
}
function search() {
 const target={name:'admin-movements',query:{...(route.query.size!==undefined?{size:route.query.size}:{}),...(route.query.returnTo!==undefined?{returnTo:route.query.returnTo}:{}),...(skuId.value.trim()?{skuId:skuId.value.trim()}:{}),...(reason.value?{reason:reason.value}:{})}}
 if(router.resolve(target).fullPath===route.fullPath) load(); else router.push(target)
}
function clear() {skuId.value='';reason.value='';const target={name:'admin-movements',query:typeof route.query.returnTo==='string'?{returnTo:route.query.returnTo}:{}};if(router.resolve(target).fullPath===route.fullPath)load();else router.push(target)}
watch(()=>route.fullPath,load,{immediate:true})
onBeforeUnmount(()=>{++version})
function fillDemo() { if (loading.value) return; const item = result.value?.content?.[0]; skuId.value = item?.skuId ? String(item.skuId) : ''; reason.value = item?.reason || 'ADMIN_ADJUSTMENT' }
</script>
<template>
 <div class="page"><div class="container">
  <RouterLink v-if="route.query.returnTo!==undefined" class="btn btn-ghost" :to="returnTarget">返回低庫存清單</RouterLink>
  <p class="eyebrow">STOCK MOVEMENTS</p><h1 class="page-title">庫存異動紀錄</h1>
  <p class="page-sub">由新到舊顯示實際數量變更；功能啟用前的庫存不補成歷史紀錄。</p>
  <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">重試載入</button></div>
  <form class="card row" style="padding:16px;flex-wrap:wrap;margin-bottom:24px" @submit.prevent="search">
<DemoFillButton :disabled="loading" @fill="fillDemo" label="一鍵填入搜尋條件" />
   <input v-model="skuId" class="input" aria-label="SKU ID" placeholder="SKU ID" inputmode="numeric" maxlength="19" style="max-width:220px" />
   <select v-model="reason" class="input" aria-label="異動原因" style="max-width:240px"><option value="">全部原因</option><option v-for="(label,key) in reasons" :key="key" :value="key">{{ label }}</option></select>
   <button class="btn btn-primary" type="submit">搜尋</button><button class="btn btn-ghost" type="button" @click="clear">清除條件</button>
  </form>
  <div v-if="loading" class="spinner">載入中…</div>
  <template v-else-if="!error && result">
   <p class="page-sub">共 {{ result.totalElements }} 筆異動</p>
   <div v-if="!result.content.length" class="empty card">目前沒有符合條件的庫存異動紀錄</div>
   <div v-else class="card" style="overflow-x:auto"><table class="table"><thead><tr><th>時間</th><th>商品／SKU</th><th>原因</th><th>異動前 → 後</th><th>數量變化</th><th>操作者／訂單</th></tr></thead><tbody>
    <tr v-for="item in result.content" :key="item.id"><td>{{ item.createdAt?.replace('T',' ') }}</td><td>{{ item.productName }}<div class="muted">{{ item.skuCode }} · SKU #{{ item.skuId }}</div></td><td>{{ reasons[item.reason] || item.reason }}</td><td>{{ item.beforeStock }} → {{ item.afterStock }}</td><td>{{ item.quantityDelta>0?'+':'' }}{{ item.quantityDelta }}</td><td>{{ item.actorMemberId ? `會員 #${item.actorMemberId}` : '操作者未提供' }}<div class="muted">{{ item.orderNo || '無關聯訂單' }}</div></td></tr>
   </tbody></table></div>
  <ListPagination :page="result.page" :total-pages="result.totalPages" :loading="loading" />
  </template>
 </div></div>
</template>
