<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import { checkoutDraft } from '@/utils/demoFill'
import { onMounted, onBeforeUnmount, reactive, ref, watch, computed } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { memberApi, orderApi } from '@/api'
import { useCartStore } from '@/stores/cart'
import { useAuthStore } from '@/stores/auth'
import { readCheckoutAttempt, saveCheckoutAttempt, settleCheckoutAttempt, checkoutCartSignature, quoteAmounts } from '@/utils/checkoutAttempt'
import FieldError from '@/components/FieldError.vue'
import { useFormErrors } from '@/composables/useFormErrors'
import {isCouponFailure,couponFailureMessage} from '@/utils/couponFailure'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useConfirmation} from '@/composables/useConfirmation'

const router = useRouter()
const cart = useCartStore()
const auth = useAuthStore()
const confirmation=useConfirmation()
const leaveConfirmation=useConfirmation()
function answerConfirmation(accepted){if(leaveConfirmation.pending.value)leaveConfirmation.answer(accepted);else confirmation.answer(accepted)}

const form = reactive({ receiverName: '', receiverPhone: '', shippingAddress: '', remark: '', paymentMethod: 'COD' })
const addresses = ref([])
const { error, fields, clear, capture } = useFormErrors()
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')
const addressWarning = ref('')
const submitting = ref(false)
const needsSync = ref(false)
const syncMessage = ref('')
const outcomeUnknown = ref(false)
const pendingAttempt = ref(null)
const checkingResult = ref(false)
const storageError = ref('')
const recoveryMessage = ref('')
const identityConflict = ref(false)
const createdOrder = ref(null)
const navigationError = ref('')
let submitSequence = 0
let loadSequence = 0
let defaultsApplied = false
const stockIssues = computed(() => cart.items.filter(item => item.stock <= 0 || item.quantity > item.stock))
const checkoutBlocked = computed(() => loading.value || cart.loading || !loaded.value || !currentQuote.value || !!loadError.value || !!storageError.value || !!pendingAttempt.value || needsSync.value || outcomeUnknown.value || !!createdOrder.value || cart.isEmpty || stockIssues.value.length > 0)
const retryCartMatches = computed(() => loaded.value && !loadError.value && (cart.isEmpty || pendingAttempt.value?.cartSignature === checkoutCartSignature(cart.items)))
const couponCode=ref(''),couponQuote=ref(null),couponError=ref(''),quoting=ref(false)
const baseQuote=ref(null)
const couponDecision=ref(false),couponMessage=ref('')
const currentQuote=computed(()=>couponQuote.value || baseQuote.value)
const summaryAmounts=computed(()=>pendingAttempt.value?.payload?.expectedAmounts || currentQuote.value)
let quoteSequence=0
const displayedTotal=computed(()=>summaryAmounts.value?.totalAmount)
const reviewFingerprint=computed(()=>JSON.stringify([form,auth.epoch,cart.items,currentQuote.value,couponCode.value,couponDecision.value,loading.value,cart.loading,needsSync.value]))
watch(reviewFingerprint,()=>{if(confirmation.pending.value){confirmation.answer(false);syncMessage.value='結帳內容已變更，請檢查最新資料後重新確認下單。'}},{flush:'sync'})
function clearCoupon(){const decision=couponDecision.value||!!couponQuote.value||!!couponError.value||!!couponCode.value.trim();++quoteSequence;couponQuote.value=null;couponError.value='';quoting.value=false;couponDecision.value=decision;couponMessage.value=''}
watch(couponCode,clearCoupon,{flush:'sync'})
function warnBeforeUnload(event) {
  if (!submitting.value || createdOrder.value) return
  event.preventDefault()
  event.returnValue = ''
}
onMounted(() => window.addEventListener('beforeunload', warnBeforeUnload))
onBeforeUnmount(()=>{
  ++quoteSequence; ++loadSequence; ++submitSequence
  window.removeEventListener('beforeunload', warnBeforeUnload)
})
// The leave prompt is independent of order review: cart refresh must not cancel it.
watch([submitting,createdOrder,()=>auth.epoch],()=>{if(!submitting.value||createdOrder.value)leaveConfirmation.answer(false)},{flush:'sync'})
onBeforeRouteLeave(async to => {
  auth.syncFromStorage()
  if(!auth.isLoggedIn||to.name==='login'){leaveConfirmation.answer(false);return true}
  if(!submitting.value||createdOrder.value)return true
  const sequence=submitSequence,epoch=auth.epoch
  const accepted=await leaveConfirmation.ask({title:'訂單處理中，仍要離開？',description:'離開頁面不會取消下單，訂單仍可能成立。請勿另外建立新訂單。',detail:`下單識別碼：${pendingAttempt.value?.requestId||'請保留目前分頁'}\n離開後可返回結帳頁查詢此次結果，或到「我的訂單」核對。`,cancelLabel:'留在此頁等待',confirmLabel:'了解並離開'})
  auth.syncFromStorage()
  return accepted&&sequence===submitSequence&&epoch===auth.epoch&&auth.isLoggedIn&&submitting.value&&!createdOrder.value
})
function restoreAttempt() {
  try {
    auth.syncFromStorage()
    const previous = pendingAttempt.value
    pendingAttempt.value = readCheckoutAttempt(auth)
    storageError.value = ''
    outcomeUnknown.value = !!pendingAttempt.value
    if (pendingAttempt.value) {
      defaultsApplied = true
      if (pendingAttempt.value.payload) {
        const { requestId, cartSnapshot, expectedAmounts, couponCode: code, ...draft } = pendingAttempt.value.payload
        Object.assign(form, draft)
        couponCode.value = code || ''
      } else if (previous?.payload) {
        Object.assign(form, { receiverName: '', receiverPhone: '', shippingAddress: '', remark: '', paymentMethod: 'COD' })
        couponCode.value = ''
      }
    }
    return pendingAttempt.value
  } catch (e) { storageError.value = '無法讀取下單暫存，請先確認訂單結果：' + e.message; return null }
}
restoreAttempt()
watch(() => auth.epoch, () => { ++submitSequence; ++loadSequence; clearCoupon(); pendingAttempt.value = null; outcomeUnknown.value = false })
async function applyCoupon(){
 if(confirmation.pending.value||quoting.value||submitting.value||checkoutBlocked.value||!couponCode.value.trim())return
 const sequence=++quoteSequence,epoch=auth.epoch;quoting.value=true;couponError.value='';couponMessage.value='';couponQuote.value=null;couponDecision.value=true
 try {const data=await orderApi.quote(couponCode.value.trim());if(sequence===quoteSequence&&epoch===auth.epoch){quoteAmounts(data,cart.items);couponQuote.value=data;couponDecision.value=false}}
 catch(e){if(sequence===quoteSequence&&epoch===auth.epoch)couponError.value=couponFailureMessage(e)}
 finally{if(sequence===quoteSequence)quoting.value=false}
}
async function removeCoupon(){
 if(confirmation.pending.value||quoting.value||submitting.value||checkoutBlocked.value)return
 const sequence=++quoteSequence,epoch=auth.epoch
 quoting.value=true;couponDecision.value=true;couponError.value='';couponMessage.value=''
 try{
  const data=await orderApi.checkoutQuote()
  if(sequence!==quoteSequence||epoch!==auth.epoch)return
  quoteAmounts(data,cart.items);baseQuote.value=data;couponCode.value='';clearCoupon();couponDecision.value=false
  couponMessage.value='優惠已移除，請確認最新原價合計後，再自行送出訂單。'
 }catch(e){if(sequence===quoteSequence&&epoch===auth.epoch)couponError.value='無法取得最新原價報價，優惠尚未移除：'+e.message}
 finally{if(sequence===quoteSequence)quoting.value=false}
}

