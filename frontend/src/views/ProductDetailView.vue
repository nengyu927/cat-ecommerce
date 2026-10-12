<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import FeedbackNotice from '@/components/FeedbackNotice.vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {safeListReturn} from '@/utils/listQuery'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import ProductImage from '@/components/ProductImage.vue'

const route = useRoute()
const router = useRouter()
const returnTarget=computed(()=>safeListReturn(router,route.query.returnTo,['products','search','category-products','wishlist'],{name:'products'}))
let loadSequence=0
let addSequence=0
onBeforeUnmount(()=>{++loadSequence})
const auth = useAuthStore()
const cart = useCartStore()

const product = ref(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const selectedSkuId = ref(null)
const quantity = ref(1)
const adding = ref(false)
const message = ref('')
const related = ref([])
const relatedError = ref('')
const relatedLoading = ref(false)
const currentImage = ref('')

async function loadRelated(sequence=loadSequence,id=route.params.id) {
  relatedLoading.value=true
  relatedError.value=''
  try {
    const data=await productApi.related(id)
    if(sequence===loadSequence)related.value=data
  } catch(e) {
    if(sequence===loadSequence)relatedError.value=e.message
  } finally { if(sequence===loadSequence)relatedLoading.value=false }
}

async function loadProduct() {
  const sequence=++loadSequence
  ++addSequence
  adding.value=false
  actionError.value=''
  const id=route.params.id
  product.value=null
  loading.value = true
  error.value = ''
  message.value = ''
  quantity.value = 1
  related.value = []
  relatedError.value = ''
  relatedLoading.value = false
  selectedSkuId.value = null
  try {
    const data=await productApi.getById(id)
    if(sequence!==loadSequence)return
    product.value = data
    currentImage.value = product.value.primaryImageUrl
    selectedSkuId.value = product.value.skus?.find(s => s.stock > 0)?.id || null
    void loadRelated(sequence,id)
  } catch (e) {
    if(sequence===loadSequence)error.value = e.message
  } finally {
    if(sequence===loadSequence)loading.value = false
  }
}
watch(() => route.params.id, loadProduct, { immediate: true })

function selectedSku() {
  return product.value?.skus?.find(s => s.id === selectedSkuId.value)
}
watch([selectedSkuId,quantity],()=>{actionError.value='';message.value=''})

async function addToCart() {
  if(adding.value)return
  actionError.value=''
  message.value=''
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (!selectedSkuId.value) return
  if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > selectedSku().stock) {
    actionError.value = '請輸入庫存範圍內的整數數量'
    return
  }
  adding.value = true
  const sequence=loadSequence,operation=++addSequence
  const current=()=>sequence===loadSequence && operation===addSequence
  try {
    await cart.addItem(selectedSkuId.value, quantity.value)
    if(current()) { message.value = '已加入購物車 ✅'; cart.openPreview() }
  } catch (e) {
    if(current())actionError.value = e.message
  } finally {
    if(current())adding.value = false
  }
}
function fillDemo() { if (loading.value || adding.value) return; const sku = product.value?.skus?.find(item => item.stock > 0); if (!sku) return; selectedSkuId.value = sku.id; quantity.value = Math.min(2, sku.stock) }
</script>

