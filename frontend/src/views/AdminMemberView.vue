<script setup>
import ListPagination from '@/components/ListPagination.vue'
import { ref,watch,onBeforeUnmount } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import {parseAdminListQuery} from '@/utils/adminListQuery'
import {queryId,emptyPageReplacement} from '@/utils/listQuery'
const route=useRoute(),router=useRouter()
const keyword=ref(''),status=ref(''),role=ref(''),result=ref(null),loading=ref(false),error=ref('')
let sequence=0
async function load(){
 const current=++sequence;loading.value=true;error.value='';result.value=null
 try{
  const params=parseAdminListQuery('members',route.query)
  keyword.value=params.keyword||'';status.value=params.status||'';role.value=params.role||''
  if(route.query.memberId!==undefined){
   const query={...route.query};const id=query.memberId;delete query.memberId
   queryId(id)
   await router.replace({name:'admin-member-history',params:{id},query,hash:route.hash});return
  }
  const data=await adminApi.members(params)
  if(current!==sequence)return
  const replacement=emptyPageReplacement(params.page,data)
  if(replacement!==null){await router.replace({name:route.name,query:{...route.query,page:replacement},hash:route.hash});return}
  if(current===sequence)result.value=data
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
function search(){
 const to={name:'admin-members',query:{...(route.query.size!==undefined?{size:route.query.size}:{}),...(keyword.value.trim()?{keyword:keyword.value.trim()}:{}),...(status.value?{status:status.value}:{}),...(role.value?{role:role.value}:{})}}
 if(router.resolve(to).fullPath===route.fullPath)load();else router.push(to)
}
function clear(){keyword.value='';status.value='';role.value='';if(Object.keys(route.query).length)router.push({name:'admin-members'});else load()}
watch(()=>route.fullPath,load,{immediate:true})
onBeforeUnmount(()=>{++sequence})
</script>
<template>
 <div class="page"><div class="container">
  <p class="eyebrow">MEMBER OPERATIONS</p><h1 class="page-title">會員管理</h1>
  <p class="page-sub">搜尋會員、停用或恢復一般會員；管理員帳號受保護，不提供密碼與角色修改。</p>
  <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" @click="load">重試載入</button></div>
  <form class="card row member-filters" @submit.prevent="search">
   <input v-model="keyword" class="input" aria-label="會員搜尋" placeholder="姓名、電子郵件或電話" maxlength="200" />
   <select v-model="status" class="input" aria-label="會員狀態"><option value="">全部狀態</option><option value="ACTIVE">啟用</option><option value="DISABLED">停用</option></select>
   <select v-model="role" class="input" aria-label="會員角色"><option value="">全部角色</option><option value="CUSTOMER">一般會員</option><option value="ADMIN">管理員</option></select>
   <button class="btn btn-primary">搜尋</button><button class="btn btn-ghost" type="button" @click="clear">清除條件</button>
  </form>
  <div v-if="loading" class="spinner">載入中…</div>
  <template v-else-if="!error && result">
   <p class="page-sub">符合條件共 {{ result.totalElements }} 位會員</p>
   <div v-if="!result.content.length" class="card empty">目前沒有符合條件的會員</div>
   <div v-else class="card" style="overflow-x:auto"><table class="table"><thead><tr><th>會員</th><th>聯絡資訊</th><th>角色／狀態</th><th>註冊時間</th><th>操作</th></tr></thead><tbody>
    <tr v-for="item in result.content" :key="item.id"><td>{{ item.name }}<div class="muted">#{{ item.id }}</div></td><td>{{ item.email }}<div class="muted">{{ item.phone || '未提供電話' }}</div></td><td>{{ item.role==='ADMIN'?'管理員':'一般會員' }} · {{ item.status==='ACTIVE'?'啟用':'停用' }}</td><td>{{ item.createdAt?.replace('T',' ') }}</td><td>
     <span v-if="item.role==='ADMIN'" class="muted">管理員帳號受保護</span>
     <RouterLink class="btn btn-ghost btn-sm" :to="{name:'admin-member-detail',params:{id:item.id},query:route.query}">會員詳情 #{{ item.id }}</RouterLink>
     <RouterLink class="btn btn-ghost btn-sm" :to="{name:'admin-member-history',params:{id:item.id},query:route.query}">操作紀錄 #{{ item.id }}</RouterLink>
    </td></tr>
   </tbody></table></div>
  <ListPagination :page="result.page" :total-pages="result.totalPages" :loading="loading" />
  </template>
 </div></div>
</template>
<style scoped>
.member-filters{padding:16px;flex-wrap:wrap;margin-bottom:24px}
.member-filters .input{max-width:250px}
.row{flex-wrap:wrap}
</style>
