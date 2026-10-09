<script setup>
import FeedbackNotice from '@/components/FeedbackNotice.vue'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useConfirmation} from '@/composables/useConfirmation'
import {onMounted,onBeforeUnmount,ref} from 'vue'
import {onBeforeRouteLeave} from 'vue-router'
import {memberApi} from '@/api'
import {useAuthStore} from '@/stores/auth'
const auth=useAuthStore(),addresses=ref([]),loading=ref(false),loaded=ref(false),error=ref(''),message=ref(''),deletingId=ref(null)
const confirmation=useConfirmation()
let sequence=0
async function load(){
 if(deletingId.value!==null)return
 const current=++sequence,epoch=auth.epoch;loading.value=true;loaded.value=false;error.value='';addresses.value=[]
 try{const data=await memberApi.listAddresses();if(current===sequence&&epoch===auth.epoch){addresses.value=data;loaded.value=true}}
 catch(e){if(current===sequence&&epoch===auth.epoch)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
onMounted(load);onBeforeUnmount(()=>{++sequence})
onBeforeRouteLeave(to=>!auth.isLoggedIn||to.name==='login'||deletingId.value===null)
async function remove(id){
 if(loading.value||deletingId.value!==null||confirmation.pending.value)return
 const address=addresses.value.find(a=>a.id===id)
 if(!address)return
 const current=sequence,epoch=auth.epoch
 const accepted=await confirmation.ask({title:'刪除此收件地址？',description:'刪除後無法復原；若為預設地址，不會自動指定其他地址。',detail:address.receiverName+' · '+address.phone+'\n'+(address.city||'')+(address.district||'')+(address.line1||''),confirmLabel:'確認刪除'})
 auth.syncFromStorage()
 if(!accepted||current!==sequence||epoch!==auth.epoch||!auth.isLoggedIn)return
 deletingId.value=id;error.value='';message.value=''
 try{
  await memberApi.deleteAddress(id);if(current!==sequence||epoch!==auth.epoch)return
  deletingId.value=null;await load();if(current+1===sequence&&epoch===auth.epoch)message.value='地址已刪除'
 }catch(e){if(current===sequence&&epoch===auth.epoch)error.value=e.message}
 finally{deletingId.value=null}
}
</script>
<template>
 <div class="page"><div class="container">
  <RouterLink class="btn btn-ghost" to="/account">返回會員中心</RouterLink>
  <h1 class="page-title mt-2">收件地址</h1>
  <p class="muted">可自行選擇預設地址；刪除預設地址後，不會自動指定另一筆。</p>
  <div class="row"><RouterLink class="btn btn-primary" :to="{name:'account-address-new'}">新增地址</RouterLink><button class="btn btn-ghost" :disabled="loading||deletingId!==null" @click="load">重新載入地址</button></div>
  <p v-if="loading" role="status">載入地址中…</p>
  <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
<FeedbackNotice :message="message" />
  <template v-if="loaded">
   <p v-if="!addresses.length" class="card address-card mt-3">尚未新增地址（可於結帳時直接填寫）</p>
   <article v-for="a in addresses" :key="a.id" class="card address-card mt-3">
    <h2>{{ a.receiverName }} · {{ a.phone }} <span v-if="a.isDefault" class="badge badge-completed">預設</span></h2>
    <p class="muted">{{ a.postalCode }} {{ a.city }}{{ a.district }}{{ a.line1 }}{{ a.line2 }}</p>
    <div class="row"><RouterLink class="btn btn-ghost" :to="{name:'account-address-edit',params:{id:a.id}}">編輯地址 #{{ a.id }}</RouterLink><button class="btn btn-danger" :aria-label="'刪除地址 #'+a.id" :disabled="deletingId!==null" @click="remove(a.id)">{{ deletingId===a.id?'刪除中…':'刪除' }}</button></div>
   </article>
  </template>
 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>.address-card{padding:24px;overflow-wrap:anywhere}.address-card h2{font-size:20px;margin:0}.row{flex-wrap:wrap}</style>
