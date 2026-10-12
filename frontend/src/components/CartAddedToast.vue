<script setup>
import { useCartStore } from '@/stores/cart'
const cart = useCartStore()
</script>
<template>
  <div class="cart-toast-region" role="status" aria-live="polite" aria-atomic="true">
    <Transition name="cart-toast">
      <div v-if="cart.addedNotice" class="cart-added-toast">
        <span class="cart-toast-check" aria-hidden="true">✓</span><span>{{ cart.addedNotice }}</span>
        <button type="button" aria-label="關閉加入購物車提示" @click="cart.clearAddedNotice()">×</button>
      </div>
    </Transition>
  </div>
</template>
<style scoped>
.cart-toast-region{position:fixed;right:24px;bottom:24px;z-index:80;pointer-events:none}
.cart-added-toast{display:flex;align-items:center;gap:12px;min-width:240px;max-width:calc(100vw - 32px);padding:14px 16px;background:#fffefa;border:1px solid #dce3d5;box-shadow:0 8px 30px #253f381a;border-radius:10px;color:#304e42;font-size:13px;pointer-events:auto}
.cart-toast-check{display:grid;place-items:center;width:24px;height:24px;border-radius:50%;background:#edf2e9;font-size:14px}
.cart-added-toast button{border:0;background:transparent;color:#798475;margin-left:auto;padding:2px 6px;font-size:20px;cursor:pointer}
.cart-toast-enter-active,.cart-toast-leave-active{transition:opacity .18s,transform .18s}.cart-toast-enter-from,.cart-toast-leave-to{opacity:0;transform:translateY(8px)}
@media(max-width:600px){.cart-toast-region{right:16px;bottom:20px}.cart-added-toast{min-width:220px}}
@media(prefers-reduced-motion:reduce){.cart-toast-enter-active,.cart-toast-leave-active{transition:none}}
</style>
