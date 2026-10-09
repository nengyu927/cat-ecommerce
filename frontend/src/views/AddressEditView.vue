<script setup>
import FeedbackNotice from '@/components/FeedbackNotice.vue'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useConfirmation} from '@/composables/useConfirmation'
import {computed,reactive,ref,watch,onBeforeUnmount} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {memberApi} from '@/api'
import {useAuthStore} from '@/stores/auth'
import FieldError from '@/components/FieldError.vue'
import {useFormErrors} from '@/composables/useFormErrors'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const confirmation=useConfirmation()
const isNew=computed(()=>route.name==='account-address-new')
const loaded=ref(false),loading=ref(false),saving=ref(false),error=ref(''),message=ref(''),baseline=ref('')
const blocked=computed(()=>saving.value||confirmation.pending.value)
const addressForm=reactive({})
const {fields:addressFields,clear:clearAddress,capture:captureAddress}=useFormErrors()
const keys=['receiverName','phone','city','district','line1','line2','postalCode','isDefault']
let sequence=0
const dirty=computed(()=>loaded.value&&JSON.stringify(addressForm)!==baseline.value)
function setForm(data){for(const key of keys)addressForm[key]=key==='isDefault'?data[key]===true:data[key]||'';baseline.value=JSON.stringify(addressForm)}
async function load(){
 const current=++sequence,epoch=auth.epoch;loaded.value=false;loading.value=true;error.value='';clearAddress()
 try{
  const id=String(route.params.id||'')
  if(!isNew.value&&(!/^[1-9][0-9]{0,18}$/.test(id)||BigInt(id)>9223372036854775807n))throw new Error('地址編號須為有效的正整數')
  let data
  if(isNew.value){
   await auth.refreshProfile();const addresses=await memberApi.listAddresses()
   data={receiverName:auth.member?.name,phone:auth.member?.phone,isDefault:addresses.length===0}
  }else data=await memberApi.address(id)
  if(current!==sequence||epoch!==auth.epoch)return
  setForm(data);loaded.value=true
 }catch(e){if(current===sequence&&epoch===auth.epoch)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
watch([()=>route.params.id,isNew],()=>{message.value='';load()},{immediate:true})
async function guard(to){
 auth.syncFromStorage()
 if(!auth.isLoggedIn||to.name==='login')return true
 if(blocked.value)return false
 if(!dirty.value)return true
 const current=sequence,epoch=auth.epoch
 const accepted=await confirmation.ask({title:'地址尚未儲存',description:'離開後，尚未儲存的地址修改將不會保留。',cancelLabel:'繼續編輯',confirmLabel:'放棄修改並離開'})
 auth.syncFromStorage()
 return accepted&&current===sequence&&epoch===auth.epoch&&auth.isLoggedIn&&!saving.value
}
onBeforeRouteLeave(guard);onBeforeRouteUpdate(guard)
function beforeUnload(e){if(dirty.value||saving.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{++sequence;window.removeEventListener('beforeunload',beforeUnload)})
async function retry(){
 if(blocked.value||loading.value)return
 const current=sequence,epoch=auth.epoch
 if(dirty.value&&!await confirmation.ask({title:'重新載入地址？',description:'尚未儲存的地址修改將被清除，並以最新資料取代。',cancelLabel:'繼續編輯',confirmLabel:'放棄修改並重新載入'}))return
 auth.syncFromStorage()
 if(current!==sequence||epoch!==auth.epoch||!auth.isLoggedIn||saving.value)return
 message.value='';load()
}
async function save(){
 if(blocked.value||!loaded.value)return
 const current=sequence,epoch=auth.epoch;error.value='';message.value='';clearAddress()
 if(['receiverName','phone','city','line1'].some(key=>!addressForm[key].trim())){error.value='收件人、電話、縣市與地址不可空白';return}
 const payload=Object.fromEntries(keys.map(key=>[key,addressForm[key]]));saving.value=true
 try{
  const data=await(isNew.value?memberApi.createAddress(payload):memberApi.updateAddress(route.params.id,payload))
  if(current!==sequence||epoch!==auth.epoch)return
  setForm(data);message.value='地址已儲存'
  if(isNew.value){saving.value=false;await router.replace({name:'account-address-edit',params:{id:data.id}});message.value='地址已儲存'}
 }catch(e){if(current===sequence&&epoch===auth.epoch){error.value=e.message;captureAddress(e)}}
 finally{if(current===sequence)saving.value=false}
}
</script>
<template>
 <div class="page"><div class="container">
  <RouterLink class="btn btn-ghost" :to="{name:'account-addresses'}">返回地址列表</RouterLink>
  <h1 class="page-title mt-2">{{ isNew?'新增收件地址':'編輯收件地址' }}</h1>
<p v-if="loading" role="status">載入地址中…</p><p v-if="error" class="alert alert-error" role="alert">{{ error }}</p><FeedbackNotice :message="message" />
<button class="btn btn-ghost mb-2" :disabled="blocked||loading" @click="retry">重新載入地址</button>
  <form v-if="loaded" class="card address-form" aria-label="收件地址編輯" @submit.prevent="save">
          <div class="field"><label>收件人<input v-model="addressForm.receiverName" class="input" :disabled="blocked" required maxlength="100" :aria-invalid="!!addressFields.receiverName" :aria-describedby="addressFields.receiverName ? 'address-receiverName-error' : undefined" /></label><FieldError id="address-receiverName-error" :message="addressFields.receiverName" /></div>
          <div class="field"><label>電話<input v-model="addressForm.phone" class="input" :disabled="blocked" required maxlength="30" :aria-invalid="!!addressFields.phone" :aria-describedby="addressFields.phone ? 'address-phone-error' : undefined" /></label><FieldError id="address-phone-error" :message="addressFields.phone" /></div>
          <div class="field"><label>縣市<input v-model="addressForm.city" class="input" :disabled="blocked" required maxlength="50" :aria-invalid="!!addressFields.city" :aria-describedby="addressFields.city ? 'address-city-error' : undefined" /></label><FieldError id="address-city-error" :message="addressFields.city" /></div>
          <div class="field"><label>區域<input v-model="addressForm.district" class="input" :disabled="blocked" maxlength="50" :aria-invalid="!!addressFields.district" :aria-describedby="addressFields.district ? 'address-district-error' : undefined" /></label><FieldError id="address-district-error" :message="addressFields.district" /></div>
          <div class="field"><label>地址<input v-model="addressForm.line1" class="input" :disabled="blocked" required maxlength="200" :aria-invalid="!!addressFields.line1" :aria-describedby="addressFields.line1 ? 'address-line1-error' : undefined" /></label><FieldError id="address-line1-error" :message="addressFields.line1" /></div>
          <div class="field"><label>樓層 / 補充地址<input v-model="addressForm.line2" class="input" :disabled="blocked" maxlength="200" :aria-invalid="!!addressFields.line2" :aria-describedby="addressFields.line2 ? 'address-line2-error' : undefined" /></label><FieldError id="address-line2-error" :message="addressFields.line2" /></div>
          <div class="field"><label>郵遞區號<input v-model="addressForm.postalCode" class="input" :disabled="blocked" maxlength="20" :aria-invalid="!!addressFields.postalCode" :aria-describedby="addressFields.postalCode ? 'address-postalCode-error' : undefined" /></label><FieldError id="address-postalCode-error" :message="addressFields.postalCode" /></div>
          <label><input v-model="addressForm.isDefault" type="checkbox" :disabled="blocked" />設為預設地址</label>

   <p class="muted">設為預設會取消同帳號其他預設地址；取消勾選允許沒有預設地址。</p>
   <button class="btn btn-primary" :disabled="blocked">{{ saving?'儲存中…':'儲存地址' }}</button>
  </form>
 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>.address-form{padding:24px;max-width:680px;overflow-wrap:anywhere}</style>
