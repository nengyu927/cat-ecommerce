<script setup>
import FeedbackNotice from '@/components/FeedbackNotice.vue'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useConfirmation} from '@/composables/useConfirmation'
import {computed,onBeforeUnmount,reactive,ref,watch} from 'vue'
import {onBeforeRouteLeave,onBeforeRouteUpdate,useRoute,useRouter} from 'vue-router'
import {memberApi} from '@/api'
import {useAuthStore} from '@/stores/auth'
import FieldError from '@/components/FieldError.vue'
import {useFormErrors} from '@/composables/useFormErrors'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const confirmation=useConfirmation()
const isPassword=computed(()=>route.meta.accountForm==='password')
const profile=reactive({name:'',phone:''}),pwd=reactive({currentPassword:'',newPassword:''})
const loading=ref(false),loaded=ref(false),saving=ref(false),error=ref(''),message=ref(''),baseline=ref('')
const blocked=computed(()=>saving.value||confirmation.pending.value)
const {fields:profileFields,clear:clearProfile,capture:captureProfile}=useFormErrors()
const {fields:passwordFields,clear:clearPassword,capture:capturePassword}=useFormErrors()
const dirty=computed(()=>loaded.value&&(isPassword.value?!!(pwd.currentPassword||pwd.newPassword):JSON.stringify(profile)!==baseline.value))
let sequence=0
function clearPasswords(){pwd.currentPassword='';pwd.newPassword=''}
async function load(){
 const current=++sequence,epoch=auth.epoch;loaded.value=false;loading.value=true;error.value='';clearProfile();clearPassword();clearPasswords()
 try{
  await auth.refreshProfile()
  if(current!==sequence||epoch!==auth.epoch||!auth.isLoggedIn)return
  profile.name=auth.member?.name||'';profile.phone=auth.member?.phone||''
  baseline.value=JSON.stringify(profile);loaded.value=true
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
watch(()=>route.meta.accountForm,()=>{message.value='';load()},{immediate:true})
async function guard(to){
 auth.syncFromStorage()
 if(!auth.isLoggedIn||to.name==='login')return true
 if(blocked.value)return false
 if(!dirty.value)return true
 const current=sequence,epoch=auth.epoch
 const accepted=await confirmation.ask({title:'資料尚未儲存',description:'離開後，尚未儲存的修改將不會保留。',cancelLabel:'繼續編輯',confirmLabel:'放棄修改並離開'})
 auth.syncFromStorage()
 return accepted&&current===sequence&&epoch===auth.epoch&&auth.isLoggedIn&&!saving.value
}
onBeforeRouteLeave(guard);onBeforeRouteUpdate(guard)
function beforeUnload(e){if(dirty.value||saving.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{++sequence;clearPasswords();window.removeEventListener('beforeunload',beforeUnload)})
async function retry(){
 if(loading.value||blocked.value)return
 const current=sequence,epoch=auth.epoch
 if(dirty.value&&!await confirmation.ask({title:'重新載入資料？',description:'尚未儲存的修改將被清除，並以最新會員資料取代。',cancelLabel:'繼續編輯',confirmLabel:'放棄修改並重新載入'}))return
 auth.syncFromStorage()
 if(current!==sequence||epoch!==auth.epoch||!auth.isLoggedIn||saving.value)return
 message.value='';load()
}
async function save(){
 if(blocked.value||!loaded.value)return
 const current=sequence,epoch=auth.epoch;error.value='';message.value='';clearProfile();clearPassword()
 if(!isPassword.value&&!profile.name.trim()){error.value='姓名不可為空';return}
 saving.value=true
 try{
  if(isPassword.value){
   await memberApi.changePassword({currentPassword:pwd.currentPassword,newPassword:pwd.newPassword})
   if(current!==sequence||epoch!==auth.epoch)return
   clearPasswords();auth.clearSession('password-changed')
   await router.replace({name:'login',query:{reason:'password-changed'}})
  }else{
   const data=await memberApi.update({name:profile.name,phone:profile.phone})
   if(current!==sequence||epoch!==auth.epoch)return
   auth.syncFromStorage();if(epoch!==auth.epoch||!auth.isLoggedIn)return
   auth.member=data;auth.persist()
   profile.name=data.name||'';profile.phone=data.phone||'';baseline.value=JSON.stringify(profile)
   message.value='個人資料已更新'
  }
 }catch(e){
  if(current===sequence&&epoch===auth.epoch){error.value=e.message;(isPassword.value?capturePassword:captureProfile)(e)}
 }finally{if(current===sequence)saving.value=false}
}
</script>
<template>
 <div class="page"><div class="container">
  <RouterLink class="btn btn-ghost" to="/account">返回會員中心</RouterLink>
  <h1 class="page-title mt-2">{{ isPassword?'變更密碼':'個人資料' }}</h1>
  <p class="page-sub">{{ auth.member?.email }}</p>
  <p v-if="loading" role="status">載入會員資料中…</p>
  <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
<FeedbackNotice :message="message" />
<button class="btn btn-ghost mb-2" :disabled="blocked||loading" @click="retry">重新載入資料</button>
  <form v-if="loaded" class="card settings-form" :aria-label="isPassword?'密碼變更':'個人資料編輯'" @submit.prevent="save">
   <template v-if="!isPassword">
          <div class="field"><label for="account-name">姓名</label><input id="account-name" v-model="profile.name" class="input" maxlength="100" :disabled="blocked" :aria-invalid="!!profileFields.name" :aria-describedby="profileFields.name ? 'account-name-error' : undefined" /><FieldError id="account-name-error" :message="profileFields.name" /></div>
          <div class="field"><label for="account-phone">電話</label><input id="account-phone" v-model="profile.phone" class="input" maxlength="30" :disabled="blocked" :aria-invalid="!!profileFields.phone" :aria-describedby="profileFields.phone ? 'account-phone-error' : undefined" /><FieldError id="account-phone-error" :message="profileFields.phone" /></div>

   </template>
   <template v-else>
    <p class="muted">密碼變更成功後，所有裝置的舊登入會失效，請重新登入。</p>
          <div class="field"><label for="password-current">目前密碼</label><input id="password-current" v-model="pwd.currentPassword" class="input" type="password" required autocomplete="current-password" :disabled="blocked" :aria-invalid="!!passwordFields.currentPassword" :aria-describedby="passwordFields.currentPassword ? 'password-current-error' : undefined" /><FieldError id="password-current-error" :message="passwordFields.currentPassword" /></div>
          <div class="field"><label for="password-new">新密碼</label><input id="password-new" v-model="pwd.newPassword" class="input" type="password" required autocomplete="new-password" minlength="8" maxlength="64" :disabled="blocked" :aria-invalid="!!passwordFields.newPassword" :aria-describedby="passwordFields.newPassword ? 'password-new-error' : undefined" /><FieldError id="password-new-error" :message="passwordFields.newPassword" /></div>

   </template>
   <button class="btn btn-primary" :disabled="blocked">{{ saving?'處理中…':isPassword?'變更密碼':'儲存' }}</button>
  </form>
 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>
.settings-form{padding:24px;max-width:680px;overflow-wrap:anywhere}
</style>
