<script setup>
import { computed, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { adminPath } from '@/config/surface'
import AdminNavIcon from '@/components/AdminNavIcon.vue'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const pageTitle = computed(() => ({'admin-category-new':'新增分類','admin-category-edit':'編輯分類','admin-product-skus':'SKU 規格管理','admin-product-images':'商品圖片管理','admin-product-new':'新增商品','admin-product-edit':'編輯商品','admin-order-detail':'訂單詳情','admin-order-shipment':'出貨登錄','admin-coupon-new':'新增優惠券','admin-coupon-edit':'編輯優惠券','admin-coupons':'優惠券管理','admin-member-detail':'會員詳情','admin-member-history':'會員操作紀錄','admin-members':'會員管理','forgot-password':'忘記密碼','reset-password':'重設密碼','admin-movements':'庫存異動紀錄','admin-inventory':'低庫存清單'})[route.name] || ({ products: '商品管理', categories: '分類管理', orders: '訂單管理' })[route.params.section] || '管理員登入')
onUnmounted(auth.startSync())
watch([() => auth.epoch, () => auth.member?.role], () => {
  if (route.meta.requiresAdmin && (!auth.isLoggedIn || auth.member?.role !== 'ADMIN'))
    router.replace({ name: 'admin-login', query: { redirect: route.fullPath } })
})
async function logout() {
  await auth.logout()
  await router.replace({ name: 'admin-login' })
}
</script>
<template>
  <div class="admin-shell" :class="{ 'admin-authenticated': auth.isLoggedIn && auth.member?.role === 'ADMIN' }">
  <a class="skip-link" href="#admin-main">跳至主要內容</a>
  <aside v-if="auth.isLoggedIn && auth.member?.role === 'ADMIN'" class="admin-sidebar">
      <RouterLink to="/" class="admin-brand">喵喵商城 <span>Admin</span><small>MEOW DAILY / OPERATIONS</small></RouterLink>
      <p class="sidebar-label">商店營運</p>
      <nav class="admin-navigation" aria-label="後台主選單">
        <RouterLink :to="adminPath('products')" :class="{ 'router-link-active': ['admin-product-new','admin-product-edit','admin-product-skus','admin-product-images'].includes(route.name) }"><AdminNavIcon name="products" />商品管理</RouterLink>
        <RouterLink :to="adminPath('categories')" :class="{ 'router-link-active': ['admin-category-new','admin-category-edit'].includes(route.name) }"><AdminNavIcon name="categories" />分類管理</RouterLink>
        <RouterLink :to="adminPath('orders')" :class="{ 'router-link-active': ['admin-order-detail','admin-order-shipment'].includes(route.name) }"><AdminNavIcon name="orders" />訂單管理</RouterLink>
        <RouterLink :to="adminPath('inventory')"><AdminNavIcon name="inventory" />低庫存清單</RouterLink>
        <RouterLink :to="adminPath('movements')"><AdminNavIcon name="movements" />庫存異動紀錄</RouterLink>
        <RouterLink :to="adminPath('members')" :class="{ 'router-link-active': ['admin-member-detail','admin-member-history'].includes(route.name) }"><AdminNavIcon name="members" />會員管理</RouterLink>
        <RouterLink :to="adminPath('coupons')" :class="{ 'router-link-active': ['admin-coupon-new','admin-coupon-edit'].includes(route.name) }"><AdminNavIcon name="coupons" />優惠券管理</RouterLink>
      </nav>
      <div class="sidebar-note"><strong>專注每一份日常</strong><p>商品、分類與訂單，<br />在這裡井然有序。</p><small>獨立管理後台</small></div>
  </aside>
  <div class="admin-workspace">
  <header class="admin-topbar">
    <div class="admin-topbar-inner">
      <div><RouterLink to="/" class="admin-mobile-brand">喵喵商城 Admin</RouterLink><p class="admin-breadcrumb">商店營運 <span>/</span> {{ pageTitle }}</p></div>
      <div v-if="auth.isLoggedIn" class="admin-account-actions">
        <div class="admin-account-profile" :title="auth.displayName">
          <span class="admin-account-avatar" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" focusable="false"><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></svg></span>
          <span class="admin-account-name">{{ auth.displayName }}</span>
        </div>
        <span class="account-action-divider" aria-hidden="true"></span>
        <button type="button" class="logout-icon-button" aria-label="登出" title="登出" @click="logout">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M9 4H5v16h4M17 8l4 4-4 4M9 12h12" /></svg>
        </button>
      </div>
    </div>
  </header>
  <main id="admin-main" tabindex="-1">
    <RouterView v-if="!route.meta.requiresAdmin || (auth.isLoggedIn && auth.member?.role === 'ADMIN')"
      :key="route.meta.requiresAdmin ? auth.epoch : undefined" />
  </main>
  <footer class="admin-footer">喵喵商城 Admin · 獨立管理後台 · 本機示範專案</footer>
  </div></div>
</template>
