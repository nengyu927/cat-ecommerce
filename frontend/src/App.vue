<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useWishlistStore } from '@/stores/wishlist'
import { isStorefrontApp } from '@/config/surface'
import CartDrawer from '@/components/CartDrawer.vue'
import HeaderCartList from '@/components/HeaderCartList.vue'
import { useStorefrontFeedback } from '@/composables/useStorefrontFeedback'

useStorefrontFeedback()

const auth = useAuthStore()
const cart = useCartStore()
const wishlist = useWishlistStore()
const route = useRoute()
const router = useRouter()
const searchTerm = ref('')
const hoverPanel=ref('')
let hoverTimer
function closeHover(){clearTimeout(hoverTimer);hoverPanel.value=''}
function openHover(panel,event){
 clearTimeout(hoverTimer)
 if(event?.type==='pointerenter'&&(event.pointerType!=='mouse'||!matchMedia('(hover: hover) and (pointer: fine)').matches))return
 if(!cart.previewOpen)hoverPanel.value=panel
}
function leaveHover(event){
 const wrapper=event.currentTarget
 clearTimeout(hoverTimer)
 hoverTimer=setTimeout(()=>{if(!wrapper.contains(document.activeElement))hoverPanel.value=''},160)
}
function blurHover(event){if(!event.currentTarget.contains(event.relatedTarget))closeHover()}
function outsideHover(event){if(!event.target.closest?.('.header-popover'))closeHover()}
function escapeHover(event){if(event.key==='Escape')closeHover()}
onMounted(()=>{document.addEventListener('pointerdown',outsideHover);document.addEventListener('keydown',escapeHover)})
onUnmounted(()=>{clearTimeout(hoverTimer);document.removeEventListener('pointerdown',outsideHover);document.removeEventListener('keydown',escapeHover)})
watch([()=>route.fullPath,()=>auth.epoch,()=>cart.previewOpen],closeHover,{flush:'sync'})
function searchProducts() {
  const q = searchTerm.value.trim()
  router.push(q ? { name: 'search', query: { q } } : { name: 'products' })
}
const stopSync = auth.startSync()
onUnmounted(stopSync)
function refreshCart() { if (auth.isLoggedIn) cart.fetch().catch(() => { /* Pages expose their own retry UI. */ }) }

onMounted(() => {
  refreshCart()
  if (auth.isLoggedIn) wishlist.fetch().catch(() => {})
})

// refresh cart badge whenever the route changes (e.g. after adding an item)
watch(() => route.fullPath, () => {
  refreshCart()
})

watch([() => auth.epoch, () => auth.member?.role], () => {
  cart.reset()
  wishlist.reset()
  if (auth.isLoggedIn) wishlist.fetch().catch(() => {})
  if (!auth.isLoggedIn && route.meta.requiresAuth) {
    router.replace({ name: 'login', query: auth.reason === 'password-changed'
      ? { reason: 'password-changed' } : { redirect: route.fullPath } })
  } else if (route.meta.requiresAdmin && auth.member?.role !== 'ADMIN') router.replace('/')
  else refreshCart()
})

function logout() {
  auth.logout()
  cart.reset()
  wishlist.reset()
}
</script>

