<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import { couponDraft } from '@/utils/demoFill'
import {adminReturnQuery} from '@/utils/adminListQuery'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useAdminDraftGuard} from '@/composables/useAdminDraftGuard'
import { adminApi } from '@/api/admin'
import { toCouponInput, couponInputToUtc } from '@/utils/couponTime'
const route=useRoute(),router=useRouter()
const isNew=computed(()=>route.name==='admin-coupon-new')
const ready=ref(false),loading=ref(false),saving=ref(false),error=ref(''),message=ref(''),baseline=ref('')
const form=reactive({});let sequence=0,original=null,originalTimes={}
const dirty=computed(()=>ready.value&&JSON.stringify(form)!==baseline.value)
const listQuery=computed(()=>adminReturnQuery('coupons',route.query))
const listTarget=computed(()=>({name:'admin-coupons',query:listQuery.value}))
function setForm(data){
 original=data.id?data:null
 Object.keys(form).forEach(key=>delete form[key])
 Object.assign(form,{code:data.code,discountAmount:data.discountAmount,minimumAmount:data.minimumAmount,maxUses:data.maxUses,active:data.active,version:data.version??null,startsAt:toCouponInput(data.startsAt),endsAt:toCouponInput(data.endsAt)})
 originalTimes={startsAt:form.startsAt,endsAt:form.endsAt};baseline.value=JSON.stringify(form)
}
async function load(){
 const current=++sequence;ready.value=false;loading.value=true;error.value='';original=null
 try{
  const id=String(route.params.id||'')
  if(!isNew.value&&(!/^[1-9][0-9]{0,18}$/.test(id)||BigInt(id)>9223372036854775807n))throw new Error('優惠券編號格式不正確')
  const data=isNew.value?{code:'',discountAmount:100,minimumAmount:500,maxUses:100,active:true,startsAt:new Date().toISOString(),endsAt:new Date(Date.now()+30*86400000).toISOString()}:await adminApi.coupon(id)
  if(current!==sequence)return
  setForm(data);ready.value=true
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
watch([()=>route.params.id,isNew],()=>{message.value='';load()},{immediate:true})
const {confirmation,blocked,retry}=useAdminDraftGuard({dirty,saving,loading,version:()=>sequence,reload:()=>{message.value='';load()},label:'優惠券資料'})
function beforeUnload(e){if(dirty.value||saving.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{++sequence;window.removeEventListener('beforeunload',beforeUnload)})
async function save(){
 if(blocked.value||!ready.value)return
 const current=sequence;error.value='';message.value=''
 try{
  const code=form.code.trim().toUpperCase()
  if(!/^[A-Z0-9_-]{3,32}$/.test(code))throw new Error('優惠碼格式不正確')
  if(!Number.isInteger(form.maxUses)||form.maxUses<1||form.maxUses>1000000)throw new Error('總使用上限須為 1 到 1000000 的整數')
  if([form.discountAmount,form.minimumAmount].some(n=>!Number.isFinite(n)||n<0||n>999999.99||Math.abs(n*100-Math.round(n*100))>0.000001)||form.discountAmount<0.01)throw new Error('折抵與門檻金額須在有效範圍內，最多兩位小數')
  const start=couponInputToUtc(form.startsAt),end=couponInputToUtc(form.endsAt)
  const unchangedTimes=original&&form.startsAt===originalTimes.startsAt&&form.endsAt===originalTimes.endsAt
  if(!unchangedTimes&&new Date(end+'Z')<=new Date(start+'Z'))throw new Error('結束時間須晚於開始時間')
  const payload={code,discountAmount:form.discountAmount,minimumAmount:form.minimumAmount,maxUses:form.maxUses,active:form.active,version:form.version,
   startsAt:original&&form.startsAt===originalTimes.startsAt?original.startsAt:start,
   endsAt:original&&form.endsAt===originalTimes.endsAt?original.endsAt:end}
  saving.value=true
  const result=await(isNew.value?adminApi.createCoupon(payload):adminApi.updateCoupon(route.params.id,payload))
  if(current!==sequence)return
  setForm(result);message.value='優惠券已儲存'
  if(isNew.value){saving.value=false;await router.replace({name:'admin-coupon-edit',params:{id:result.id},query:listQuery.value});message.value='優惠券已儲存'}
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false}
}
function fillDemo(code) { if (blocked.value || !ready.value) return; const draft = couponDraft(); if (!isNew.value) draft.code = form.code; else if (typeof code === 'string') draft.code = code; Object.assign(form, draft); error.value = ''; message.value = '' }
</script>
<template>
 <div class="page"><div class="container">
  <RouterLink class="btn btn-ghost" :to="listTarget">返回優惠券列表</RouterLink>
  <h1 class="page-title mt-2">{{ isNew?'新增優惠券':'編輯優惠券' }}</h1>
  <p class="muted">輸入使用瀏覽器本地時間；傳送後端時轉為 UTC。未修改的時間保留原始精度。</p>
  <p v-if="loading" role="status">載入優惠券中…</p>
  <p v-if="error" class="alert alert-error" role="alert"><span>{{ error }}</span> <button class="btn btn-ghost" :disabled="blocked||loading" @click="retry">重新載入</button></p>
  <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
  <button v-if="ready&&!error" type="button" class="btn btn-ghost mb-2" :disabled="blocked||loading" @click="retry">重新載入</button>
  <form v-if="ready" class="card coupon-form mt-3" aria-label="優惠券編輯" @submit.prevent="save">
<DemoFillButton :disabled="blocked" @fill="fillDemo" />
   <DemoFillButton v-if="isNew" :disabled="blocked" label="一鍵填入前台演示優惠券（MEOW100）" @fill="fillDemo('MEOW100')" />
   <p v-if="isNew" class="muted">MEOW100 可搭配前台一鍵填入優惠碼；若已建立，請從列表編輯原券。</p>
   <label>優惠碼<input v-model="form.code" class="input" pattern="[A-Za-z0-9_-]{3,32}" maxlength="32" required :disabled="blocked || !isNew" /></label>
   <label>折抵金額<input v-model.number="form.discountAmount" class="input" type="number" min="0.01" max="999999.99" step="0.01" required :disabled="blocked" /></label>
   <label>最低商品金額<input v-model.number="form.minimumAmount" class="input" type="number" min="0" max="999999.99" step="0.01" required :disabled="blocked" /></label>
   <label>總使用上限<input v-model.number="form.maxUses" class="input" type="number" min="1" max="1000000" step="1" required :disabled="blocked" /></label>
   <label>開始時間（本地時間）<input v-model="form.startsAt" class="input" type="datetime-local" step="0.001" required :disabled="blocked" /></label>
   <label>結束時間（本地時間）<input v-model="form.endsAt" class="input" type="datetime-local" step="0.001" required :disabled="blocked" /></label>
   <label><input v-model="form.active" type="checkbox" :disabled="blocked" />啟用優惠券</label>
   <p class="muted">不提供刪除或更名；修改不會改變既有訂單折扣。降低使用上限不會撤銷舊訂單。</p>
   <div class="row"><button class="btn btn-primary" :disabled="blocked">{{ saving?'儲存中…':'儲存' }}</button><RouterLink class="btn btn-ghost" :to="listTarget">返回列表</RouterLink></div>
  </form>
 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>
.coupon-form{padding:24px;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.coupon-form p,.coupon-form .row{grid-column:1/-1}.row{flex-wrap:wrap}
@media(max-width:650px){.coupon-form{grid-template-columns:minmax(0,1fr)}}
</style>