async function load() {
  if (confirmation.pending.value || loading.value || submitting.value || checkingResult.value || createdOrder.value) return
  const sequence = ++loadSequence
  const hadData = loaded.value
  loading.value = true; loadError.value = ''; addressWarning.value = ''; syncMessage.value = ''
  clearCoupon()
  baseQuote.value = null
  try {
    await cart.fetch()
    if (sequence !== loadSequence) return
    if (!cart.isEmpty && !stockIssues.value.length && !pendingAttempt.value) {
      const quote = await orderApi.checkoutQuote()
      if (sequence !== loadSequence) return
      quoteAmounts(quote,cart.items)
      baseQuote.value = quote
    }
    try {
      const data = await memberApi.listAddresses()
      if (sequence !== loadSequence) return
      addresses.value = data
    }
    catch (e) {
      if (sequence !== loadSequence) return
      if (e.status === 401) throw e
      addresses.value = []
      addressWarning.value = '收件地址載入失敗，可手動填寫或重試'
    }
    const def = addresses.value.find(a => a.isDefault) || addresses.value[0]
    if (def && !defaultsApplied) {
      if (!form.receiverName) form.receiverName = def.receiverName
      if (!form.receiverPhone) form.receiverPhone = def.phone
      if (!form.shippingAddress) form.shippingAddress = `${def.city}${def.district || ''}${def.line1}${def.line2 || ''}`
    }
    defaultsApplied = true
    loaded.value = true
    needsSync.value = false
    if (hadData) clear()
    if (hadData) syncMessage.value = '結帳資料已同步，填寫內容已保留；優惠券請重新套用。'
  } catch (e) { if (sequence === loadSequence) loadError.value = e.message }
  finally { if (sequence === loadSequence) loading.value = false }
}
onMounted(load)

