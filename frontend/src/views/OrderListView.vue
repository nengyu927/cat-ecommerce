<script setup>
import ListPagination from '@/components/ListPagination.vue'
import {ref,watch,onBeforeUnmount} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {orderApi} from '@/api'
import {queryInteger} from '@/utils/listQuery'

const route=useRoute(),router=useRouter(),orders=ref([]),loading=ref(false),error=ref(''),page=ref(0),totalPages=ref(0),total=ref(0)
let sequence=0
const statusText={PENDING:'待處理',PAID:'已付款',SHIPPED:'已出貨',COMPLETED:'已完成',CANCELLED:'已取消'}
async function load(){
 const current=++sequence;loading.value=true;error.value='';orders.value=[];totalPages.value=0;total.value=0
 try{
  page.value=queryInteger(route.query.page,0)
  const size=queryInteger(route.query.size,20,1,100,'每頁筆數')
  
  const data=await orderApi.list({page:page.value,size})
  if(current!==sequence)return
  if(page.value>0&&!data.content.length){await router.replace({name:route.name,query:{...route.query,page:Math.max(0,data.totalPages-1)}});return}
  orders.value=data.content;totalPages.value=data.totalPages;total.value=data.totalElements
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
function clearQuery(){router.replace({name:route.name})}
watch(()=>route.fullPath,load,{immediate:true})
onBeforeUnmount(()=>{++sequence})
</script>
<template>
  <div class="page">
    <div class="container">
      <h1 class="page-title">我的訂單</h1>
      <p class="page-sub">查看歷史訂單與狀態</p>

      <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost" @click="load">重試載入</button><button class="btn btn-ghost" @click="clearQuery">清除查詢</button></div>
      <div v-if="loading" class="spinner">載入中…</div>
      <div v-else-if="!error && !orders.length" class="empty card">
        <span class="emoji">📦</span>
        還沒有任何訂單
        <div class="mt-2"><RouterLink to="/products" class="btn btn-primary">去購物</RouterLink></div>
      </div>
      <div v-else-if="!error" class="card" style="overflow-x:auto">
        <table class="table">
          <thead>
            <tr><th>訂單編號</th><th>成立時間</th><th>狀態</th><th>金額</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="o in orders" :key="o.id">
              <td style="font-family: monospace;">{{ o.orderNo }}</td>
              <td class="muted">{{ new Date(o.createdAt).toLocaleString('zh-TW') }}</td>
              <td><span class="badge" :class="`badge-${o.status.toLowerCase()}`">{{ statusText[o.status] || o.status }}</span></td>
              <td style="font-weight: 700;">NT$ {{ Number(o.totalAmount).toLocaleString() }}</td>
              <td><RouterLink :to="{name:'order-detail',params:{id:o.id},query:{returnTo:route.fullPath}}" class="btn btn-ghost btn-sm">詳情</RouterLink></td>
            </tr>
          </tbody>
        </table>
      </div>
  <ListPagination v-if="!error" :page="page" :total-pages="totalPages" :loading="loading" />
    </div>
  </div>
</template>
