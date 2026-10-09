<script setup>
import ListPagination from '@/components/ListPagination.vue'
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import {parseAdminListQuery,adminReturnQuery} from '@/utils/adminListQuery'
import {emptyPageReplacement} from '@/utils/listQuery'
const route=useRoute(),router=useRouter()
const rows=ref([]),page=ref(0),totalPages=ref(0),loading=ref(false),error=ref('')
let sequence=0
async function load(){
 const current=++sequence;loading.value=true;error.value='';rows.value=[];totalPages.value=0
 try{
  const params=parseAdminListQuery('coupons',route.query)
  page.value=params.page
  const data=await adminApi.coupons(params)
  if(current!==sequence)return
  const replacement=emptyPageReplacement(params.page,data)
  if(replacement!==null){await router.replace({name:route.name,query:{...route.query,page:replacement},hash:route.hash});return}
  rows.value=data.content;totalPages.value=data.totalPages
 }catch(e){if(current===sequence){totalPages.value=0;error.value=e.message}}
 finally{if(current===sequence)loading.value=false}
}
function edit(row){router.push({name:row?'admin-coupon-edit':'admin-coupon-new',params:row?{id:row.id}:{},query:adminReturnQuery('coupons',route.query)})}
watch(()=>route.fullPath,load,{immediate:true})
onBeforeUnmount(()=>{++sequence})
</script>
<template>
 <div class="page"><div class="container">
  <h1 class="page-title">優惠券管理</h1><p class="page-sub">固定金額折抵，每位會員同券限用一次；取消未出貨訂單可重新使用。</p>
  <p v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost" @click="router.push({name:'admin-coupons'})">清除頁碼</button></p>
  <button class="btn btn-primary" @click="edit(null)">新增優惠券</button>
  <p v-if="loading" role="status">載入中…</p>
  <div v-if="!error" class="card mt-3" style="overflow-x:auto"><table class="table">
   <thead><tr><th>優惠碼</th><th>折抵／門檻</th><th>有效期間</th><th>使用上限</th><th>狀態</th><th>操作</th></tr></thead>
   <tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.code }}</td><td>NT$ {{ row.discountAmount }}／{{ row.minimumAmount }}</td><td>{{ new Date(row.startsAt+'Z').toLocaleString('zh-TW') }} ～ {{ new Date(row.endsAt+'Z').toLocaleString('zh-TW') }}</td><td>{{ row.maxUses }}</td><td>{{ row.active?'啟用':'停用' }}</td><td><button class="btn btn-ghost btn-sm" @click="edit(row)">編輯</button></td></tr><tr v-if="!loading&&!rows.length"><td colspan="6">尚無優惠券</td></tr></tbody>
  </table></div>
  <ListPagination v-if="!error" :page="page" :total-pages="totalPages" :loading="loading" /><button class="btn btn-ghost mt-2" :disabled="loading" @click="load">重新載入</button>
 </div></div>
</template>
<style scoped>.row{flex-wrap:wrap}</style>