function useAddress(a) {
  if (confirmation.pending.value || submitting.value || loading.value || pendingAttempt.value || createdOrder.value) return
  form.receiverName = a.receiverName
  form.receiverPhone = a.phone
  form.shippingAddress = `${a.city}${a.district || ''}${a.line1}${a.line2 || ''}`
}

async function submit() {
  if (confirmation.pending.value || submitting.value || quoting.value || couponDecision.value || checkoutBlocked.value || !form.receiverName.trim() || !form.receiverPhone.trim() || !form.shippingAddress.trim()) return
  auth.syncFromStorage()
  const fingerprint=reviewFingerprint.value,epoch=auth.epoch
  let payload
  try{payload={...form,couponCode:couponQuote.value?.couponCode||null,expectedAmounts:quoteAmounts(currentQuote.value,cart.items)}}
  catch(e){capture(e);return}
  const money=value=>'NT$ '+Number(value).toLocaleString('zh-TW',{maximumFractionDigits:2})
  const amounts=payload.expectedAmounts
  const detail=[...cart.items.map(item=>`${item.productName}${item.specName?' / '+item.specName:''} × ${item.quantity}　${money(item.subtotal)}`),'',`收件人：${payload.receiverName}`,`電話：${payload.receiverPhone}`,`地址：${payload.shippingAddress}`,`付款方式：${payload.paymentMethod==='COD'?'貨到付款':'模擬付款（非正式金流）'}`,payload.remark?`備註：${payload.remark}`:'','',`商品金額：${money(amounts.goodsTotal)}`,`優惠碼：${payload.couponCode||'未使用'}`,`優惠折抵：− ${money(amounts.discountAmount)}`,`運費：${money(amounts.shippingFee)}`,`合計：${money(amounts.totalAmount)}`].join('\n')
  const accepted=await confirmation.ask({title:'確認此次訂單',description:'請核對商品、收件資訊與合計。確認後會建立訂單；優惠名額與庫存仍以下單時的驗證為準。',detail,cancelLabel:'返回修改',confirmLabel:'確認成立訂單',tone:'primary'})
  auth.syncFromStorage()
  if(!accepted||!auth.isLoggedIn||epoch!==auth.epoch||fingerprint!==reviewFingerprint.value||checkoutBlocked.value||couponDecision.value||quoting.value||submitting.value)return
  let attempt
  try {
    attempt = saveCheckoutAttempt(auth, payload, cart.items)
    pendingAttempt.value = attempt
  } catch (e) { storageError.value = '無法保存下單暫存，本次未送出：' + e.message; return }
  await sendAttempt(attempt, true)
}

async function finishOrder(order, sequence, attempt) {
  if (sequence !== submitSequence) return
  if (!order || !Number.isSafeInteger(Number(order.id)) || Number(order.id) <= 0) throw new Error('下單回應缺少有效訂單編號')
  createdOrder.value = order
  try { settleCheckoutAttempt(auth, attempt.requestId) } catch { storageError.value = '訂單已成立，但暫存清除失敗；請勿再次下單' }
  pendingAttempt.value = null
  outcomeUnknown.value = false
  // Replay may refer to an older order: do not clear goods added after that order.
  try { await cart.fetch() } catch { /* Route/header refresh can retry; the order is already confirmed. */ }
  if (sequence !== submitSequence) return
  const failure = await router.push({ name: 'order-detail', params: { id: order.id } })
  if (failure && sequence === submitSequence) navigationError.value = '訂單已成立，但未能開啟詳情，請使用下方連結查看。'
}

