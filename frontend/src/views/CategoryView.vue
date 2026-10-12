<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { categoryApi } from '@/api'
import { flattenCategories } from '@/config/collections'
const categories = ref([]), loading = ref(false), error = ref('')
const roots = computed(() => categories.value.filter(category => !category.parentId))
const categoryVisuals = {
  food: ['orijen-cat-dry.png','從主食開始，準備每天的營養與補水。'], health: ['lysine-supplement.png','依日常需求挑選合適的營養補給。'], toys: ['feather-wand-toy.png','為追逐、探索與陪玩留一點時間。'], treats: ['freeze-dried-chicken.png','分享一起玩的獎勵與小小心意。'], litter: ['tofu-litter.png','維持乾爽，整理舒服的生活環境。'], dining: ['ceramic-bowl.jpg','讓每天的餐桌也有喜歡的樣子。'], beds: ['linen-cat-bed.jpg','窗邊、沙發旁，都是好眠的地方。'], scratchers: ['cat-scratcher.jpg','抓磨、伸展、休息，一次準備齊全。'], grooming: ['cat-grooming.jpg','用溫柔的節奏，完成日常整理。'], travel: ['cat-carrier.jpg','從熟悉的氣味開始，準備安心出門。']
}
function visual(category) { return categoryVisuals[category.slug] || ['ceramic-bowl.jpg','探索適合主子的日常選品。'] }
let version = 0
async function load() { const current=++version; loading.value=true; error.value=''; try { const tree=await categoryApi.list(); if(current===version)categories.value=flattenCategories(tree) } catch(e) { if(current===version)error.value=e.message } finally { if(current===version)loading.value=false } }
onMounted(load); onBeforeUnmount(()=>{++version})
</script>

<template>
  <div class="page category-directory">
    <div class="container">
      <div class="category-intro"><div><p class="eyebrow">THE EVERYDAY DEPARTMENTS</p><h1>每一種日常，都有剛剛好的選擇。</h1><p>從一頓飯到一場好眠，依照主子的需要慢慢挑選。</p></div><RouterLink to="/products" class="text-link">瀏覽所有商品 ↗</RouterLink></div>
      <p v-if="loading" class="spinner" role="status">整理生活分類中…</p><p v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">重試載入</button></p>
      <div class="category-directory-grid"><article v-for="category in roots" :key="category.id" class="directory-card"><RouterLink :to="{name:'category-products',params:{slug:category.slug}}" class="directory-image"><img :src="'/product-images/'+visual(category)[0]" :alt="category.name+'商品示意圖'" loading="lazy" width="600" height="375" /></RouterLink><div class="directory-body"><h2><RouterLink :to="{name:'category-products',params:{slug:category.slug}}">{{ category.name }} ↗</RouterLink></h2><p class="directory-description">{{ visual(category)[1] }}</p><div class="collection-chips"><RouterLink v-for="child in categories.filter(child=>String(child.parentId)===String(category.id))" :key="child.id" :to="{name:'category-products',params:{slug:child.slug}}">{{ child.name }} ↗</RouterLink></div></div></article></div>
      <p v-if="!loading&&!error&&!roots.length" class="empty card">分類整理中，歡迎先逛逛所有商品。</p>
    </div>
  </div>
</template>
