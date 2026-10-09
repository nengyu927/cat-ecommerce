<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import ProductImage from './ProductImage.vue'
import FeedbackNotice from './FeedbackNotice.vue'
const cart = useCartStore(), auth = useAuthStore(), route = useRoute(), router = useRouter()
const dialog = ref(null), busy = ref(false), error = ref(''), needsSync = ref(true)
const message = ref('')
const stockIssue = computed(() => cart.items.some(i => i.stock <= 0 || i.quantity > i.stock))
let version = 0, savedOverflow = null
function restoreScroll() {
  if (savedOverflow !== null) { document.body.style.overflow = savedOverflow; savedOverflow = null }
}
function close() { cart.closePreview() }
async function sync() {
  if (busy.value || !auth.isLoggedIn) return
  const operation = version
  busy.value = true; error.value = ''; message.value = ''; needsSync.value = true
  try { await cart.fetch(); if (operation === version) needsSync.value = false }
  catch (e) { if (operation === version) error.value = e.message }
  finally { if (operation === version) busy.value = false }
}
async function change(item, quantity) {
  if (busy.value || needsSync.value) return
  if (quantity !== null && (!Number.isInteger(quantity) || quantity < 1 || quantity > item.stock)) return
  const operation = version
  busy.value = true; error.value = ''; message.value = ''
  try {
    if (quantity === null) await cart.removeItem(item.id)
    else await cart.updateItem(item.id, quantity)
    if (operation === version) message.value = quantity === null ? '商品已移除' : '購物車數量已更新'
  } catch (e) {
    if (operation === version) { error.value = e.message; needsSync.value = true }
  } finally { if (operation === version) busy.value = false }
}
function navigate(path) { close(); router.push(path) }
function backdrop(event) {
  if (event.target !== dialog.value) return
  const box = dialog.value.getBoundingClientRect()
  if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) close()
}
watch(() => cart.previewOpen, async open => {
  const operation = ++version
  busy.value = false; error.value = ''; message.value = ''; needsSync.value = true
  if (!open) { dialog.value?.close(); restoreScroll(); return }
  await nextTick()
  if (operation !== version || !cart.previewOpen) return
  savedOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialog.value.showModal()
  if (auth.isLoggedIn) void sync()
})
watch(() => route.fullPath, close)
watch(() => auth.epoch, close)
onBeforeUnmount(() => { ++version; dialog.value?.close(); restoreScroll() })
const money = value => 'NT$ ' + Number(value || 0).toLocaleString()
</script>

<template>
  <dialog ref="dialog" class="cart-drawer" aria-labelledby="cart-preview-title" @cancel.prevent="close" @close="!dialog.open && close()" @click="backdrop">
    <template v-if="cart.previewOpen">
    <header class="drawer-header">
      <div><small>MEOW DAILY / YOUR BAG</small><h2 id="cart-preview-title">購物車預覽 <span>({{ cart.count }})</span></h2></div>
      <button type="button" class="drawer-close" aria-label="關閉購物車預覽" autofocus @click="close">×</button>
    </header>
    <div class="drawer-content">
      <template v-if="!auth.isLoggedIn">
        <div class="drawer-empty"><h3>登入後查看購物車</h3><p>收藏日常好物，為主子準備下一份驚喜。</p>
          <button class="btn btn-primary" @click="navigate({ name: 'login', query: { redirect: route.fullPath } })">前往登入</button></div>
      </template>
      <template v-else>
        <p v-if="busy" role="status" class="muted">正在同步購物車…</p>
        <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
        <FeedbackNotice :message="message" />
        <button v-if="needsSync && !busy" class="btn btn-ghost btn-sm" @click="sync">重試同步購物車</button>
        <div v-if="cart.isEmpty && !busy && !needsSync" class="drawer-empty"><h3>購物車還空著</h3><p>挑選一點主子的日常好物吧。</p><button class="btn btn-primary" @click="navigate('/products')">繼續逛逛</button></div>
        <article v-for="item in cart.items" :key="item.id" class="drawer-item">
          <ProductImage :src="item.imageUrl" :alt="item.productName" class="drawer-image" />
          <div class="drawer-item-body">
            <h3>{{ item.productName }}</h3><p class="muted">{{ item.specName }}</p>
            <small v-if="item.imageUrl?.startsWith('/product-images/')">商品示意圖</small>
            <p class="drawer-price">{{ money(item.unitPrice) }}</p>
            <p v-if="item.stock <= 0 || item.quantity > item.stock" class="field-error">庫存不足，請調整或移除</p>
            <div class="drawer-item-actions">
              <div class="drawer-quantity">
                <button :aria-label="'減少數量：' + item.productName" :disabled="busy || needsSync || item.quantity <= 1" @click="change(item, item.quantity - 1)">−</button>
                <span :aria-label="'預覽商品數量：' + item.productName">{{ item.quantity }}</span>
                <button :aria-label="'增加數量：' + item.productName" :disabled="busy || needsSync || item.quantity >= item.stock" @click="change(item, item.quantity + 1)">+</button>
              </div>
              <button class="drawer-remove" :disabled="busy || needsSync" :aria-label="'移除：' + item.productName" @click="change(item, null)">移除</button>
            </div>
          </div>
        </article>
      </template>
    </div>
    <footer v-if="auth.isLoggedIn" class="drawer-footer">
      <div class="drawer-subtotal"><span>商品小計</span><strong>{{ money(cart.totalAmount) }}</strong></div>
      <p class="muted">運費與優惠將於結帳時確認。</p>
      <button class="btn btn-primary btn-block" :disabled="busy || needsSync || cart.isEmpty || stockIssue" @click="navigate('/checkout')">前往結帳</button>
      <button class="btn btn-ghost btn-block" @click="navigate('/cart')">查看完整購物車</button>
    </footer>
    </template>
  </dialog>