<template>
  <div class="page">
    <div class="container">
      <RouterLink class="btn btn-ghost mb-2" :to="returnTarget">{{ returnTarget.name==='wishlist'?'返回收藏列表':'返回商品列表' }}</RouterLink>
      <div v-if="loading" class="spinner">載入中…</div>
      <div v-else-if="error" class="alert alert-error" role="alert"><span>{{ error }}</span> <button class="btn btn-ghost btn-sm" @click="loadProduct">重試載入商品</button></div>
      <div v-else-if="product" class="grid" style="grid-template-columns: 1fr 1fr; align-items: start;">
        <div class="card" style="overflow: hidden;">
          <ProductImage :src="currentImage" :alt="product.name" style="width: 100%; display: block;" />
          <p v-if="!currentImage || currentImage.startsWith('/product-images/')" class="muted" style="padding:0 16px">AI 商品示意圖，非品牌官方照片</p>
          <div class="row" style="padding: 12px; flex-wrap: wrap;">
            <button v-for="image in product.images" :key="image.id" class="btn btn-ghost" @click="currentImage = image.url" :aria-label="image.altText || '切換商品圖片'">
              <ProductImage :src="image.url" :alt="image.altText || product.name" style="width: 64px; height: 64px; object-fit: cover;" />
            </button>
          </div>
        </div>
        <div>
          <div class="muted">{{ product.categoryName }} · {{ product.brand }}</div>
          <h1 style="font-size: 26px; margin: 6px 0 12px;">{{ product.name }}</h1>
          <div style="font-size: 28px; font-weight: 800; color: var(--brand); margin-bottom: 18px;">
            NT$ {{ Number(selectedSku()?.price ?? product.basePrice).toLocaleString() }}
          </div>
          <p class="muted" style="margin-bottom: 22px;">{{ product.description }}</p>
          <dl v-if="product.ingredients || product.suitableAge || product.origin || product.storageInstructions" class="card" style="padding: 16px; margin-bottom: 18px;">
            <template v-if="product.ingredients"><dt>成分</dt><dd>{{ product.ingredients }}</dd></template>
            <template v-if="product.suitableAge"><dt>適用年齡</dt><dd>{{ product.suitableAge }}</dd></template>
            <template v-if="product.origin"><dt>產地</dt><dd>{{ product.origin }}</dd></template>
            <template v-if="product.storageInstructions"><dt>保存方式</dt><dd>{{ product.storageInstructions }}</dd></template>
          </dl>

          <div v-if="product.skus?.length" class="field">
<DemoFillButton :disabled="adding || !product.skus?.some(s => s.stock > 0)" @fill="fillDemo" label="一鍵填入規格與數量" />
            <label>選擇規格</label>
            <select v-model="selectedSkuId" class="select" aria-label="選擇規格" :disabled="adding">
              <option v-for="s in product.skus" :key="s.id" :value="s.id" :disabled="s.stock <= 0">
                {{ s.specName }} — NT$ {{ Number(s.price).toLocaleString() }}
                {{ s.stock > 0 ? `（庫存 ${s.stock}）` : '（缺貨）' }}
              </option>
            </select>
          </div>

          <div class="field" style="max-width: 140px;">
            <label>數量</label>
            <input v-model.number="quantity" aria-label="數量" type="number" min="1" :max="selectedSku()?.stock || 1" class="input" :disabled="adding" />
          </div>

          <div v-if="actionError" class="alert alert-error" role="alert">{{ actionError }}</div>
<FeedbackNotice :message="message" />

          <div class="row mt-2">
            <button class="btn btn-primary" :disabled="adding || !selectedSkuId || (selectedSku()?.stock ?? 0) <= 0" @click="addToCart">
              {{ adding ? '加入中…' : '加入購物車' }}
            </button>
            <RouterLink to="/cart" class="btn btn-ghost">前往購物車</RouterLink>
          </div>
        </div>
      </div>
      <section v-if="product && !loading && !error" aria-label="相關商品" :aria-busy="relatedLoading" style="margin-top: 28px;">
        <h2>相關商品</h2>
        <p v-if="relatedLoading" class="muted" role="status">相關商品載入中…</p>
        <div v-if="relatedError" class="alert alert-error" role="alert"><span>相關商品載入失敗：{{ relatedError }}</span> <button class="btn btn-ghost btn-sm" :disabled="relatedLoading" @click="loadRelated()">重試相關商品</button></div>
        <p v-if="!relatedLoading && !relatedError && !related.length" class="muted">目前沒有相關商品</p>
        <div class="grid grid-products">
          <RouterLink v-for="item in related" :key="item.id" :to="{name:'product-detail',params:{id:item.id},query:{returnTo:router.resolve(returnTarget).fullPath}}" class="card product-card">
            <ProductImage :src="item.primaryImageUrl" :alt="item.name" class="product-thumb" />
            <div class="product-body"><div>{{ item.name }}</div><div class="product-price">NT$ {{ item.basePrice }}</div></div>
          </RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>
