<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useProductStore } from '@/stores/product'

const store = useProductStore()
const router = useRouter()

onMounted(() => {
  if (!store.categories.length) store.fetchCategories()
})

function go(slug) {
  router.push({ name: 'category-products', params: { slug } })
}
</script>

<template>
  <div class="page">
    <div class="container">
      <h1 class="page-title">商品分類</h1>
      <p class="page-sub">依需求挑選最適合主子的商品</p>
      <div class="grid grid-categories">
        <button
          v-for="c in store.categories"
          :key="c.id"
          class="card"
          style="padding: 26px; text-align: left; cursor: pointer;"
          @click="go(c.slug)"
        >
          <div style="font-size: 30px;">🐾</div>
          <div style="font-weight: 700; font-size: 17px; margin-top: 10px;">{{ c.name }}</div>
          <div class="muted" style="font-size: 12px;">{{ c.slug }}</div>
        </button>
      </div>
    </div>
  </div>
</template>
