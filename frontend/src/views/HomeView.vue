<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProductStore } from '@/stores/product'
import ProductCard from '@/components/ProductCard.vue'
import catScene from '@/assets/cat-daily.svg'

const store = useProductStore()
const router = useRouter()
const error = ref('')
const categories = computed(() => store.categories.filter(c => !c.parentId))
const categoryIcons = ['◉', '✦', '◌', '✿', '▦', '◇']

async function load() {
  error.value = ''
  try {
  await Promise.all([
    store.fetchCategories(),
    store.fetchProducts({ page: 0, size: 8 })
  ])
  } catch (e) { error.value = e.message }
}
onMounted(load)

function goCategory(slug) {
  router.push({ name: 'category-products', params: { slug } })
}
</script>

<template>
  <div class="page home-page">
    <div class="container">
      <!-- hero -->
      <section class="daily-hero">
        <div class="hero-copy"><p class="eyebrow">MEOW DAILY / 貓咪的生活提案</p>
          <h1>主子的好日子，<br />從日常小事開始。</h1>
          <p class="hero-description">一碗喜歡的飯、一個好玩的玩具。<br />把吃得好、玩得開心的日常，慢慢準備齊全。</p>
          <div class="hero-actions"><RouterLink to="/products" class="btn btn-primary">開始選購 <span aria-hidden="true">↗</span></RouterLink><RouterLink to="/categories" class="hero-secondary">依分類逛逛 →</RouterLink></div>
          <p class="hero-caption">FOOD · WELLNESS · PLAY · LIVING</p>
        </div>
        <div class="hero-art"><span class="hero-stamp">好好照顧<br />一起生活</span><img :src="catScene" alt="貓咪與飼料、食碗和植物的原創日常插畫" /></div>
      </section>

      <div class="service-strip"><div><span aria-hidden="true">01</span><strong>日常補給</strong><small>主食、零食與生活用品</small></div><div><span aria-hidden="true">02</span><strong>自由挑選</strong><small>依分類與商品規格選購</small></div><div><span aria-hidden="true">03</span><strong>訂單追蹤</strong><small>登入會員查看訂單狀態</small></div></div>
      <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" :disabled="store.loading" @click="load">重試載入</button></div>

      <!-- categories -->
      <div class="section-heading">
        <div><p class="eyebrow">SHOP BY CATEGORY</p><h2>主子今天需要什麼？</h2></div>
        <RouterLink to="/categories" class="text-link">查看全部分類 →</RouterLink>
      </div>
      <div class="daily-categories">
        <button
          v-for="(c, index) in categories"
          :key="c.id"
          class="category-tile"
          @click="goCategory(c.slug)"
        >
          <span class="category-symbol" aria-hidden="true">{{ categoryIcons[index % categoryIcons.length] }}</span>
          <strong>{{ c.name }}</strong><span class="muted">探索選品 ↗</span>
        </button>
      </div>

      <!-- featured products -->
      <div class="section-heading">
        <div><p class="eyebrow">THE DAILY PICKS</p><h2>最新商品</h2><p class="muted">從一份小小心意，開始照顧主子的每一天。</p></div>
        <RouterLink to="/products" class="text-link">更多商品 →</RouterLink>
      </div>
      <div v-if="store.loading" class="spinner">載入中…</div>
      <div v-else-if="store.products.length" class="grid grid-products"><ProductCard v-for="p in store.products" :key="p.id" :product="p" /></div>
      <div v-else-if="!error" class="empty card">目前沒有上架商品，歡迎稍後再來逛逛。</div>
      <section class="member-banner"><div><p class="eyebrow">A LITTLE MORE TOGETHER</p><h2>把主子的日常，放在心上。</h2><p>登入後查看購物車、管理收件地址與訂單。</p></div><RouterLink to="/account" class="btn btn-ghost">前往會員中心 →</RouterLink></section>
    </div>
  </div>
</template>