<template>
  <div class="storefront-shell">
  <a class="skip-link" href="#store-main">跳至主要內容</a>
  <div class="announcement">陪主子，把每一天過得剛剛好。<span>貓咪日常選品 · 喵喵商城</span></div>
  <header class="site-header">
    <div class="container inner">
      <RouterLink to="/" class="logo"><span class="logo-mark" aria-hidden="true">ฅ</span><span class="logo-word">喵喵商城<small>MEOW DAILY</small></span></RouterLink>
      <form class="header-search" role="search" @submit.prevent="searchProducts">
        <input v-model="searchTerm" type="search" aria-label="搜尋商城商品" placeholder="找找主子的日常好物…" maxlength="100" />
        <button type="submit" aria-label="搜尋商品">搜尋 <span aria-hidden="true">↗</span></button>
      </form>
      <div class="header-actions">
        <div class="header-popover" @pointerenter="openHover('cart',$event)" @pointerleave="leaveHover" @focusin="openHover('cart')" @focusout="blurHover" @keydown.esc="closeHover">
        <button type="button" @click="cart.openPreview()" aria-haspopup="dialog" :aria-expanded="cart.previewOpen || hoverPanel==='cart'" class="cart-link header-icon-link" :aria-label="cart.count > 0 ? `購物車，${cart.count} 件商品` : '購物車'" title="購物車">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 3h2l2.5 12h11l2-8H6" /><path d="M8 15l-1 3h12" /><circle cx="9" cy="21" r="1" /><circle cx="18" cy="21" r="1" /></svg>
          <span v-if="cart.count > 0" :key="cart.count" class="cart-badge" aria-hidden="true">{{ cart.count }}</span>
        </button>
        <HeaderCartList v-if="hoverPanel==='cart' && !cart.previewOpen" />
        </div>
        <template v-if="auth.isLoggedIn">
          <div class="header-popover" @pointerenter="openHover('member',$event)" @pointerleave="leaveHover" @focusin="openHover('member')" @focusout="blurHover" @keydown.esc="closeHover">
          <RouterLink to="/account" class="header-icon-link member-icon-link" :aria-expanded="hoverPanel==='member'" aria-controls="member-shortcuts" :aria-label="auth.displayName" :title="`會員中心：${auth.displayName}`">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></svg>
          </RouterLink>
          <nav v-if="hoverPanel==='member'" id="member-shortcuts" class="header-popover-panel member-shortcuts" aria-label="會員功能選單">
            <strong>{{ auth.displayName }}</strong>
            <RouterLink to="/account">會員總覽</RouterLink>
            <RouterLink to="/account/profile">個人資料</RouterLink>
            <RouterLink to="/account/addresses">收件地址</RouterLink>
            <RouterLink to="/orders">訂單查詢</RouterLink>
            <RouterLink to="/wishlist">收藏商品</RouterLink>
            <RouterLink to="/account/password">變更密碼</RouterLink>
          </nav>
          </div>
          <button type="button" class="logout-icon-button" aria-label="登出" title="登出" @click="logout">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M9 4H5v16h4M17 8l4 4-4 4M9 12h12" /></svg>
          </button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="btn btn-ghost btn-sm">登入</RouterLink>
          <RouterLink to="/register" class="btn btn-primary btn-sm">註冊</RouterLink>
        </template>
      </div>
    </div>
    <div class="store-nav-bar"><div class="container">
      <nav class="nav" aria-label="商城主選單">
        <RouterLink to="/">首頁</RouterLink>
        <RouterLink to="/products">所有商品</RouterLink>
        <RouterLink to="/categories">分類</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" to="/orders">我的訂單</RouterLink>
        <RouterLink v-if="auth.isLoggedIn" to="/wishlist">我的收藏</RouterLink>
        <template v-if="!isStorefrontApp">
          <RouterLink v-if="auth.member?.role === 'ADMIN'" to="/admin/products">管理後台</RouterLink>
          <RouterLink v-else to="/admin/login">後台登入</RouterLink>
        </template>
      </nav>
    </div></div>
  </header>

  <main id="store-main" tabindex="-1">
    <RouterView v-if="!route.meta.requiresAuth || auth.isLoggedIn" :key="route.meta.requiresAuth ? auth.epoch : undefined" />
  </main>
  <CartDrawer />

  <footer class="site-footer">
    <div class="container footer-grid">
      <div><strong>喵喵商城</strong><p>為主子挑選，也為日常留一點溫柔。</p><small>貓用品電商示範專案 · 非正式營運網站</small></div>
      <div><h2>逛逛商城</h2><RouterLink to="/products">所有商品</RouterLink><RouterLink to="/categories">商品分類</RouterLink></div>
      <div><h2>會員服務</h2><RouterLink to="/account">會員中心</RouterLink><RouterLink to="/orders">訂單查詢</RouterLink></div>
    </div><div class="container footer-bottom">MEOW DAILY · 好好照顧，一起生活。</div>
  </footer>
  </div>
</template>
