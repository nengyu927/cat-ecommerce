<script setup>
import FeedbackNotice from '@/components/FeedbackNotice.vue'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useConfirmation} from '@/composables/useConfirmation'
import {useAuthStore} from '@/stores/auth'
import { computed, watch, ref, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {safeListReturn} from '@/utils/listQuery'
import { orderApi } from '@/api'

const route = useRoute()
const router=useRouter()
const auth=useAuthStore(),confirmation=useConfirmation()
const returnTarget=computed(()=>safeListReturn(router,route.query.returnTo,['orders'],{name:'orders'}))
const order = ref(null)
const shipment = ref(null)
const loading = ref(true)
const error = ref('')
const message = ref('')
const cancelError = ref('')
const cancelling = ref(false)
let loadSequence = 0
onBeforeUnmount(()=>{++loadSequence})

const statusText = {
  PENDING: '待處理', PAID: '已付款', SHIPPED: '已出貨', COMPLETED: '已完成', CANCELLED: '已取消'
}

async function load() {
  const sequence = ++loadSequence
  cancelling.value = false
  loading.value = true; error.value = ''; cancelError.value = ''; message.value = ''
  try {
    const [data, shipping] = await Promise.all([orderApi.detail(route.params.id), orderApi.shipment(route.params.id)])
    if (sequence === loadSequence) { order.value = data; shipment.value = shipping }
  } catch (e) {
    if (sequence === loadSequence) error.value = e.message
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

watch(() => route.params.id, load, { immediate: true })

async function cancel() {
  if (cancelling.value || !order.value || !['PENDING', 'PAID'].includes(order.value.status)) return
  if (confirmation.pending.value) return
  const id = order.value.id, sequence = loadSequence, epoch = auth.epoch
  const current=()=>sequence===loadSequence&&epoch===auth.epoch&&auth.isLoggedIn
  const accepted=await confirmation.ask({title:'取消這筆訂單？',description:'取消後無法恢復此訂單，若仍需要商品，請重新下單。',detail:'訂單編號：'+order.value.orderNo,confirmLabel:'確認取消訂單'})
  auth.syncFromStorage()
  if(!accepted||!current()||order.value?.id!==id||!['PENDING','PAID'].includes(order.value.status))return
  cancelling.value = true; cancelError.value = ''
  try {
    const data = await orderApi.cancel(id)
    if (!current()) return
    order.value = data
    message.value = '訂單已取消'
  } catch (e) {
    if(current())cancelError.value = e.message
  } finally { if(current())cancelling.value = false }
}
</script>

<template>
  <div class="page">
    <div class="container">
      <RouterLink class="btn btn-ghost mb-2" :to="returnTarget">返回訂單列表</RouterLink>
      <div v-if="loading" class="spinner">載入中…</div>
      <div v-else-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost" @click="load">重試載入</button></div>
      <div v-else-if="order">
        <div class="row-between">
          <div>
            <h1 class="page-title">訂單 {{ order.orderNo }}</h1>
            <p class="page-sub">{{ new Date(order.createdAt).toLocaleString('zh-TW') }}</p>
          </div>
          <span class="badge" :class="`badge-${order.status.toLowerCase()}`">{{ statusText[order.status] || order.status }}</span>
        </div>

<FeedbackNotice :message="message" />
        <div v-if="cancelError" class="alert alert-error" role="alert">{{ cancelError }}</div>

        <section class="card mt-3" style="padding:20px; margin-bottom:20px" aria-label="出貨紀錄">
          <h3>出貨紀錄</h3>
          <template v-if="shipment">
            <p>{{ shipment.carrier }} · 物流單號：<strong style="overflow-wrap:anywhere">{{ shipment.trackingNumber }}</strong></p>
            <p>出貨登錄時間：{{ new Date(shipment.shippedAt).toLocaleString('zh-TW') }}</p>
            <p class="muted">由商店人工登錄，非物流即時追蹤；配送進度請向物流公司查詢。</p>
          </template>
          <p v-else class="muted">尚無出貨登錄資料。</p>
        </section>
        <div class="grid order-detail-grid">
          <div class="card">
            <table class="table">
              <thead><tr><th>商品</th><th>規格</th><th>單價</th><th>數量</th><th>小計</th></tr></thead>
              <tbody>
                <tr v-for="it in order.items" :key="it.id">
                  <td style="font-weight: 600;">{{ it.productName }}</td>
                  <td class="muted">{{ it.skuSpec }}</td>
                  <td>NT$ {{ Number(it.unitPrice).toLocaleString() }}</td>
                  <td>{{ it.quantity }}</td>
                  <td style="font-weight: 700;">NT$ {{ Number(it.subtotal).toLocaleString() }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="card" style="padding: 20px;">
            <h3 style="margin: 0 0 14px;">收件資訊</h3>
            <div style="font-size: 14px; line-height: 1.9;">
              <div><span class="muted">收件人：</span>{{ order.receiverName }}</div>
              <div><span class="muted">電話：</span>{{ order.receiverPhone }}</div>
              <div><span class="muted">地址：</span>{{ order.shippingAddress }}</div>
              <div><span class="muted">付款：</span>{{ order.paymentMethod === 'COD' ? '貨到付款' : '模擬付款' }}</div>
              <div v-if="order.remark"><span class="muted">備註：</span>{{ order.remark }}</div>
            </div>
            <hr style="border: none; border-top: 1px solid var(--line); margin: 14px 0;" />
            <div class="row-between" style="margin-bottom: 6px;"><span class="muted">運費</span><span>NT$ {{ Number(order.shippingFee).toLocaleString() }}</span></div>
            <div v-if="order.couponCode" class="row-between" style="margin-bottom:6px"><span>優惠 {{ order.couponCode }}</span><span>− NT$ {{ Number(order.discountAmount).toLocaleString() }}</span></div>
            <div class="row-between" style="font-size: 18px; font-weight: 800;">
              <span>合計</span><span style="color: var(--brand);">NT$ {{ Number(order.totalAmount).toLocaleString() }}</span>
            </div>
            <button v-if="['PENDING', 'PAID'].includes(order.status)" class="btn btn-danger btn-block mt-3" :disabled="cancelling" @click="cancel">{{ cancelling ? '取消中…' : '取消訂單' }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
  <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>

<style scoped>
.order-detail-grid { grid-template-columns:minmax(0,1fr) 320px; align-items:start }
.order-detail-grid > .card { min-width:0; overflow-x:auto }
@media(max-width:800px) { .order-detail-grid { grid-template-columns:minmax(0,1fr) } }
</style>
