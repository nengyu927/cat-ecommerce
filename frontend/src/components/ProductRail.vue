<script setup>
import { ref } from 'vue'
import ProductCard from './ProductCard.vue'
defineProps({ products: { type: Array, required: true }, title: String, eyebrow: String, description: String, to: { type: [String, Object], required: true } })
const track = ref(null)
function scroll(direction) { track.value?.scrollBy({ left: direction * track.value.clientWidth * 0.8, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' }) }
</script>
<template>
  <section v-if="products.length" class="product-rail-section" :aria-label="title">
    <div class="section-heading"><div><p class="eyebrow">{{ eyebrow }}</p><h2>{{ title }}</h2><p class="muted">{{ description }}</p></div><div class="rail-navigation"><RouterLink :to="to" class="text-link">探索選品 ↗</RouterLink><button type="button" :aria-label="title+'往左瀏覽'" @click="scroll(-1)">←</button><button type="button" :aria-label="title+'往右瀏覽'" @click="scroll(1)">→</button></div></div>
    <div ref="track" class="product-rail" tabindex="0" :aria-label="title+'商品列表，可左右捲動'" @keydown.left.self.prevent="scroll(-1)" @keydown.right.self.prevent="scroll(1)"><ProductCard v-for="product in products" :key="product.id" :product="product" /></div>
  </section>
</template>