async function lookupResult() {
  if (submitting.value || checkingResult.value || loading.value || createdOrder.value) return
  const attempt = restoreAttempt()
  if (!attempt) return
  clear(); recoveryMessage.value = ''
  const sequence = ++submitSequence
  checkingResult.value = true
  try { await finishOrder(await orderApi.byRequestId(attempt.requestId), sequence, attempt) }
  catch (e) {
    if (sequence !== submitSequence) return
    if (createdOrder.value) navigationError.value = '訂單已成立，但未能開啟詳情，請使用下方連結查看。'
    else if (e.status === 404) recoveryMessage.value = attempt.payload
      ? '尚未查到已成立訂單，原請求可能仍在處理；請以相同識別碼和原內容重試，勿換新碼。'
      : '尚未查到已成立訂單，原收件內容已逾期；請聯絡商店確認，勿換新識別碼重送。'
    else capture(e)
  } finally { if (sequence === submitSequence) checkingResult.value = false }
}
async function retryPending() {
  if (submitting.value || checkingResult.value || loading.value || createdOrder.value || identityConflict.value || !retryCartMatches.value) return
  const attempt = restoreAttempt()
  if (attempt?.payload?.cartSnapshot && attempt.payload.expectedAmounts) await sendAttempt(attempt)
}
async function sendAttempt(attempt, isNew = false) {
  clear(); recoveryMessage.value = ''
  navigationError.value = ''
  syncMessage.value = ''
  const sequence = ++submitSequence
  let requestStarted = false
  let preflightStep = 'contract'
  submitting.value = true
  try {
    const contract = await orderApi.requestIdentity()
    if (sequence !== submitSequence) return
    if (contract?.version !== 3) throw new Error('後端尚未支援本版結帳金額確認')
    if (!isNew) {
      preflightStep = 'cart'
      await cart.fetch()
      if (sequence !== submitSequence) return
      if (!cart.isEmpty && attempt.cartSignature !== checkoutCartSignature(cart.items)) {
        capture(new Error('購物車內容已變更，本次未重送；請先查回原訂單，仍查不到時聯絡商店確認。'))
        outcomeUnknown.value = true
        return
      }
    }
    requestStarted = true
    await finishOrder(await orderApi.create({ ...attempt.payload }), sequence, attempt)
  } catch (e) {
    if (sequence !== submitSequence) return
    if (createdOrder.value) {
      navigationError.value = '訂單已成立，但未能開啟詳情，請使用下方連結查看。'
      return
    }
    if (!requestStarted) {
      capture(new Error(preflightStep === 'cart' ? '無法同步購物車，本次未重送：' + e.message
        : '無法確認後端支援下單識別碼，本次未送出；請確認後端已更新。' + e.message))
      if (isNew) {
        try { settleCheckoutAttempt(auth, attempt.requestId) } catch { storageError.value = '下單暫存清除失敗，請先查詢訂單' }
        pendingAttempt.value = null
      }
      outcomeUnknown.value = !!pendingAttempt.value
      return
    }
    capture(e)
    if ([400,409,422].includes(e.status) && e.code !== 'REQUEST_ID_CONFLICT') {
      try { settleCheckoutAttempt(auth, attempt.requestId) } catch { storageError.value = '下單暫存清除失敗，請先查詢訂單' }
      pendingAttempt.value = null
      outcomeUnknown.value = false
    }
    if (isCouponFailure(e) && e.status===400) {
      clear();clearCoupon();couponDecision.value=true;couponError.value=couponFailureMessage(e)
    }
    if (e.status === 409 && e.code !== 'REQUEST_ID_CONFLICT') {
      needsSync.value = true
      clearCoupon()
    }
    if (pendingAttempt.value) {
      outcomeUnknown.value = true
      identityConflict.value = e.code === 'REQUEST_ID_CONFLICT'
      clearCoupon()
    }
  } finally {
    if (sequence === submitSequence) submitting.value = false
  }
}
function fillDemo() { if (confirmation.pending.value || submitting.value || pendingAttempt.value || createdOrder.value || !loaded.value) return; Object.assign(form, checkoutDraft()); clear() }
</script>

