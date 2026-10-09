<script setup>
import FeedbackNotice from '@/components/FeedbackNotice.vue'
import { onMounted, onBeforeUnmount, computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import ProductImage from '@/components/ProductImage.vue'

const cart = useCartStore()
const router = useRouter()
const error = ref('')
const loaded = ref(false)
const changing = ref(false)
const syncing = ref(false), needsSync = ref(false), message = ref('')
const busy=computed(()=>changing.value||syncing.value||cart.loading)
const stockIssue=computed(()=>cart.items.some(item=>item.quantity>item.stock||item.stock<=0))
let version=0
onBeforeUnmount(()=>{++version})

async function load() {
  // fetch() shares the in-flight request with the header badge.
  if(syncing.value||changing.value)return
  const current=++version,hadData=loaded.value
  syncing.value=true;error.value='';message.value=''
  try {
    await cart.fetch()
    if(current!==version)return
    loaded.value=true;needsSync.value=false
    if(hadData)message.value='購物車已同步最新庫存與價格'
  }
  catch (e) { if(current===version){error.value=e.message;needsSync.value=hadData} }
  finally { if(current===version)syncing.value=false }
}
onMounted(load)

async function updateQty(item, qty) {
  if (busy.value) return
  if (!Number.isInteger(qty) || qty < 1 || qty > item.stock) { error.value = '請輸入庫存範圍內的整數數量'; return }
  changing.value = true; error.value = '';message.value=''
  try { await cart.updateItem(item.id, qty);needsSync.value=false } catch (e) { error.value = e.message;needsSync.value=true }
  finally { changing.value = false }
}

async function remove(item) {
  if (busy.value) return
  changing.value = true; error.value = '';message.value=''
  try { await cart.removeItem(item.id);needsSync.value=false } catch (e) { error.value = e.message;needsSync.value=true }
  finally { changing.value = false }
}
</script>

<template>
  <div class="page">
    <div class="container">
      <h1 class="page-title">購物車</h1>
      <p class="page-sub">確認商品與數量後前往結帳</p>

      <button class="btn btn-ghost mb-2" :disabled="busy" @click="load">{{ syncing?'同步中…':'重新同步購物車' }}</button>
      <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost" :disabled="busy" @click="load">{{ loaded?'重試同步':'重試載入' }}</button></div>
      <p v-if="needsSync && loaded" class="muted">以下為上次成功讀取的資料，請重新同步後再結帳。</p>
<FeedbackNotice :message="message" />
      <div v-if="!loaded && busy" class="spinner">載入中…</div>

      <div v-else-if="loaded && cart.isEmpty" class="empty card">
        <span class="emoji">🛒</span>
        購物車是空的
        <div class="mt-2"><RouterLink to="/products" class="btn btn-primary">去逛逛</RouterLink></div>
      </div>

      <div v-else-if="loaded" class="grid" style="grid-template-columns: 1fr 320px; align-items: start;" :aria-busy="busy">
        <div class="card">
          <table class="table">
            <thead>
              <tr><th>商品</th><th>單價</th><th>數量</th><th>小計</th><th></th></tr>
            </thead>
            <tbody>
              <tr v-for="item in cart.items" :key="item.id">
                <td>
                  <div class="row">
                    <ProductImage :src="item.imageUrl" :alt="item.productName" style="width: 52px; height: 52px; border-radius: 8px; object-fit: cover;" />
                    <div>
                      <div style="font-weight: 600;">{{ item.productName }}</div>
                      <div class="muted" style="font-size: 12px;">{{ item.specName }}</div>
                      <div class="muted">目前庫存：{{ item.stock }}</div>
                      <div v-if="item.quantity>item.stock || item.stock<=0" class="field-error">庫存不足，請調整數量或移除商品</div>
                    </div>
                  </div>
                </td>
                <td>NT$ {{ Number(item.unitPrice).toLocaleString() }}</td>
                <td>
                  <input
                    type="number" min="1" :max="item.stock" :value="item.quantity"
                    :disabled="busy" :aria-label="'商品數量：'+item.productName"
                    class="input" style="width: 76px;"
                    @change="updateQty(item, Number($event.target.value))"
                  />
                </td>
                <td style="font-weight: 700;">NT$ {{ Number(item.subtotal).toLocaleString() }}</td>
                <td><button class="btn btn-danger btn-sm" :disabled="busy" @click="remove(item)">移除</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card" style="padding: 20px;">
          <h3 style="margin: 0 0 14px;">訂單摘要</h3>
          <div class="row-between" style="margin-bottom: 8px;">
            <span class="muted">商品件數</span><span>{{ cart.totalQuantity }} 件</span>
          </div>
          <div class="row-between" style="margin-bottom: 8px;">
            <span class="muted">商品金額</span><span>NT$ {{ Number(cart.totalAmount).toLocaleString() }}</span>
          </div>
          <div class="row-between" style="margin-bottom: 8px;">
            <span class="muted">運費</span>
            <span>{{ cart.totalAmount >= 1000 ? '免運' : 'NT$ 80' }}</span>
          </div>
          <hr style="border: none; border-top: 1px solid var(--line); margin: 14px 0;" />
          <div class="row-between" style="font-size: 18px; font-weight: 800;">
            <span>合計</span>
            <span style="color: var(--brand);">NT$ {{ Number(cart.totalAmount + (cart.totalAmount >= 1000 ? 0 : 80)).toLocaleString() }}</span>
          </div>
          <button class="btn btn-primary btn-block mt-3" :disabled="busy || needsSync || stockIssue" @click="router.push('/checkout')">前往結帳</button>
        </div>
      </div>
    </div>
  </div>
</template>
