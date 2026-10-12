<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'

import {adminReturnQuery} from '@/utils/adminListQuery'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useAdminDraftGuard} from '@/composables/useAdminDraftGuard'
import { adminApi } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const order=ref(null),shipment=ref(null),loading=ref(false),saving=ref(false),error=ref(''),message=ref('')
const form=reactive({carrier:'',trackingNumber:''})
let sequence=0
const isShipping=computed(()=>!!route.meta.shipmentForm)
const dirty=computed(()=>isShipping.value && !!(form.carrier||form.trackingNumber))
const canShip=computed(()=>order.value && (order.value.status==='PAID'||(order.value.status==='PENDING'&&order.value.paymentMethod==='COD')))
const labels={PENDING:'待處理',PAID:'已付款',SHIPPED:'已出貨',COMPLETED:'已完成',CANCELLED:'已取消'}
const transitions={PENDING:['PAID','CANCELLED'],PAID:['CANCELLED'],SHIPPED:['COMPLETED']}
const actions=computed(()=>transitions[order.value?.status]||[])
const listQuery=computed(()=>adminReturnQuery('orders',route.query))
const listTarget=computed(()=>({name:'admin',params:{section:'orders'},query:listQuery.value}))
const detailTarget=computed(()=>({name:'admin-order-detail',params:{id:route.params.id},query:listQuery.value}))
async function load(){
 const current=++sequence;loading.value=true;error.value='';order.value=null;shipment.value=null
 try{
  const id=String(route.params.id)
  if(!/^[1-9][0-9]{0,18}$/.test(id)||BigInt(id)>9223372036854775807n)throw new Error('訂單編號格式不正確')
  const [data,record]=await Promise.all([adminApi.order(id),adminApi.shipment(id)])
  if(current!==sequence)return
  order.value=data;shipment.value=record
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
watch([()=>route.params.id,isShipping],([id],[previousId]=[])=>{
 if(id!==previousId)message.value=''
 Object.assign(form,{carrier:'',trackingNumber:''})
 load()
},{immediate:true})
const {confirmation,blocked,retry}=useAdminDraftGuard({dirty,saving,loading,version:()=>sequence,label:'出貨資料',reload:()=>{Object.assign(form,{carrier:'',trackingNumber:''});load()}})
function beforeUnload(event){if(dirty.value||saving.value){event.preventDefault();event.returnValue=''}}
function currentOperation(current,epoch,id){
 auth.syncFromStorage()
 return current===sequence&&auth.epoch===epoch&&auth.isLoggedIn&&auth.member?.role==='ADMIN'&&order.value?.id===id&&!loading.value&&!saving.value
}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{++sequence;window.removeEventListener('beforeunload',beforeUnload)})
async function ship(){
 if(blocked.value||loading.value||!isShipping.value||!canShip.value)return
 const payload={carrier:form.carrier.trim(),trackingNumber:form.trackingNumber.trim()}
 if(!payload.carrier||!payload.trackingNumber){error.value='請填寫物流公司與單號';return}
 const current=sequence,id=order.value.id,epoch=auth.epoch
 const accepted=await confirmation.ask({title:'確認登錄出貨？',description:'請核對物流資料。出貨後不可取消訂單。',detail:`訂單：${order.value.orderNo}\n物流公司：${payload.carrier}\n物流單號：${payload.trackingNumber}`,cancelLabel:'返回修改',confirmLabel:'確認登錄出貨',tone:'primary'})
 if(!accepted||!currentOperation(current,epoch,id)||!canShip.value||!isShipping.value||payload.carrier!==form.carrier.trim()||payload.trackingNumber!==form.trackingNumber.trim())return
 saving.value=true;error.value='';message.value=''
 try{
  await adminApi.ship(id,payload)
  if(current!==sequence)return
  Object.assign(form,{carrier:'',trackingNumber:''});message.value='已登錄出貨'
  saving.value=false
  await router.replace(detailTarget.value)
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false}
}
async function updateStatus(target){
 if(blocked.value||loading.value||isShipping.value||!actions.value.includes(target))return
 const current=sequence,id=order.value.id,epoch=auth.epoch,status=order.value.status
 const accepted=await confirmation.ask({title:`將訂單改為「${labels[target]}」？`,description:target==='CANCELLED'?'取消後將回補庫存與優惠券名額。':'請確認訂單狀態後再繼續。',detail:`訂單：${order.value.orderNo}`,cancelLabel:'保留原狀態',confirmLabel:`確認${labels[target]}`,tone:target==='CANCELLED'?'danger':'primary'})
 if(!accepted||!currentOperation(current,epoch,id)||isShipping.value||order.value.status!==status||!actions.value.includes(target))return
 saving.value=true;error.value='';message.value=''
 try{const data=await adminApi.updateStatus(id,target);if(current===sequence){order.value=data;message.value='訂單狀態已更新'}}
 catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false}
}
function fillDemo() { if (blocked.value || loading.value || !canShip.value || !isShipping.value) return; Object.assign(form, { carrier: '黑貓宅急便', trackingNumber: `2${String(Date.now()).slice(-11)}` }); error.value = '' }
</script>
<template>
 <div class="page"><div class="container">
  <nav class="row mb-2" aria-label="訂單導覽"><RouterLink class="btn btn-ghost" :to="listTarget">返回訂單列表</RouterLink><RouterLink v-if="isShipping" class="btn btn-ghost" :to="detailTarget">返回訂單詳情</RouterLink></nav>
  <h1 class="page-title">{{ isShipping?'出貨登錄':'訂單詳情' }}</h1>
  <p v-if="loading" class="spinner" role="status">載入訂單中…</p>
  <p v-if="error" class="alert alert-error" role="alert"><span>{{ error }}</span> <button class="btn btn-ghost btn-sm" :disabled="loading||blocked" @click="retry">重新載入</button></p>
  <button v-if="order&&!loading&&!error" class="btn btn-ghost btn-sm" :disabled="blocked" @click="retry">重新載入</button>
  <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
  <template v-if="order&&!loading">
   <section class="card order-panel"><h2>{{ order.orderNo }}</h2><p>{{ labels[order.status] }} · {{ order.paymentMethod==='COD'?'貨到付款':'模擬付款' }}</p><p>會員：{{ order.memberName }} · {{ order.memberEmail }}</p><p>收件：{{ order.receiverName }} · {{ order.receiverPhone }} · {{ order.shippingAddress }}</p><p v-if="order.remark">備註：{{ order.remark }}</p><p v-if="order.couponCode">優惠碼 {{ order.couponCode }} · 折抵 NT$ {{ order.discountAmount }}</p><p>運費 NT$ {{ order.shippingFee }} · 合計 NT$ {{ order.totalAmount }}</p></section>
   <div class="card mt-3 order-items"><table class="table"><thead><tr><th>商品／規格</th><th>單價</th><th>數量</th><th>小計</th></tr></thead><tbody><tr v-for="item in order.items" :key="item.id"><td>{{ item.productName }}／{{ item.skuSpec }}</td><td>{{ item.unitPrice }}</td><td>{{ item.quantity }}</td><td>{{ item.subtotal }}</td></tr></tbody></table></div>
   <section class="card mt-3 order-panel" aria-label="出貨紀錄"><h3>出貨紀錄</h3><p v-if="shipment">{{ shipment.carrier }} · {{ shipment.trackingNumber }} · {{ new Date(shipment.shippedAt).toLocaleString('zh-TW') }}</p><p v-else>尚無出貨登錄資料（舊訂單不補造紀錄）。</p><p class="muted">人工登錄，非物流即時追蹤；已出貨不可取消訂單。</p></section>
   <form v-if="isShipping&&canShip" class="card mt-3 order-panel" aria-label="出貨登錄" @submit.prevent="ship">
<DemoFillButton :disabled="blocked" @fill="fillDemo" />
    <div class="field"><label>物流公司<input v-model="form.carrier" class="input" maxlength="80" required :disabled="blocked" /></label></div>
    <div class="field"><label>物流單號<input v-model="form.trackingNumber" class="input" maxlength="100" required :disabled="blocked" /></label></div>
    <button class="btn btn-primary" :disabled="blocked">{{ saving?'處理中…':'確認出貨' }}</button>
   </form>
   <p v-else-if="isShipping" class="alert" role="status">目前訂單狀態不可出貨。請返回詳情查看最新狀態。</p>
   <div v-if="!isShipping" class="row mt-3" style="flex-wrap:wrap">
    <RouterLink v-if="canShip&&!blocked" class="btn btn-primary" :to="{name:'admin-order-shipment',params:{id:order.id},query:listQuery}">登錄出貨</RouterLink>
    <button v-for="target in actions" :key="target" class="btn" :class="target==='CANCELLED'?'btn-danger':'btn-primary'" :disabled="blocked" @click="updateStatus(target)">{{ labels[target] }}</button>
   </div>
  </template>
 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>
.order-panel{padding:24px;overflow-wrap:anywhere}.order-items{overflow-x:auto}
</style>
