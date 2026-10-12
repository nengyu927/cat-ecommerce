<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { categoryApi, productApi } from '@/api'
import ProductCard from '@/components/ProductCard.vue'
import ProductRail from '@/components/ProductRail.vue'
import { collections, flattenCategories, categoryDescendants } from '@/config/collections'
const products = ref([]), allCategories = ref([]), totalProducts = ref(0), loading = ref(true), error = ref('')
const active = ref(0), paused = ref(false), hovering = ref(false), focused = ref(false)
const slides = collections.slice(0, 3), slide = computed(() => slides[active.value])
const roots = computed(() => allCategories.value.filter(category => !category.parentId))
const latest = computed(() => products.value.slice(0, 8))
const tablePicks = computed(() => pick(['food', 'dining']))
const homePicks = computed(() => pick(['beds', 'scratchers', 'grooming', 'travel']))
const symbols = ['◉', '✦', '◌', '✿', '▦', '◇', '⌑', '⊙', '✧', '↗']
let version = 0, timer, motion
function pick(slugs) { const ids = categoryDescendants(allCategories.value, slugs); return products.value.filter(product => ids.has(String(product.categoryId))).slice(0, 10) }
async function load() {
  const current = ++version; loading.value = true; error.value = ''
  const results = await Promise.allSettled([categoryApi.list(), productApi.list({ page: 0, size: 48, sort: 'createdAt,desc' })])
  if (current !== version) return
  if (results[0].status === 'fulfilled') allCategories.value = flattenCategories(results[0].value)
  if (results[1].status === 'fulfilled') { products.value = results[1].value.content; totalProducts.value = results[1].value.totalElements }
  const failure = results.find(result => result.status === 'rejected')
  if (failure) error.value = failure.reason.message || '選品資料暫時無法載入，請稍後重試。'
  loading.value = false
}
function changeSlide(index) { active.value = (index + slides.length) % slides.length }
function pauseForMotion() { if (motion.matches) paused.value = true }
onMounted(() => {
  load(); motion = matchMedia('(prefers-reduced-motion: reduce)'); pauseForMotion(); motion.addEventListener('change', pauseForMotion)
  timer = setInterval(() => { if (!paused.value && !hovering.value && !focused.value && !document.hidden && !motion.matches) changeSlide(active.value + 1) }, 7000)
})
onBeforeUnmount(() => { ++version; clearInterval(timer); motion?.removeEventListener('change', pauseForMotion) })
</script>
<template>
  <div class="page home-page premium-home"><div class="container">
    <section class="editorial-hero" aria-label="本季生活企劃" aria-roledescription="輪播" @mouseenter="hovering=true" @mouseleave="hovering=false" @focusin="focused=true" @focusout="focused=$event.currentTarget.contains($event.relatedTarget)">
      <div class="editorial-copy"><p class="eyebrow">MEOW DAILY / {{ slide.eyebrow }}</p><span class="edition-mark">THE EVERYDAY COLLECTION · VOL. 01</span><h1>{{ slide.title }}</h1><p class="editorial-description">{{ slide.description }}</p><div class="hero-actions"><RouterLink :to="{name:'collection',params:{slug:slide.slug}}" class="btn btn-primary">探索生活提案 ↗</RouterLink><RouterLink to="/products" class="hero-secondary">逛逛所有商品 →</RouterLink></div><div class="hero-pagination"><span class="hero-count">0{{ active+1 }} <span>/ 03</span></span><div class="hero-dots"><button v-for="(item,index) in slides" :key="item.slug" type="button" :aria-label="'查看企劃：'+item.shortTitle" :aria-current="index===active ? 'true' : undefined" :class="{active:index===active}" @click="changeSlide(index)"></button></div><button class="hero-play" type="button" :aria-label="paused?'播放活動輪播':'暫停活動輪播'" @click="paused=!paused">{{ paused?'播放':'暫停' }}</button></div></div>
      <div class="editorial-image"><img :key="slide.image" :src="slide.image" :alt="slide.shortTitle+'的原創情境示意圖'" fetchpriority="high" width="1536" height="1024" /><span class="image-editorial-label">{{ slide.tag }}<small>CURATED FOR YOUR CAT</small></span></div>
    </section>
    <div class="premium-service-strip"><span>給主子，也給喜歡生活的你。</span><div><span>主食與日常</span><i></i><span>自然材質選品</span><i></i><span>一起生活的提案</span></div></div>
    <nav class="home-anchor-nav" aria-label="首頁區塊導覽"><a href="#seasonal">本季企劃</a><a href="#categories">依需求選購</a><a href="#daily-picks">新到選品</a><a href="#living-edit">居家生活</a></nav>
    <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">重試載入</button></div>
    <section id="seasonal" class="seasonal-section"><div class="section-heading"><div><p class="eyebrow">THE SEASONAL JOURNAL</p><h2>為每一種日常，準備一份提案。</h2><p class="muted">往下逛逛，找到你和主子喜歡的生活方式。</p></div><span class="section-number">01 / LIFESTYLE</span></div>
      <div class="campaign-grid"><RouterLink v-for="(collection,index) in collections" :key="collection.slug" :to="{name:'collection',params:{slug:collection.slug}}" class="campaign-card" :class="'tone-'+collection.tone"><div class="campaign-image"><img :src="collection.image" :alt="collection.shortTitle+'情境示意圖'" loading="lazy" width="600" height="450" /><span class="campaign-index">0{{ index+1 }}</span></div><div class="campaign-body"><span class="campaign-tag">{{ collection.tag }}</span><h3>{{ collection.shortTitle }}</h3><p>{{ collection.description }}</p><span class="campaign-link">閱讀提案與選購 <span aria-hidden="true">↗</span></span></div></RouterLink></div>
    </section>
    <section id="categories" class="category-section"><div class="section-heading"><div><p class="eyebrow">A PLACE FOR EVERYTHING</p><h2>主子今天需要什麼？</h2></div><RouterLink to="/categories" class="text-link">全部 {{ roots.length }} 種生活分類 ↗</RouterLink></div><div class="premium-categories"><RouterLink v-for="(category,index) in roots" :key="category.id" :to="{name:'category-products',params:{slug:category.slug}}" class="premium-category"><span aria-hidden="true">{{ symbols[index % symbols.length] }}</span><strong>{{ category.name }}</strong><small>探索選品 ↗</small></RouterLink></div><p v-if="!loading&&!roots.length" class="muted">分類整理中，歡迎先逛逛所有商品。</p></section>
    <section id="daily-picks" class="latest-section"><div class="section-heading"><div><p class="eyebrow">NEW & NOTEWORTHY</p><h2>新到的日常好物</h2><p class="muted">細節剛剛好，讓每天多一點喜歡。</p></div><RouterLink to="/products" class="text-link">查看全部 {{ totalProducts }} 件選品 ↗</RouterLink></div><div v-if="loading" class="product-skeleton-grid" role="status" aria-label="載入選品中"><div v-for="n in 4" :key="n" class="product-skeleton"></div></div><div v-else-if="latest.length" class="grid grid-products"><ProductCard v-for="product in latest" :key="product.id" :product="product" /></div><div v-else-if="!error" class="empty card">新選品整理中，歡迎稍後再來逛逛。</div></section>
    <ProductRail :products="tablePicks" eyebrow="GOOD FOOD, GOOD MOOD" title="餐桌上的小小幸福" description="主食、補水與溫潤食器，準備好每一天的第一餐。" :to="{name:'collection',params:{slug:'daily-table'}}" />
    <section class="brand-story"><div class="brand-story-image"><img src="/campaigns/cat-lifestyle-hero.jpg" alt="暖陽下的貓咪與陶瓷食碗" loading="lazy" width="1536" height="1024" /></div><div class="brand-story-copy"><p class="eyebrow">LESS, BUT BETTER</p><h2>不急著填滿。<br />只留下剛剛好的喜歡。</h2><p>我們相信，好好生活藏在每天重複的小事裡。<br />一頓飯、一場好眠、一次一起玩的時間，<br />都是你和主子慢慢累積的默契。</p><RouterLink :to="{name:'collection',params:{slug:'quiet-home'}}" class="text-link">找到生活裡的剛剛好 ↗</RouterLink></div></section>
    <div id="living-edit"><ProductRail :products="homePicks" eyebrow="OBJECTS FOR A QUIETER HOME" title="把家，留一角給主子" description="舒服的觸感、沉靜的色彩，讓主子的日常融入你的家。" :to="{name:'collection',params:{slug:'quiet-home'}}" /></div>
    <section class="care-journal"><div class="section-heading"><div><p class="eyebrow">LITTLE NOTES FOR EVERYDAY</p><h2>一起生活的小筆記</h2></div><span class="section-number">02 / EVERYDAY NOTES</span></div><div class="journal-grid"><RouterLink :to="{name:'collection',params:{slug:'daily-table'}}"><span>01 · 用餐</span><h3>替用餐角落留一點空間</h3><p>食碗和水碗分開放，定期清洗；依照食量挑選方便分餐的規格。</p><small>看看餐桌選品 ↗</small></RouterLink><RouterLink :to="{name:'collection',params:{slug:'gentle-care'}}"><span>02 · 整理</span><h3>照顧的節奏，可以慢一點</h3><p>從短時間梳毛開始，熟悉工具的觸感，讓日常整理變成溫柔的互動。</p><small>準備日常工具 ↗</small></RouterLink><RouterLink :to="{name:'collection',params:{slug:'little-journey'}}"><span>03 · 外出</span><h3>出門之前，先在家練習</h3><p>把提籠留在熟悉的角落，放入常用的休息墊，讓主子慢慢認識外出空間。</p><small>看看外出提案 ↗</small></RouterLink></div></section>
    <section class="premium-member-banner"><div><p class="eyebrow">A LITTLE MORE TOGETHER</p><h2>把喜歡的日常，慢慢收藏。</h2><p>儲存心動的選品、安排收件地址，讓下次相遇更輕鬆。</p></div><RouterLink to="/account" class="btn btn-ghost">前往會員中心 ↗</RouterLink></section>
  </div></div>
</template>
