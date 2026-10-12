<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import ListPagination from '@/components/ListPagination.vue'
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProductStore } from '@/stores/product'
import { categoryApi, productApi } from '@/api'
import ProductCard from '@/components/ProductCard.vue'
import {queryInteger,queryText,queryChoice,queryId,emptyPageReplacement} from '@/utils/listQuery'

const store = useProductStore()
const route = useRoute()
const router = useRouter()

const keyword = ref(route.query.keyword || '')
const categoryId = ref(route.query.categoryId ? Number(route.query.categoryId) : null)
const page = ref(route.query.page ? Number(route.query.page) : 0)
const sort = ref(route.query.sort || 'createdAt,desc')
const error = ref('')
const title = ref('所有商品')
const brand = ref(''), minPrice = ref(''), maxPrice = ref(''), brands = ref([]), optionsError = ref('')
const inStock = ref(false)
const routeCategoryId = ref(null)
let version = 0
function validatePrices() {
  for (const price of [minPrice.value, maxPrice.value]) {
    if (price !== '' && (!/^\d+(\.\d{1,2})?$/.test(String(price)) || Number(price) > 99999999.99)) throw new Error('價格須為 0 至 99999999.99，最多兩位小數')
  }
  if (minPrice.value !== '' && maxPrice.value !== '' && Number(minPrice.value) > Number(maxPrice.value)) throw new Error('最低價格不可高於最高價格')
}
async function loadOptions() {
  optionsError.value = ''
  try {
    const results = await Promise.all([productApi.brands(), store.fetchCategories()])
    brands.value = results[0]
  } catch (e) { optionsError.value = `篩選選單載入失敗：${e.message}` }
}

async function load() {
  const current = ++version
  ++store.requestVersion
  store.loading = true
  error.value = ''
  store.products=[];store.totalElements=0;store.totalPages=0
  try {
  keyword.value = queryText(route.query.q ?? route.query.keyword,'',200,'關鍵字')
  categoryId.value = queryId(route.query.categoryId)
  page.value = queryInteger(route.query.page,0)
  const size=queryInteger(route.query.size,12,1,100,'每頁筆數')
  sort.value = queryChoice(route.query.sort,'createdAt,desc',['createdAt,desc','createdAt,asc','price,asc','price,desc','basePrice,asc','basePrice,desc','name,asc','name,desc']).replace('basePrice,','price,')
  brand.value = queryText(route.query.brand,'',100,'品牌')
  minPrice.value = queryText(route.query.minPrice,'',16,'最低價格')
  maxPrice.value = queryText(route.query.maxPrice,'',16,'最高價格')
  if (route.query.inStock !== undefined && !['true', 'false'].includes(route.query.inStock)) throw new Error('現貨條件須為 true 或 false')
  inStock.value = route.query.inStock === 'true'
  validatePrices()
  title.value = route.name === 'search' ? '搜尋結果' : '所有商品'
  routeCategoryId.value = null
  if (route.params.slug) {
    const category = await categoryApi.getBySlug(route.params.slug)
    if (current !== version) return
    categoryId.value = category.id
    routeCategoryId.value = String(category.id)
    title.value = category.name
  }
  const data=await store.fetchProducts({
    page: page.value,
    size,
    categoryId: categoryId.value,
    keyword: keyword.value,
    sort: sort.value, brand: brand.value, minPrice: minPrice.value, maxPrice: maxPrice.value, inStock: inStock.value
  })
  if(current!==version||!data)return
  const replacement=emptyPageReplacement(page.value,data)
  if(replacement!==null)await router.replace({name:route.name,params:route.params,query:{...route.query,page:replacement},hash:route.hash})
  } catch (e) { if (current === version) error.value = e.message }
  finally { if (current === version) store.loading = false }
}

onMounted(() => { loadOptions(); load() })
onBeforeUnmount(() => { ++version; ++store.requestVersion })

watch(() => route.fullPath, load)

function applyFilters() {
  try { validatePrices() } catch (e) { error.value = e.message; return }
  const sameCategory=route.name==='category-products' && routeCategoryId.value!==null && String(categoryId.value)===routeCategoryId.value
  router.push({ name: sameCategory ? 'category-products' : keyword.value ? 'search' : 'products', ...(sameCategory?{params:route.params}:{}), query: {
    ...(keyword.value ? { q: keyword.value } : {}),
    ...(!sameCategory && categoryId.value ? { categoryId: categoryId.value } : {}),
    ...(brand.value ? { brand: brand.value } : {}),
    ...(minPrice.value !== '' ? { minPrice: minPrice.value } : {}),
    ...(maxPrice.value !== '' ? { maxPrice: maxPrice.value } : {}),
    ...(inStock.value ? { inStock: 'true' } : {}),
    sort: sort.value,
    ...(route.query.size!==undefined?{size:route.query.size}:{})
  }})
}

