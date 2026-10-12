<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { categoryApi, productApi } from '@/api'
import { collections } from '@/config/collections'
import ProductCard from '@/components/ProductCard.vue'
const route = useRoute(), products = ref([]), categories = ref([]), loading = ref(false), error = ref(''), sort = ref('default')
const collection = computed(() => collections.find(item => item.slug === route.params.slug))
const sorted = computed(() => sort.value === 'default' ? products.value : [...products.value].sort((a,b) => (sort.value === 'asc' ? 1 : -1) * (a.basePrice-b.basePrice)))
let version = 0
async function load() {
  const current = ++version; products.value = []; categories.value = []; loading.value = true; error.value = ''; sort.value = 'default'
  if (!collection.value) { loading.value = false; return }
  const results = await Promise.allSettled(collection.value.categories.map(async slug => {
    const category = await categoryApi.getBySlug(slug)
    const pages = []
    for (let page = 0; ; page++) {
      if (current !== version) return { category, products: [] }
      const data = await productApi.list({ categoryId: category.id, page, size: 50, sort: 'createdAt,desc' })
      pages.push(...data.content)
      if (data.last || page+1 >= data.totalPages) break
    }
    return { category, products: pages }
  }))
  if (current !== version) return
  const success = results.filter(result => result.status === 'fulfilled').map(result => result.value)
  categories.value = success.map(result => result.category)
  products.value = [...new Map(success.flatMap(result => result.products).map(product => [product.id,product])).values()]
  if (results.some(result => result.status === 'rejected')) error.value = '部分選品暫時無法載入，可重試或前往所有商品繼續選購。'
  loading.value = false
}
watch(() => route.params.slug, load, { immediate: true })
onBeforeUnmount(() => { ++version })
</script>
<template>
  <div class="page collection-page"><div class="container">
    <nav class="collection-breadcrumb" aria-label="企劃導覽"><RouterLink to="/">首頁</RouterLink><span>/</span><RouterLink :to="{name:'home',hash:'#seasonal'}">生活企劃</RouterLink><template v-if="collection"><span>/</span><span>{{ collection.shortTitle }}</span></template></nav>
    <template v-if="collection"><section class="collection-hero" :class="'tone-'+collection.tone"><div><p class="eyebrow">{{ collection.eyebrow }}</p><h1>{{ collection.title }}</h1><p>{{ collection.description }}</p><a href="#collection-products" class="btn btn-primary">瀏覽本次選品 ↓</a></div><img :src="collection.image" :alt="collection.shortTitle+'情境示意圖'" width="900" height="675" fetchpriority="high" /></section>
      <div class="collection-notes"><div v-for="(detail,index) in collection.details" :key="detail"><span>0{{ index+1 }}</span>{{ detail }}</div></div>
      <section id="collection-products"><div class="section-heading"><div><p class="eyebrow">THE CURATED SELECTION</p><h2>這份提案裡的日常好物</h2><p class="muted">{{ loading?'正在整理選品…':products.length+' 件選品，慢慢挑選你的喜歡。' }}</p></div><div class="collection-sort" aria-label="選品排序"><button v-for="(label,key) in {default:'精選順序',asc:'價格由低到高',desc:'價格由高到低'}" :key="key" type="button" :aria-pressed="sort===key" :class="{active:sort===key}" @click="sort=key">{{ label }}</button></div></div>
        <div class="collection-chips"><RouterLink v-for="category in categories" :key="category.id" :to="{name:'category-products',params:{slug:category.slug}}">{{ category.name }} ↗</RouterLink></div>
        <p v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">重試載入</button></p><div v-if="loading" class="spinner" role="status">載入生活選品中…</div><div v-else-if="sorted.length" class="grid grid-products"><ProductCard v-for="product in sorted" :key="product.id" :product="product" /></div><div v-else-if="!error" class="empty card">本次選品整理中。<RouterLink to="/products" class="text-link">先逛逛所有商品 ↗</RouterLink></div>
      </section><div class="collection-next"><p class="eyebrow">KEEP EXPLORING</p><h2>下一份日常，想從哪裡開始？</h2><div class="collection-chips"><RouterLink v-for="item in collections.filter(item=>item.slug!==collection.slug)" :key="item.slug" :to="{name:'collection',params:{slug:item.slug}}">{{ item.shortTitle }} ↗</RouterLink></div></div>
    </template><div v-else class="empty card"><h1>找不到這份生活企劃</h1><RouterLink :to="{name:'home',hash:'#seasonal'}" class="btn btn-primary">查看本季企劃</RouterLink></div>
  </div></div>
</template>
