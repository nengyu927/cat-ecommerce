<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import ListPagination from '@/components/ListPagination.vue'
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import { adminPath } from '@/config/surface'
import {parseAdminListQuery} from '@/utils/adminListQuery'
import {emptyPageReplacement} from '@/utils/listQuery'
const route=useRoute(), router=useRouter()
const keyword=ref(''), outOnly=ref(false), loading=ref(false), error=ref(''), result=ref(null)
let version=0
async function load() {
  const current=++version; loading.value=true; error.value='';result.value=null
  try {
    const params=parseAdminListQuery('inventory',route.query)
    keyword.value=params.keyword||'';outOnly.value=params.outOfStockOnly
    const data=await adminApi.lowStock(params)
    if(current!==version)return
    const replacement=emptyPageReplacement(params.page,data.items)
    if(replacement!==null){await router.replace({name:route.name,query:{...route.query,page:replacement},hash:route.hash});return}
    if(current===version) result.value=data
  } catch(e) { if(current===version) error.value=e.message }
  finally { if(current===version) loading.value=false }
}
function search() {
  const target={name:'admin-inventory',query:{...(route.query.size!==undefined?{size:route.query.size}:{}),...(keyword.value.trim()?{keyword:keyword.value.trim()}:{}),...(outOnly.value?{outOfStockOnly:'true'}:{})}}
  if(router.resolve(target).fullPath===route.fullPath) load()
  else router.push(target)
}
function clear() { keyword.value=''; outOnly.value=false; if(Object.keys(route.query).length)router.push({name:'admin-inventory'});else load() }
watch(()=>route.fullPath,load,{immediate:true})
onBeforeUnmount(()=>{++version})
function fillDemo() { if (loading.value) return; keyword.value = result.value?.items?.content?.[0]?.skuCode || '雞肉'; outOnly.value = result.value?.items?.content?.[0]?.stock === 0 }
</script>
<template>
 <div class="page"><div class="container">
  <p class="eyebrow">INVENTORY ALERTS</p><h1 class="page-title">低庫存清單</h1>
  <p class="page-sub">啟用商品與規格，庫存小於或等於安全庫存（含下架商品）。現有庫存不會因查詢而變動。</p>
  <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">重試載入</button></div>
  <form class="card row" style="padding:16px;flex-wrap:wrap;margin-bottom:24px" @submit.prevent="search">
<DemoFillButton :disabled="loading" @fill="fillDemo" label="一鍵填入搜尋條件" />
   <input v-model="keyword" class="input" aria-label="低庫存搜尋" placeholder="商品名稱或 SKU 編號" maxlength="200" style="max-width:320px" />
   <label class="stock-filter"><input v-model="outOnly" type="checkbox" /> 只看缺貨</label>
   <button class="btn btn-primary" type="submit">搜尋</button><button class="btn btn-ghost" type="button" @click="clear">清除條件</button>
  </form>
  <div v-if="loading" class="spinner">載入中…</div>
  <template v-else-if="!error && result">
   <p class="page-sub">符合搜尋的全部預警規格：{{ result.lowStockCount }} 筆 · 其中缺貨：{{ result.outOfStockCount }} 筆 · 目前列表：{{ result.items.totalElements }} 筆</p>
   <div v-if="!result.items.content.length" class="empty card">目前沒有符合條件的低庫存規格</div>
   <div v-else class="card" style="overflow-x:auto"><table class="table"><thead><tr><th>商品／分類</th><th>規格／SKU</th><th>狀態</th><th>庫存／安全庫存</th><th>補至安全庫存差額</th><th>操作</th></tr></thead>
<tbody><tr v-for="item in result.items.content" :key="item.skuId"><td>{{ item.productName }}<div class="muted">#{{ item.productId }} · {{ item.categoryName }} · {{ item.productStatus }}</div></td><td>{{ item.specName }}<div class="muted">{{ item.skuCode }}</div></td><td>{{ item.stock===0?'缺貨':'低庫存' }}</td><td>{{ item.stock }} / {{ item.safetyStock }}</td><td>{{ item.shortage }}</td><td><RouterLink class="btn btn-ghost btn-sm" :to="{path:adminPath('products'),query:{keyword:item.productName,returnTo:route.fullPath}}">前往商品管理</RouterLink> <RouterLink class="btn btn-ghost btn-sm" :to="{path:adminPath('movements'),query:{skuId:item.skuId,returnTo:route.fullPath}}">異動紀錄</RouterLink></td></tr></tbody>
   </table></div>
  <ListPagination :page="result.items.page" :total-pages="result.items.totalPages" :loading="loading" />
  </template>
 </div></div>
</template>
