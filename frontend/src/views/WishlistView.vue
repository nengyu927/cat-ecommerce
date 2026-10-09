<script setup>
import ListPagination from '@/components/ListPagination.vue'
import {ref,watch,onBeforeUnmount} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {wishlistApi} from '@/api'
import {queryInteger} from '@/utils/listQuery'
import {useWishlistStore} from '@/stores/wishlist'
import ProductCard from '@/components/ProductCard.vue'
const wishlist=useWishlistStore()
const route=useRoute(),router=useRouter(),rows=ref([]),loading=ref(false),error=ref(''),page=ref(0),totalPages=ref(0),total=ref(0)
let sequence=0

async function load(){
 const current=++sequence;loading.value=true;error.value='';rows.value=[];totalPages.value=0;total.value=0
 try{
  page.value=queryInteger(route.query.page,0)
  const size=queryInteger(route.query.size,12,1,100,'每頁筆數')
  await wishlist.fetch();if(current!==sequence)return
  const data=await wishlistApi.list({page:page.value,size})
  if(current!==sequence)return
  if(page.value>0&&!data.content.length){await router.replace({name:route.name,query:{...route.query,page:Math.max(0,data.totalPages-1)}});return}
  rows.value=data.content;totalPages.value=data.totalPages;total.value=data.totalElements
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
function clearQuery(){router.replace({name:route.name})}
watch(()=>route.fullPath,load,{immediate:true})
onBeforeUnmount(()=>{++sequence})
</script>
<template>
  <div class="page"><div class="container">
    <p class="eyebrow">MY DAILY PICKS</p><h1 class="page-title">我的收藏</h1>
    <p class="page-sub">為主子留住喜歡的好物 · {{ total }} 件收藏（上限 500 件）</p>
    <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">重試載入</button> <button class="btn btn-ghost btn-sm" @click="clearQuery">清除查詢</button></div>
    <div v-if="loading" class="spinner">載入中…</div>
    <div v-else-if="!error && !rows.length" class="empty card"><p>還沒有收藏商品，先去逛逛吧。</p><RouterLink to="/products" class="btn btn-primary">探索商品</RouterLink></div>
    <div v-else-if="!error" class="grid grid-products"><ProductCard v-for="product in rows" :key="product.id" :product="product" @wishlist-change="load" /></div>
  <ListPagination v-if="!error" :page="page" :total-pages="totalPages" :loading="loading" :default-size="12" />
  </div></div>
</template>
