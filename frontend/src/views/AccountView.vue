<script setup>
import {onMounted,ref} from 'vue'
import {useAuthStore} from '@/stores/auth'
const auth=useAuthStore(),loading=ref(false),error=ref('')
async function load(){if(loading.value)return;loading.value=true;error.value='';try{await auth.refreshProfile()}catch(e){error.value=e.message}finally{loading.value=false}}
onMounted(load)
</script>
<template>
  <div class="page">
    <div class="container">
      <h1 class="page-title">會員中心</h1>
      <p class="page-sub">{{ auth.member?.email }}</p>

      <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost" :disabled="loading" @click="load">重試載入</button></div>
      <div v-if="loading" class="spinner" role="status">載入會員資料中…</div>


      <nav class="account-links" aria-label="會員功能">
       <RouterLink class="card account-link" to="/account/profile"><h2>個人資料</h2><p>修改姓名與聯絡電話</p><span>編輯資料 →</span></RouterLink>
       <RouterLink class="card account-link" to="/account/password"><h2>變更密碼</h2><p>更新密碼後，所有裝置需重新登入</p><span>管理密碼 →</span></RouterLink>
       <RouterLink class="card account-link" to="/orders"><h2>我的訂單</h2><p>查看訂單與出貨紀錄</p><span>查看訂單 →</span></RouterLink>
       <RouterLink class="card account-link" to="/wishlist"><h2>我的收藏</h2><p>回到喜愛的商品</p><span>查看收藏 →</span></RouterLink>
       <RouterLink class="card account-link" to="/account/addresses"><h2>收件地址</h2><p>管理常用收件地址與預設地址</p><span>管理地址 →</span></RouterLink>
      </nav>

    </div>
  </div>
</template>
<style scoped>
.account-links{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.account-link{padding:24px;overflow-wrap:anywhere}.account-link h2{font-size:22px;margin:0}.account-link p{color:var(--muted)}.account-link span{font-weight:600}
@media(max-width:650px){.account-links{grid-template-columns:minmax(0,1fr)}}
</style>
