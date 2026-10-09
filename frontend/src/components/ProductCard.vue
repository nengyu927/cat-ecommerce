<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import fallback from '@/assets/cat-daily.svg'
import { useAuthStore } from '@/stores/auth'
import { useWishlistStore } from '@/stores/wishlist'
import { useRoute, useRouter } from 'vue-router'
import {productApi} from '@/api'
import {useCartStore} from '@/stores/cart'
import ProductSkuDialog from './ProductSkuDialog.vue'
const props = defineProps({ product: { type: Object, required: true } })
const emit = defineEmits(['wishlist-change'])
const auth = useAuthStore(), wishlist = useWishlistStore(), route = useRoute(), router = useRouter()
const error = ref('')
const cart=useCartStore(),adding=ref(false),choices=ref(null)
let operation=0,resolveChoice
function choose(id){const done=resolveChoice;resolveChoice=undefined;choices.value=null;done?.(id)}
function invalidate(){++operation;choose(null);adding.value=false;error.value=''}
onBeforeUnmount(invalidate)
watch([()=>props.product.id,()=>route.fullPath,()=>auth.epoch],invalidate,{flush:'sync'})
async function add(){
 if(adding.value||props.product.available===false)return
 auth.syncFromStorage()
 if(!auth.isLoggedIn){router.push({name:'login',query:{redirect:route.fullPath}});return}
 const currentOperation=++operation,id=props.product.id,epoch=auth.epoch
 const current=()=>currentOperation===operation&&id===props.product.id&&epoch===auth.epoch&&auth.isLoggedIn
 adding.value=true;error.value=''
 try{
  const detail=await productApi.getById(id)
  if(!current())return
  const skus=(detail.skus||[]).filter(s=>s.isActive!==false),available=skus.filter(s=>s.stock>0)
  if(!available.length)throw new Error('目前規格皆缺貨，請稍後再試')
  let skuId
  if(skus.length===1)skuId=available[0].id
  else{choices.value=skus;skuId=await new Promise(done=>{resolveChoice=done})}
  auth.syncFromStorage()
  if(!skuId||!current())return
  await cart.addItem(skuId,1)
  if(current())cart.openPreview()
 }catch(e){if(current())error.value=e.message}
 finally{if(current())adding.value=false}
}
const productTarget=computed(()=>({name:'product-detail',params:{id:props.product.id},query:['products','search','category-products','wishlist'].includes(route.name)?{returnTo:route.fullPath}:{}}))
async function toggle() {
  if (!auth.isLoggedIn) { router.push({ name: 'login', query: { redirect: route.fullPath } }); return }
  error.value = ''
  try { await wishlist.toggle(props.product.id); emit('wishlist-change') }
  catch (e) { error.value = e.message }
}
const failed = ref(false)
watch(() => props.product.primaryImageUrl, () => { failed.value = false })
</script>
<template>
  <article class="card product-card">
    <div class="product-image-wrap">
      <component :is="product.available === false ? 'div' : 'RouterLink'" :to="productTarget">
      <img class="product-thumb" :src="!failed && product.primaryImageUrl ? product.primaryImageUrl : fallback" :alt="product.name" loading="lazy" @error="failed = true" />
      </component>
      <span v-if="failed || !product.primaryImageUrl || product.primaryImageUrl.startsWith('/product-images/')" class="image-note">商品示意圖</span>
      <button type="button" class="wishlist-toggle" :class="{ 'is-saved': wishlist.contains(product.id) }" :aria-label="`${wishlist.contains(product.id) ? '取消收藏' : '收藏'}：${product.name}`" :aria-pressed="wishlist.contains(product.id)" :disabled="wishlist.pendingIds.includes(product.id) || (product.available === false && !wishlist.contains(product.id))" @click="toggle">
        <svg viewBox="0 0 24 24" :fill="wishlist.contains(product.id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.7" aria-hidden="true" focusable="false"><path d="M12 21 3 12a5.5 5.5 0 0 1 9-6 5.5 5.5 0 0 1 9 6Z" /></svg>
      </button>
    </div>
    <div class="product-body">
      <div class="product-brand">{{ product.brand || '喵喵日常選品' }}</div>
      <div class="product-name">{{ product.name }}</div>
      <span v-if="product.available === false" class="muted">商品已下架或暫停販售</span>
      <div class="product-card-bottom"><div class="product-price">NT$ {{ Number(product.basePrice).toLocaleString() }}</div><button type="button" class="btn btn-primary quick-add-button" :aria-label="'加入購物車：'+product.name" :disabled="adding || product.available===false" @click="add">{{ adding?'處理中…':'加入購物車' }}</button></div>
    </div>
    <p v-if="error" class="field-error product-wishlist-error" role="alert">{{ error }}</p>
    <ProductSkuDialog v-if="choices" :name="product.name" :skus="choices" @choose="choose" />
  </article>
</template>
