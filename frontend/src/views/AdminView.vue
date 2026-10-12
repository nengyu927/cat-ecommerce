<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import ListPagination from '@/components/ListPagination.vue'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useConfirmation} from '@/composables/useConfirmation'
import {useAuthStore} from '@/stores/auth'
import { computed, reactive, ref, watch, onBeforeUnmount } from 'vue'
import {parseAdminListQuery} from '@/utils/adminListQuery'
import {safeListReturn,emptyPageReplacement} from '@/utils/listQuery'
import { useRoute, useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import { adminPath } from '@/config/surface'

const route = useRoute()
const router = useRouter()
const auth=useAuthStore(),confirmation=useConfirmation()
const inventoryReturn=computed(()=>safeListReturn(router,route.query.returnTo,['admin-inventory'],{name:'admin-inventory'}))
const section = computed(() => route.params.section)
const rows = ref([])
const categories = ref([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const saving = ref(false)
const blocked=computed(()=>saving.value||confirmation.pending.value)
const error = ref('')
const message = ref('')
const keyword = ref('')
const status = ref('')
const filters = reactive({ categoryId: '', brand: '', isActive: '', memberId: '', from: '', to: '' })
const appliedFilters = ref({})
let loadVersion = 0
const title = computed(() => ({ products: '商品管理', categories: '分類管理', orders: '訂單管理' })[section.value])
const sortOptions=computed(()=>{
 const fields=section.value==='products'?{createdAt:'建立時間',name:'商品名稱',basePrice:'基本售價',status:'商品狀態',id:'商品編號'}:{createdAt:'成立時間',orderNo:'訂單編號',totalAmount:'訂單金額',status:'訂單狀態',id:'流水編號'}
 return Object.entries(fields).flatMap(([key,label])=>[{value:key+',desc',label:label+'（降冪）'},{value:key+',asc',label:label+'（升冪）'}])
})
function changeSort(value){navigateQuery({...route.query,sort:value,page:0})}
const transitions = { PENDING: ['PAID', 'SHIPPED', 'CANCELLED'], PAID: ['SHIPPED', 'CANCELLED'], SHIPPED: ['COMPLETED'], COMPLETED: [], CANCELLED: [] }
const labels = { PENDING: '待處理', PAID: '已付款', SHIPPED: '已出貨', COMPLETED: '已完成', CANCELLED: '已取消', ON_SALE: '上架', OFF_SHELF: '下架', SOLD_OUT: '售罄' }

async function load() {
  const version = ++loadVersion
  const currentSection = section.value
  loading.value = true
  error.value = ''
  rows.value=[];totalPages.value=0
  try {
    const params = parseAdminListQuery(currentSection,route.query)
    const data = await adminApi[currentSection](params)
    if (version !== loadVersion) return
    const replacement=emptyPageReplacement(params.page,data)
    if(replacement!==null){await router.replace({name:route.name,params:route.params,query:{...route.query,page:replacement},hash:route.hash});return}
    rows.value = data.content
    totalPages.value = data.totalPages
    if (currentSection === 'products') {
      // Pagination is respected so categories beyond the first page remain editable/selectable.
      const all = []
      for (let p = 0; ; p++) {
        const result = await adminApi.categories({ page: p, size: 100 })
        if (version !== loadVersion) return
        all.push(...result.content)
        if (result.last || p + 1 >= result.totalPages) break
      }
      categories.value = all
    }
  } catch (e) { if (version === loadVersion) error.value = e.message }
  finally { if (version === loadVersion) loading.value = false }
}

function clearFilterValues() {
  keyword.value = ''; status.value = ''
  Object.keys(filters).forEach(key => { filters[key] = '' })
  appliedFilters.value = {}
}
watch(()=>route.fullPath,()=>{message.value='';restoreQuery()},{immediate:true})
onBeforeUnmount(()=>{++loadVersion})

function restoreQuery(){
 clearFilterValues()
 try{
  const params=parseAdminListQuery(section.value,route.query)
  page.value=params.page
  keyword.value=params.keyword||params.orderNo||'';status.value=params.status||''
  for(const key of Object.keys(filters))filters[key]=params[key]===undefined?'':String(params[key])
  appliedFilters.value=Object.fromEntries(Object.entries(params).filter(([key])=>!['page','size','sort'].includes(key)))
  load()
 }catch(e){++loadVersion;loading.value=false;rows.value=[];totalPages.value=0;error.value=e.message}
}

function navigateQuery(query){
 if(blocked.value)return
 const target={name:'admin',params:{section:section.value},query}
 if(router.resolve(target).fullPath===route.fullPath)restoreQuery();else router.push(target)
}
const navigateCategoryQuery=navigateQuery,navigateProductQuery=navigateQuery,navigateOrderQuery=navigateQuery

function search() {
  if(blocked.value||loading.value)return
  const params = {}
  for(const key of ['size','sort','returnTo'])if(route.query[key]!==undefined)params[key]=route.query[key]
  const term = keyword.value.trim()
  if (term) params[section.value === 'orders' ? 'orderNo' : 'keyword'] = term
  if (section.value !== 'categories' && status.value) params.status = status.value
  if (section.value !== 'orders' && filters.isActive !== '') params.isActive = filters.isActive === 'true'
  if (section.value === 'products') {
    if (filters.categoryId !== '') params.categoryId = filters.categoryId
    if (filters.brand.trim()) params.brand = filters.brand.trim()
  }
  if (section.value === 'orders') {
    const memberId = filters.memberId.trim()
    if (memberId && (!/^[1-9][0-9]{0,18}$/.test(memberId) || BigInt(memberId) > 9223372036854775807n)) {
      error.value = '會員編號須為有效的正整數'; return
    }
    if (filters.from && filters.to && filters.from > filters.to) {
      error.value = '開始日期不可晚於結束日期'; return
    }
    if (memberId) params.memberId = memberId
    if (filters.from) params.from = filters.from
    if (filters.to) params.to = filters.to
  }
  if (section.value === 'orders') { navigateOrderQuery(params); return }
  if (section.value === 'products') { navigateProductQuery(params); return }
  if (section.value === 'categories') { navigateCategoryQuery(params); return }
  appliedFilters.value = params
  page.value = 0; load()
}
function resetFilters() { const context=section.value==='products'&&typeof route.query.returnTo==='string'?{returnTo:route.query.returnTo}:{};navigateQuery(context) }
function edit(row) {
  if(blocked.value||loading.value)return
  const prefix=section.value==='products'?'admin-product':'admin-category'
  router.push({name:prefix+(row?'-edit':'-new'),params:row?{id:row.id}:{},query:route.query})
}

async function disable(row) {
  if(blocked.value||loading.value||!row.isActive||!rows.value.includes(row)||!['products','categories'].includes(section.value))return
  auth.syncFromStorage()
  if(!auth.isLoggedIn||auth.member?.role!=='ADMIN')return
  const currentSection=section.value,path=route.fullPath,version=loadVersion,epoch=auth.epoch,id=row.id,name=row.name
  const action=currentSection==='products'?'下架商品':'停用分類'
  const current=()=>{auth.syncFromStorage();return route.fullPath===path&&section.value===currentSection&&auth.epoch===epoch&&auth.isLoggedIn&&auth.member?.role==='ADMIN'}
  const accepted=await confirmation.ask({title:`確認${action}？`,description:currentSection==='products'?'商品下架後將停止販售，請確認操作對象。':'分類將設為停用；若仍有子分類或上架商品，系統會拒絕操作。',detail:`${currentSection==='products'?'商品':'分類'}：${name}\n編號：#${id}`,cancelLabel:'保留不變',confirmLabel:`確認${action}`})
  if(!accepted||!current()||version!==loadVersion||loading.value||saving.value||!rows.value.includes(row)||!row.isActive||row.id!==id||row.name!==name)return
  saving.value = true
  error.value='';message.value=''
  try {
    if (currentSection === 'products') await adminApi.offShelf(id)
    else await adminApi.deactivateCategory(id)
    if(!current()||version!==loadVersion)return
    const refresh=load(),refreshVersion=loadVersion
    await refresh
    if(current()&&refreshVersion===loadVersion)message.value=currentSection==='products'?'商品已下架':'分類已停用'
  } catch (e) { if(current()&&version===loadVersion)error.value = e.message }
  finally { saving.value = false }
}
function retryLoad(){if(!blocked.value&&!loading.value)restoreQuery()}

function fillDemo() {
 if (loading.value || blocked.value) return
 const row = rows.value[0]
 Object.assign(filters, { categoryId: '', brand: '', isActive: '', memberId: '', from: '', to: '' })
 status.value = ''
 if (section.value === 'products') {
  keyword.value = row?.name || '雞肉'; filters.categoryId = row?.categoryId || ''; filters.brand = row?.brand || ''; filters.isActive = row ? String(row.isActive) : 'true'; status.value = row?.status || 'ON_SALE'
 } else if (section.value === 'categories') {
  keyword.value = row?.name || '主食'; filters.isActive = row ? String(row.isActive) : 'true'
 } else {
  keyword.value = row?.orderNo || ''; filters.memberId = row?.memberId ? String(row.memberId) : ''; status.value = row?.status || ''
  const date = row?.createdAt ? new Date(row.createdAt) : new Date()
  if (Number.isFinite(date.getTime())) { filters.from = new Date(date.getTime()-86400000).toISOString().slice(0,10); filters.to = new Date(date.getTime()+86400000).toISOString().slice(0,10) }
 }
}
</script>

<template>
  <div class="page"><div class="container">
      <RouterLink v-if="section==='products'&&route.query.returnTo!==undefined" class="btn btn-ghost mb-2" :to="inventoryReturn">返回低庫存清單</RouterLink>
      <nav class="row mb-2" aria-label="管理後台">
        <RouterLink :to="adminPath('products')" class="btn btn-ghost">商品</RouterLink>
        <RouterLink :to="adminPath('categories')" class="btn btn-ghost">分類</RouterLink>
        <RouterLink :to="adminPath('orders')" class="btn btn-ghost">訂單</RouterLink>
    </nav>
    <div class="admin-page-heading"><div><p class="eyebrow">STORE OPERATIONS</p><h1 class="page-title">{{ title }}</h1><p class="page-sub">{{ section === 'products' ? '整理商品與規格，讓每一件好物準備就緒。' : section === 'categories' ? '整理選購動線，讓顧客更容易找到需要的商品。' : '從訂單詳情到狀態更新，掌握每一筆購物流程。' }}</p></div><span class="admin-section-label">喵喵商城 Admin</span></div>
    <div class="admin-summary" aria-label="目前列表摘要"><div><span>當頁資料</span><strong>{{ loading ? '—' : rows.length }}<small> 筆</small></strong></div><div><span>查詢總頁數</span><strong>{{ loading ? '—' : totalPages }}<small> 頁</small></strong></div><div><span>已套用篩選</span><strong>{{ Object.keys(appliedFilters).length }}<small> 個條件</small></strong></div></div>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }} <button class="btn btn-ghost btn-sm" :disabled="loading||blocked" @click="retryLoad">重試載入</button></p>
    <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
    <form class="row mb-2 admin-filter-panel" style="flex-wrap: wrap; align-items: end;" aria-label="管理列表篩選" @submit.prevent="search">
<DemoFillButton :disabled="loading || blocked" @fill="fillDemo" label="一鍵填入搜尋條件" />
      <fieldset class="admin-filter-fields" :disabled="blocked">
      <label>{{ section === 'orders' ? '訂單編號' : '名稱搜尋' }}<input v-model="keyword" class="input" style="max-width: 260px" :placeholder="section === 'orders' ? '訂單編號' : '名稱搜尋'" /></label>
      <label v-if="section !== 'categories'">狀態<select v-model="status" aria-label="狀態" class="select" style="max-width: 180px" @change="search">
        <option value="">全部狀態</option>
        <option v-for="s in section === 'orders' ? Object.keys(transitions) : ['ON_SALE', 'OFF_SHELF', 'SOLD_OUT']" :key="s" :value="s">{{ labels[s] }}</option>
      </select></label>
      <template v-if="section === 'products'">
        <label>分類篩選<select v-model="filters.categoryId" aria-label="分類篩選" class="select"><option value="">全部分類</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}{{ c.isActive ? '' : '（停用）' }}</option></select></label>
        <label>品牌篩選<input v-model="filters.brand" class="input" maxlength="100" placeholder="完整品牌名稱" /></label>
      </template>
      <label v-if="section !== 'orders'">啟用狀態<select v-model="filters.isActive" aria-label="啟用狀態" class="select"><option value="">全部</option><option value="true">啟用</option><option value="false">停用</option></select></label>
      <template v-if="section === 'orders'">
        <label>會員編號<input v-model="filters.memberId" class="input" inputmode="numeric" maxlength="19" placeholder="會員 ID" /></label>
        <label>開始日期<input v-model="filters.from" class="input" type="date" /></label>
        <label>結束日期<input v-model="filters.to" class="input" type="date" /></label>
      </template>
      <button type="submit" class="btn btn-primary" :disabled="loading">搜尋</button>
      <label v-if="section==='products'||section==='orders'">列表排序<select class="select" aria-label="列表排序" :value="route.query.sort||'createdAt,desc'" :disabled="loading" @change="changeSort($event.target.value)"><option v-for="option in sortOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
      <button type="button" class="btn btn-ghost" @click="resetFilters" :disabled="loading">清除篩選</button>
      <button v-if="section !== 'orders'" type="button" class="btn btn-ghost" @click="edit(null)">新增</button>
      </fieldset>
    </form>
    <div v-if="loading" class="spinner">載入中…</div>
    <div v-else-if="!error" class="card" style="overflow-x: auto"><table class="table">
      <thead><tr><th>編號</th><th>{{ section === 'orders' ? '訂單' : '名稱' }}</th><th v-if="section === 'orders'">會員／建立時間</th><th>狀態</th><th>{{ section === 'products' ? '售價 / 庫存' : section === 'orders' ? '金額' : '排序' }}</th><th>操作</th></tr></thead>
      <tbody><tr v-for="row in rows" :key="row.id">
        <td class="muted">#{{ row.id }}</td><td class="table-record-name">{{ row.name || row.orderNo }}</td><td v-if="section === 'orders'">#{{ row.memberId }} {{ row.memberName }}<br />{{ row.memberEmail }}<br />{{ row.createdAt?.replace('T', ' ') }}</td><td><span class="record-status" :class="{ 'record-inactive': row.status === 'CANCELLED' || row.status === 'OFF_SHELF' || row.isActive === false }">{{ labels[row.status] || (row.isActive ? '啟用' : '停用') }}</span></td>
        <td>{{ section === 'products' ? `${row.basePrice} / ${row.totalStock}` : section === 'orders' ? row.totalAmount : row.sortOrder }}</td>
        <td class="row"><RouterLink v-if="section === 'orders'" class="btn btn-ghost btn-sm" :to="{name:'admin-order-detail',params:{id:row.id},query:route.query}">詳情</RouterLink><button v-else class="btn btn-ghost btn-sm" :disabled="blocked||loading" @click="edit(row)">編輯</button><button v-if="section !== 'orders'" class="btn btn-danger btn-sm" :disabled="blocked || loading || !row.isActive" @click="disable(row)">{{ section === 'products' ? '下架' : '停用' }}</button></td>
      </tr><tr v-if="!rows.length"><td :colspan="section === 'orders' ? 6 : 5">沒有符合條件的資料</td></tr></tbody>
    </table></div>
  <ListPagination v-if="!error" :page="page" :total-pages="totalPages" :loading="loading||blocked" />


  </div></div>
  <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>
.admin-filter-fields{display:contents}
</style>