function clearFilters() {
  keyword.value = ''; categoryId.value = null; brand.value = ''; minPrice.value = ''; maxPrice.value = ''; sort.value = 'createdAt,desc'
  inStock.value = false
  router.push({name:'products'})
}
function fillDemo() {
 if (store.loading) return
 const product = store.products[0]
 keyword.value = product?.name || '雞肉'; categoryId.value = product?.categoryId || null; brand.value = product?.brand || ''
 const price = Number(product?.basePrice)
 minPrice.value = Number.isFinite(price) ? String(Math.max(0, Math.floor(price * 0.8))) : '100'
 maxPrice.value = Number.isFinite(price) ? String(Math.ceil(price * 1.2 + 1)) : '1500'
 inStock.value = false; sort.value = 'price,asc'; error.value = ''
}
</script>

<template>
  <div class="page">
    <div class="container">
      <h1 class="page-title">{{ title }}</h1>
      <div v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" @click="load">重試查詢</button></div>
      <div v-if="optionsError" class="alert alert-error" role="alert">{{ optionsError }} <button class="btn btn-ghost btn-sm" @click="loadOptions">重試選單</button></div>
      <p class="page-sub">共 {{ store.totalElements }} 件商品</p>

      <div class="card catalog-filter-panel" style="padding: 16px; margin-bottom: 24px;">
<DemoFillButton :disabled="store.loading" @fill="fillDemo" label="一鍵填入搜尋條件" />
        <div class="row" style="flex-wrap: wrap;">
          <input v-model="keyword" class="input" aria-label="商品關鍵字" style="max-width: 260px;" placeholder="搜尋商品名稱…" @keyup.enter="applyFilters" />
          <select v-model="categoryId" aria-label="商品分類" class="select" style="max-width: 200px;" @change="applyFilters">
            <option :value="null">全部分類</option>
            <option v-for="c in store.categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <select v-model="brand" aria-label="商品品牌" class="select" style="max-width: 200px;" @change="applyFilters">
            <option value="">全部品牌</option>
            <option v-if="brand && !brands.includes(brand)" :value="brand">{{ brand }}</option>
            <option v-for="name in brands" :key="name" :value="name">{{ name }}</option>
          </select>
          <input v-model="minPrice" class="input" aria-label="最低價格" inputmode="decimal" style="max-width: 150px;" placeholder="最低價格" @keyup.enter="applyFilters" />
          <input v-model="maxPrice" class="input" aria-label="最高價格" inputmode="decimal" style="max-width: 150px;" placeholder="最高價格" @keyup.enter="applyFilters" />
          <label class="stock-filter"><input v-model="inStock" type="checkbox" @change="applyFilters" /> 只看現貨</label>
          <button class="btn btn-primary" @click="applyFilters">搜尋</button>
          <button class="btn btn-ghost" @click="clearFilters">清除條件</button>
          <select v-model="sort" aria-label="商品排序" class="select" style="max-width: 200px;" @change="applyFilters">
            <option value="createdAt,desc">最新商品</option>
            <option value="createdAt,asc">最早商品</option>
            <option value="price,asc">價格由低到高</option>
            <option value="price,desc">價格由高到低</option>
            <option value="name,asc">名稱排序</option>
            <option value="name,desc">名稱反向排序</option>
          </select>
        </div>
      </div>
      <p v-if="route.query.brand || route.query.minPrice || route.query.maxPrice || route.query.inStock === 'true'" class="page-sub">已套用：品牌 {{ route.query.brand || '不限' }} · 基本售價 NT$ {{ route.query.minPrice ?? '0' }} ～ {{ route.query.maxPrice || '不限' }}{{ route.query.inStock === 'true' ? ' · 只看現貨' : '' }}</p>

      <div v-if="store.loading" class="spinner">載入中…</div>
      <div v-else-if="!error && !store.products.length" class="empty">
        <span class="emoji">🔍</span>
        找不到符合條件的商品
      </div>
      <div v-else-if="!error" class="grid grid-products">
        <ProductCard v-for="p in store.products" :key="p.id" :product="p" />
      </div>

  <ListPagination v-if="!error" :page="page" :total-pages="store.totalPages" :loading="store.loading" :default-size="12" />
    </div>
  </div>
</template>