<template>
  <div class="page">
    <div class="container">
      <h1 class="page-title">結帳</h1>
      <p class="page-sub">填寫收件資訊，送出後即建立訂單</p>
      <button v-if="loaded" class="btn btn-ghost btn-sm mb-2" :disabled="loading || submitting" @click="load">{{ loading ? '同步中…' : '重新同步結帳資料' }}</button>

      <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
      <div v-if="storageError" class="alert alert-error" role="alert">{{ storageError }} <button class="btn btn-ghost btn-sm" :disabled="submitting || checkingResult" @click="restoreAttempt">重試讀取下單暫存</button></div>
      <section v-if="submitting && !createdOrder" class="checkout-result-panel checkout-processing" aria-label="下單處理狀態">
        <p class="checkout-result-eyebrow">ORDER / 處理中</p><h2>正在確認此次訂單</h2>
        <p role="status">訂單正在送出，請勿重複操作；離開頁面不會取消下單。</p>
        <p v-if="pendingAttempt" class="checkout-request-id">下單識別碼：{{ pendingAttempt.requestId }}</p>
        <p class="muted">系統會在收到結果後開啟訂單詳情；若連線中斷，請用同一識別碼查回結果。</p>
      </section>
      <section v-if="outcomeUnknown && !submitting && !createdOrder" class="alert alert-error checkout-result-panel" role="alert" aria-label="下單結果待確認">
        <p class="checkout-result-eyebrow">ORDER / 等待確認</p><h2>先確認訂單，再決定下一步</h2>
        <p>尚無法確認訂單是否成立，請先查看我的訂單；不要直接重複送出。</p>
        <ol class="checkout-recovery-steps"><li>先查詢此次下單結果，或查看我的訂單。</li><li>「尚未查到」不代表下單失敗；原請求可能仍在處理。</li><li>若要重試，只使用下方的相同識別碼與原內容；仍無法確認時請聯絡商店。</li></ol>
        <RouterLink class="btn btn-ghost btn-sm" to="/orders">查看我的訂單</RouterLink>
        <p>下單識別碼：<span style="overflow-wrap:anywhere">{{ pendingAttempt?.requestId }}</span></p>
        <div class="row" style="flex-wrap:wrap">
          <button class="btn btn-ghost btn-sm" :disabled="loading || submitting || checkingResult" @click="lookupResult">{{ checkingResult ? '查詢中…' : '查詢此次下單結果' }}</button>
          <button class="btn btn-ghost btn-sm" :disabled="!pendingAttempt?.payload?.cartSnapshot || !pendingAttempt?.payload?.expectedAmounts || !retryCartMatches || identityConflict || loading || submitting || checkingResult" @click="retryPending">以相同識別碼重試</button>
        </div>
        <p v-if="recoveryMessage" role="status">{{ recoveryMessage }}</p>
        <p v-if="!pendingAttempt?.payload">原收件內容已逾暫存期限，僅能查回結果；若仍查不到，請聯絡商店確認，勿另外重送。</p>
        <p v-else-if="!pendingAttempt.payload.cartSnapshot || !pendingAttempt.payload.expectedAmounts">此為舊版下單暫存，僅能查回結果；若仍查不到，請聯絡商店確認，勿更換識別碼重送。</p>
        <p v-else-if="loaded && !retryCartMatches">購物車商品或數量與原請求不同，請先查回原訂單；仍查不到時聯絡商店，勿直接重送。</p>
        <p class="muted">只重送原內容，不套用新地址或優惠。收件資料暫存於目前分頁，兩小時後存取時移除；成功確認、明確拒絕或更換登入會清除。</p>
      </section>
      <section v-if="createdOrder" class="alert alert-ok" role="status">
        <p>訂單已成立：{{ createdOrder.orderNo || createdOrder.id }}，請勿再次下單。</p>
        <p v-if="navigationError">{{ navigationError }}</p>
        <RouterLink :to="{ name: 'order-detail', params: { id: createdOrder.id } }">查看已成立訂單</RouterLink>
      </section>
      <p v-if="syncMessage" role="status">{{ syncMessage }}</p>
      <p v-if="needsSync" class="alert alert-error" role="alert">訂單資料有衝突，請重新同步結帳資料後確認庫存與金額，再自行送出。</p>
      <p v-if="loaded && loadError" role="alert">目前顯示上次成功載入的資料，重試成功前無法下單。</p>
      <div v-if="loaded && stockIssues.length" class="alert alert-error" role="alert">
        <p v-for="item in stockIssues" :key="item.id">{{ item.productName }}：需要 {{ item.quantity }} 件，目前庫存 {{ item.stock }} 件。</p>
        <RouterLink class="btn btn-ghost btn-sm" to="/cart">返回購物車調整</RouterLink>
      </div>
      <p v-if="loaded && cart.isEmpty" role="status">購物車是空的，請先加入商品。<RouterLink to="/products">前往選購</RouterLink></p>
      <div v-if="loading" class="spinner" role="status">載入結帳資料中…</div>
      <div v-if="loadError" class="alert alert-error" role="alert">{{ loadError }} <button class="btn btn-ghost" @click="load" :disabled="loading">重試載入</button></div>
      <div v-if="addressWarning" class="alert alert-error" role="alert">{{ addressWarning }} <button class="btn btn-ghost" @click="load" :disabled="loading">重試地址</button></div>

      <div v-if="loaded" class="grid checkout-grid">
        <div class="card" style="padding: 24px;">
          <fieldset class="checkout-fields" :disabled="confirmation.pending.value || submitting || !!pendingAttempt || !!createdOrder">
