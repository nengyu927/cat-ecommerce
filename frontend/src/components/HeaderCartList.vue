<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {useCartStore} from '@/stores/cart'
import {useAuthStore} from '@/stores/auth'
import ProductImage from './ProductImage.vue'
const cart=useCartStore(),auth=useAuthStore(),error=ref(''),loading=ref(false)
let active=true
async function load(){
 if(loading.value||!auth.isLoggedIn)return
 loading.value=true;error.value=''
 try{await cart.fetch()}catch(e){if(active)error.value=e.message}
 finally{if(active)loading.value=false}
}
onMounted(load);onBeforeUnmount(()=>{active=false})
</script>
<template>
 <section class="header-popover-panel header-cart-panel" aria-label="購物車清單">
  <h2>購物車 <span>{{ cart.count }} 件</span></h2>
  <template v-if="auth.isLoggedIn">
   <p v-if="loading" role="status" class="muted">同步購物車中…</p>
   <p v-if="error" role="alert" class="field-error">{{ error }} <button class="btn btn-ghost btn-sm" @click="load">重試清單</button></p>
   <p v-if="!loading && !error && cart.isEmpty" class="muted">購物車還空著，逛逛商品吧。</p>
   <div class="header-cart-items">
    <div v-for="item in cart.items" :key="item.id" class="header-cart-item">
     <ProductImage :src="item.imageUrl" :alt="item.productName" />
     <div><strong>{{ item.productName }}</strong><small>{{ item.specName }} · {{ item.quantity }} 件</small><span>NT$ {{ Number(item.subtotal).toLocaleString() }}</span></div>
    </div>
   </div>
   <p class="header-cart-total">商品小計 <strong>NT$ {{ Number(cart.totalAmount).toLocaleString() }}</strong></p>
   <small class="muted">運費與優惠於結帳確認{{ error?'；目前顯示上次同步資料':'' }}。</small>
   <button class="btn btn-primary btn-block mt-2" @click="cart.openPreview()">開啟購物車側欄</button>
   <RouterLink to="/cart" class="btn btn-ghost btn-block mt-2">查看購物車頁面</RouterLink>
  </template>
  <template v-else><p class="muted">登入後查看購物車。</p><RouterLink to="/login" class="btn btn-primary btn-block">前往登入</RouterLink></template>
 </section>
</template>