</template>

<style scoped>
.cart-drawer{position:fixed;inset:0 0 0 auto;margin:0;border:0;padding:0;width:min(440px,100vw);max-width:100vw;height:100dvh;max-height:100dvh;color:#203d35;background:#fffdf8;box-shadow:-12px 0 48px #203d3526}
.cart-drawer[open]{display:flex;flex-direction:column;animation:drawer-in .22s ease-out}
.cart-drawer::backdrop{background:#122e2866}
.drawer-header{display:flex;justify-content:space-between;align-items:center;padding:24px;border-bottom:1px solid #e4e9df}
.drawer-header small{font-size:10px;letter-spacing:2px}.drawer-header h2{font-size:23px;margin:8px 0 0}.drawer-header h2 span{font-size:16px}
.drawer-close{border:1px solid #dce4d7;border-radius:50%;width:36px;height:36px;background:transparent;color:inherit;font-size:26px;cursor:pointer}
.drawer-content{flex:1;overflow:auto;padding:20px 24px;overscroll-behavior:contain}
.drawer-empty{text-align:center;padding:60px 0}.drawer-empty p{color:#67776e;line-height:1.8}
.drawer-item{display:flex;gap:16px;padding:20px 0;border-bottom:1px solid #e4e9df}
.drawer-image{width:96px;height:96px;object-fit:cover;border-radius:12px;background:#f2f3ed}
.drawer-item-body{flex:1;min-width:0}.drawer-item h3{font-size:15px;line-height:1.5;margin:0}.drawer-item p{margin:6px 0;font-size:13px}.drawer-item small{font-size:10px;color:#7c897e}.drawer-price{font-weight:700}
.drawer-item-actions{display:flex;justify-content:space-between;align-items:center;gap:8px;margin-top:12px}
.drawer-quantity{display:flex;align-items:center;border:1px solid #dce4d7;border-radius:8px;overflow:hidden}.drawer-quantity button{width:30px;height:30px;border:0;background:#eef2e9;color:inherit;cursor:pointer}.drawer-quantity span{min-width:30px;text-align:center;font-size:13px}
.drawer-remove{border:0;background:transparent;color:#65786c;text-decoration:underline;font-size:12px;cursor:pointer}
button:disabled{opacity:.45;cursor:not-allowed}.drawer-footer{padding:20px 24px max(20px,env(safe-area-inset-bottom));border-top:1px solid #e4e9df;background:white}
.drawer-subtotal{display:flex;justify-content:space-between;font-size:18px}.drawer-footer p{font-size:12px}.drawer-footer button+button{margin-top:8px}
@keyframes drawer-in{from{transform:translateX(100%)}to{transform:translateX(0)}}
@media(prefers-reduced-motion:reduce){.cart-drawer[open]{animation:none}}
</style>