<DemoFillButton :disabled="confirmation.pending.value || submitting || !!pendingAttempt || !!createdOrder" @fill="fillDemo" />
          <div v-if="addresses.length" class="mb-2">
            <label style="font-size: 13px; font-weight: 600; color: var(--ink-soft);">使用已儲存地址</label>
            <div class="row" style="flex-wrap: wrap; margin-top: 8px;">
              <button v-for="a in addresses" :key="a.id" class="btn btn-ghost btn-sm" :disabled="loading || submitting" @click="useAddress(a)">
                {{ a.receiverName }} · {{ a.city }}{{ a.line1 }}
              </button>
            </div>
          </div>

          <div class="field">
            <label for="checkout-name">收件人 *</label>
            <input id="checkout-name" v-model="form.receiverName" class="input" placeholder="王小明" maxlength="100" :aria-invalid="!!fields.receiverName" :aria-describedby="fields.receiverName ? 'checkout-name-error' : undefined" />
            <FieldError id="checkout-name-error" :message="fields.receiverName" />
          </div>
          <div class="field">
            <label for="checkout-phone">收件電話 *</label>
            <input id="checkout-phone" v-model="form.receiverPhone" class="input" placeholder="0912345678" maxlength="30" :aria-invalid="!!fields.receiverPhone" :aria-describedby="fields.receiverPhone ? 'checkout-phone-error' : undefined" />
            <FieldError id="checkout-phone-error" :message="fields.receiverPhone" />
          </div>
          <div class="field">
            <label for="checkout-address">收件地址 *</label>
            <input id="checkout-address" v-model="form.shippingAddress" class="input" placeholder="台北市大安區復興南路一段 100 號 5 樓" maxlength="500" :aria-invalid="!!fields.shippingAddress" :aria-describedby="fields.shippingAddress ? 'checkout-address-error' : undefined" />
            <FieldError id="checkout-address-error" :message="fields.shippingAddress" />
          </div>
          <div class="field">
            <label for="checkout-remark">備註</label>
            <textarea id="checkout-remark" v-model="form.remark" class="textarea" maxlength="500" placeholder="例如：請於下午時段配送"></textarea>
            <FieldError :message="fields.remark" />
          </div>
          <div class="field">
            <label for="payment-method">付款方式</label>
            <select id="payment-method" v-model="form.paymentMethod" class="select">
              <option value="COD">貨到付款</option>
              <option value="MOCK">模擬付款（測試用途）</option>
            </select>
            <FieldError :message="fields.paymentMethod" />
            <p class="muted">金額以右側後端報價為準；若送出前價格、優惠或運費變更，需重新同步並確認後才能下單。</p>
          </div>
          </fieldset>
        </div>

        <div class="card" style="padding: 20px;">
          <h3 style="margin: 0 0 14px;">訂單摘要</h3>
          <form aria-label="套用優惠券" @submit.prevent="applyCoupon">
            <DemoFillButton :disabled="confirmation.pending.value || quoting || submitting || !!pendingAttempt || !!createdOrder" label="一鍵填入優惠碼" @fill="couponCode = 'MEOW100'" />
            <p class="muted" style="font-size:12px">演示優惠碼 MEOW100，請先在後台建立並啟用；使用資格依實際優惠券規則判定。</p>
            <label for="coupon-code">優惠碼</label>
            <input id="coupon-code" v-model="couponCode" class="input" maxlength="32" placeholder="輸入優惠碼" :disabled="confirmation.pending.value || quoting || submitting || !!pendingAttempt || !!createdOrder" />
            <div class="row mt-2" style="flex-wrap:wrap"><button class="btn btn-ghost btn-sm" :disabled="quoting||submitting||checkoutBlocked||!couponCode.trim()">套用優惠券</button><button v-if="couponCode.trim() || couponDecision" type="button" class="btn btn-ghost btn-sm" :disabled="quoting||submitting||checkoutBlocked" @click="removeCoupon">移除優惠並確認原價</button></div>
            <p v-if="quoting" role="status">驗證優惠中…</p>
            <p v-if="couponError" class="alert alert-error" role="alert">{{ couponError }}</p>
            <p v-if="couponQuote" role="status">已套用 {{ couponQuote.couponCode }}，折抵 NT$ {{ couponQuote.discountAmount }}</p>
            <p v-if="couponQuote" class="muted">報價不保留名額，下單時會再次驗證優惠。</p>
            <p v-if="couponDecision && !pendingAttempt" role="status">優惠尚未確認，請重新套用，或移除優惠並確認原價後再下單。</p>
            <p v-if="couponMessage" role="status">{{ couponMessage }}</p>
            <p class="muted">先按套用才會帶入下單；預覽不保留名額，下單時重新驗證。免運門檻依折扣前商品金額計算。</p>
          </form>
          <div v-for="item in cart.items" :key="item.id" class="row-between" style="margin-bottom: 8px; font-size: 14px;">
            <span class="muted">{{ item.productName }} × {{ item.quantity }}</span>
            <span>NT$ {{ Number(item.subtotal).toLocaleString() }}</span>
          </div>
          <hr style="border: none; border-top: 1px solid var(--line); margin: 14px 0;" />
          <div v-if="summaryAmounts" class="row-between"><span>商品金額</span><span>NT$ {{ summaryAmounts.goodsTotal }}</span></div>
          <div v-if="summaryAmounts" class="row-between"><span>優惠折抵</span><span>− NT$ {{ summaryAmounts.discountAmount }}</span></div>
          <div class="row-between"><span>運費</span><span>{{ summaryAmounts ? 'NT$ '+summaryAmounts.shippingFee : '尚未取得報價' }}</span></div>
          <div class="row-between" style="font-size: 18px; font-weight: 800;">
            <span>合計</span>
            <span style="color: var(--brand);">{{ displayedTotal == null ? '尚未取得報價' : 'NT$ '+Number(displayedTotal).toLocaleString() }}</span>
          </div>
          <button
            class="btn btn-primary btn-block mt-3"
            :disabled="confirmation.pending.value || submitting || quoting || couponDecision || checkoutBlocked || !form.receiverName.trim() || !form.receiverPhone.trim() || !form.shippingAddress.trim()"
            @click="submit"
          >
            {{ submitting ? '處理中…' : '確認下單' }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <ConfirmationDialog :request="leaveConfirmation.request.value || confirmation.request.value" @answer="answerConfirmation" />
</template>
<style scoped>
.checkout-grid{grid-template-columns:minmax(0,1fr) 320px;align-items:start}
.checkout-fields{border:0;padding:0;margin:0;min-width:0}
.checkout-result-panel{padding:24px;border-radius:20px;margin:0 0 20px;overflow-wrap:anywhere}
.checkout-processing{border:1px solid var(--line);background:var(--brand-soft)}
.checkout-result-panel h2{font-size:22px;margin:8px 0 12px}.checkout-result-eyebrow{font-size:11px;letter-spacing:1.3px;color:var(--ink-soft)}
.checkout-request-id{font-size:13px;overflow-wrap:anywhere}.checkout-recovery-steps{padding-left:22px;font-size:14px;line-height:1.8}.checkout-recovery-steps li+li{margin-top:6px}
@media(max-width:800px){.checkout-grid{grid-template-columns:minmax(0,1fr)}}
</style>
