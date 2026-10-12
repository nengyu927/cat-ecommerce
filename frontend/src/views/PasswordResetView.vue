<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import { demoPassword } from '@/utils/demoFill'
import { demoAccounts } from '@/config/demo'
import { isAdminApp } from '@/config/surface'
import FeedbackNotice from '@/components/FeedbackNotice.vue'
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { loginRouteName } from '@/config/surface'
import { demoLoginEnabled } from '@/config/demo'
import { passwordResetApi } from '@/api/passwordReset'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const resetting=computed(()=>route.name==='reset-password')
const email=ref(''),password=ref(''),confirm=ref(''),error=ref(''),message=ref(''),loading=ref(false)
// Fragment is not sent to the web server or as HTTP Referer. Remove it from browser history immediately.
const token=ref('')
let version=0
watch([()=>route.name,()=>route.hash],([name,hash],previous)=>{
 if(name!==previous?.[0] || (resetting.value && hash)){
  ++version;error.value='';message.value='';loading.value=false;password.value='';confirm.value=''
  token.value=resetting.value ? new URLSearchParams(hash.slice(1)).get('token') || '' : ''
 }
 if(resetting.value && hash)router.replace({name:'reset-password',hash:''})
},{immediate:true})
onBeforeUnmount(()=>{++version;token.value='';password.value='';confirm.value=''})
async function submit(){
 if(loading.value)return
 error.value='';message.value=''
 if(resetting.value && !/^[A-Za-z0-9_-]{43}$/.test(token.value)){error.value='重設連結缺少有效憑證，請重新申請並開啟完整連結';return}
 if(resetting.value && (password.value.length<8 || password.value.length>64)){error.value='新密碼長度需介於 8-64 字元';return}
 if(resetting.value && password.value!==confirm.value){error.value='兩次輸入的密碼不一致';return}
 const current=++version,epoch=auth.epoch
 loading.value=true
 try{
  if(resetting.value){
   await passwordResetApi.reset(token.value,password.value)
   if(current!==version)return
   auth.syncFromStorage()
   if(auth.epoch===epoch)auth.clearSession('password-changed')
   token.value='';password.value='';confirm.value=''
   message.value='密碼已重設，原有登入已失效，請使用新密碼重新登入。'
  }else{
   const notice=await passwordResetApi.request(email.value.trim())
   if(current===version)message.value=notice
  }
 }catch(e){if(current===version)error.value=e.message}
 finally{if(current===version)loading.value=false}
}
function fillDemo() { if (loading.value) return; if (resetting.value) { password.value = demoPassword; confirm.value = demoPassword } else email.value = demoAccounts[isAdminApp ? 'admin' : 'customer'].email; error.value = '' }
</script>
<template>
 <div class="page"><div class="container" style="max-width:460px">
  <h1 class="page-title">{{ resetting?'重設密碼':'忘記密碼' }}</h1>
  <p class="page-sub">{{ resetting?'請輸入新的登入密碼。連結只能使用一次，15 分鐘內有效。':'輸入註冊時的電子郵件，申請一次性重設連結。' }}</p>
  <div class="card" style="padding:26px">
   <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
<FeedbackNotice :message="message" />
   <form v-if="!resetting || !message" @submit.prevent="submit">
<DemoFillButton :disabled="loading" @fill="fillDemo" />
    <div v-if="!resetting" class="field"><label for="reset-email">電子郵件</label><input id="reset-email" v-model="email" type="email" class="input" required maxlength="200" autocomplete="email" :disabled="loading" /></div>
    <template v-else>
     <div v-if="!token" class="alert">此頁沒有有效重設連結；重新整理後請再次開啟信件中的完整連結。</div>
     <div class="field"><label for="reset-password">新密碼</label><input id="reset-password" v-model="password" type="password" class="input" required minlength="8" maxlength="64" autocomplete="new-password" :disabled="loading" /></div>
     <div class="field"><label for="reset-confirm">確認新密碼</label><input id="reset-confirm" v-model="confirm" type="password" class="input" required minlength="8" maxlength="64" autocomplete="new-password" :disabled="loading" /></div>
    </template>
    <button class="btn btn-primary btn-block" :disabled="loading || (resetting && !token)">{{ loading?'處理中…':resetting?'重設密碼':'申請重設連結' }}</button>
   </form>
   <p v-if="demoLoginEnabled && !resetting" class="muted mt-2" style="font-size:13px">本機展示不寄出真實郵件。測試信件由操作端在後端的 .local/password-reset-mails 查看，不在網站公開。</p>
   <p class="mt-2"><RouterLink :to="{name:loginRouteName}">返回登入</RouterLink> · <RouterLink v-if="resetting" :to="{name:'forgot-password'}">重新申請連結</RouterLink></p>
  </div>
 </div></div>
</template>
