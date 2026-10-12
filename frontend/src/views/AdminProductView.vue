<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import { productDraft, skuDraft } from '@/utils/demoFill'
import {adminReturnQuery} from '@/utils/adminListQuery'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useAdminDraftGuard} from '@/composables/useAdminDraftGuard'
import { adminApi } from '@/api/admin'
const route=useRoute(),router=useRouter()
const isNew=computed(()=>route.name==='admin-product-new')
const ready=ref(false),loading=ref(false),saving=ref(false),error=ref(''),message=ref('')
const categories=ref([]),detail=ref(null),form=reactive({}),sku=reactive({})
const labels={ON_SALE:'上架',OFF_SHELF:'下架',SOLD_OUT:'售罄'}
const defaults=()=>({name:'',slug:'',categoryId:null,brand:'',description:'',ingredients:'',suitableAge:'',origin:'',storageInstructions:'',basePrice:0,status:'ON_SALE',isActive:true,images:[],imageUrl:''})
const copy=data=>JSON.parse(JSON.stringify(data))
let sequence=0
const baseline=ref(''),skuBaseline=ref(''),newSkuBaseline=ref('')
const dirty=computed(()=>ready.value&&(JSON.stringify(form)!==baseline.value||(isNew.value&&JSON.stringify(sku)!==newSkuBaseline.value)))
const listQuery=computed(()=>adminReturnQuery('products',route.query))
const listTarget=computed(()=>({name:'admin',params:{section:'products'},query:listQuery.value}))
function setForm(data){Object.keys(form).forEach(k=>delete form[k]);Object.assign(form,{...defaults(),...copy(data),imageUrl:''})}
function resetSku(price){Object.keys(sku).forEach(k=>delete sku[k]);Object.assign(sku,{skuCode:'',specName:'',price,stock:0,safetyStock:0,isActive:true});newSkuBaseline.value=JSON.stringify(sku)}
async function load(){
 const current=++sequence;loading.value=true;ready.value=false;error.value='';detail.value=null;categories.value=[]
 try{
  const id=String(route.params.id||'')
  if(!isNew.value&&(!/^[1-9][0-9]{0,18}$/.test(id)||BigInt(id)>9223372036854775807n))throw new Error('商品編號格式不正確')
  const data=isNew.value?defaults():await adminApi.product(id),all=[]
  if(current!==sequence)return
  for(let page=0;;page++){
   const result=await adminApi.categories({page,size:100})
   if(current!==sequence)return
   all.push(...result.content)
   if(result.last||page+1>=result.totalPages)break
  }
  if(current!==sequence)return
  categories.value=all;if(isNew.value)data.categoryId=all.find(c=>c.isActive)?.id||null
  detail.value=copy(data);setForm(data);resetSku(data.basePrice)
  baseline.value=JSON.stringify(form);skuBaseline.value=JSON.stringify(detail.value?.skus||[]);ready.value=true
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
watch([()=>route.params.id,isNew],()=>{message.value='';load()},{immediate:true})
const {confirmation,blocked,retry}=useAdminDraftGuard({dirty,saving,loading,version:()=>sequence,reload:()=>{message.value='';load()},label:'商品資料'})
function beforeUnload(e){if(dirty.value||saving.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{++sequence;window.removeEventListener('beforeunload',beforeUnload)})
function appendImage(){if(!form.imageUrl||blocked.value)return;form.images.push({url:form.imageUrl.trim(),altText:form.name,sortOrder:form.images.length,isPrimary:!form.images.length});form.imageUrl=''}
function productPayload(){
 const data=Object.fromEntries(Object.keys(defaults()).filter(k=>k!=='imageUrl').map(k=>[k,copy(form[k])]))
 if(!isNew.value)delete data.images
 if(isNew.value)data.images=data.images.map(({url,altText,sortOrder,isPrimary})=>({url,altText,sortOrder,isPrimary}))
 return data
}
async function save(){
 if(blocked.value||!ready.value)return
 if(form.imageUrl)appendImage()
 const current=sequence;saving.value=true;error.value='';message.value=''
 try{
  const data=productPayload();if(isNew.value)data.skus=[{...sku}]
  const result=await(isNew.value?adminApi.createProduct(data):adminApi.updateProduct(route.params.id,data))
  if(current!==sequence)return
  baseline.value=JSON.stringify(form);message.value='已儲存'
  if(isNew.value){newSkuBaseline.value=JSON.stringify(sku);saving.value=false;await router.replace({name:'admin-product-edit',params:{id:result.id},query:listQuery.value});message.value='已儲存'}
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false}
}
async function uploadImage(event){
 const file=event.target.files?.[0];if(!file||blocked.value)return
 if(!['image/png','image/jpeg'].includes(file.type)||file.size>5*1024*1024){error.value='請選擇 5 MB 以下的 PNG／JPEG 圖片';event.target.value='';return}
 const current=sequence;saving.value=true;error.value=''
 try{const result=await adminApi.uploadImage(file);if(current===sequence){form.imageUrl=result.url;message.value='圖片已上傳，儲存商品後生效'}}
 catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false;event.target.value=''}
}
function fillDemo() {
 if (blocked.value || !ready.value) return
 const category = categories.value.find(c => c.isActive && /乾糧|主食|飼料/.test(c.name)) || categories.value.find(c => c.isActive)
 Object.assign(form, productDraft(category?.id ?? form.categoryId))
 if (!isNew.value) form.slug = detail.value.slug
 if (isNew.value) { Object.assign(sku, skuDraft(form.basePrice)); form.imageUrl = '/product-images/orijen-cat-dry.png'; form.images = [] }
 error.value = ''; message.value = ''
}
</script>
<template>
 <div class="page"><div class="container">
  <RouterLink class="btn btn-ghost" :to="listTarget">返回商品列表</RouterLink>
  <h1 class="page-title mt-2">{{ isNew?'新增商品':'編輯商品' }}</h1>
  <p class="muted">圖片與 SKU 請使用各自的管理頁；新增商品先建立第一個規格。</p>
  <p v-if="loading" role="status">載入商品中…</p>
  <p v-if="error" class="alert alert-error" role="alert"><span>{{ error }}</span> <button class="btn btn-ghost" :disabled="blocked||loading" @click="retry">重新載入</button></p>
  <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
    <nav v-if="ready&&!isNew" class="row mt-2" aria-label="商品操作">
      <RouterLink class="btn btn-primary" :to="{name:'admin-product-skus',params:{id:route.params.id},query:listQuery}">管理 SKU</RouterLink>
      <RouterLink class="btn btn-ghost" :to="{name:'admin-product-images',params:{id:route.params.id},query:listQuery}">管理圖片</RouterLink>
    </nav>
    <button v-if="ready&&!error" type="button" class="btn btn-ghost mb-2" :disabled="blocked||loading" @click="retry">重新載入</button>
  <form v-if="ready" class="card mt-3" style="padding: 24px" @submit.prevent="save">
<DemoFillButton :disabled="blocked" @fill="fillDemo" />
      <fieldset :disabled="blocked"><h2>{{ isNew ? '新增商品' : '編輯商品' }}</h2>
      <div class="field"><label>名稱<input v-model="form.name" class="input" required maxlength="200" /></label></div>
      <div class="field"><label>網址代稱（小寫英數字與連字號）<input v-model="form.slug" class="input" required pattern="[a-z0-9-]+" /></label></div>

        <div class="field"><label>分類<select v-model="form.categoryId" class="select" aria-label="分類" required><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}{{ c.isActive ? '' : '（停用）' }}</option></select></label></div>
        <div class="field"><label>品牌<input v-model="form.brand" class="input" maxlength="100" /></label></div>
        <div class="field"><label>描述<textarea v-model="form.description" class="textarea"></textarea></label></div>
        <div class="field"><label>成分<textarea v-model="form.ingredients" class="textarea" maxlength="10000"></textarea></label></div>
        <div class="field"><label>適用年齡<input v-model="form.suitableAge" class="input" maxlength="100" /></label></div>
        <div class="field"><label>產地<input v-model="form.origin" class="input" maxlength="100" /></label></div>
        <div class="field"><label>保存方式<input v-model="form.storageInstructions" class="input" maxlength="500" /></label></div>
        <div class="field"><label>展示售價<input v-model.number="form.basePrice" class="input" type="number" min="0" step="0.01" required /></label></div>
        <div class="field"><label>狀態<select v-model="form.status" class="select"><option v-for="s in ['ON_SALE', 'OFF_SHELF', 'SOLD_OUT']" :key="s" :value="s">{{ labels[s] }}</option></select></label></div>
      <template v-if="isNew">
        <div class="field"><label>圖片網址<input v-model="form.imageUrl" class="input" placeholder="https://... 或 /api/images/..." /></label><label>上傳 PNG / JPEG（最多 5 MB）<input type="file" accept="image/png,image/jpeg" :disabled="blocked" @change="uploadImage" /></label><img v-if="form.imageUrl" :src="form.imageUrl" alt="商品圖片預覽" style="width: 120px" /><button type="button" class="btn btn-ghost" @click="appendImage" :disabled="!form.imageUrl">加入圖片清單</button></div>
        <div v-for="(image, index) in form.images" :key="index" class="row mb-2"><img :src="image.url" :alt="image.altText" style="width: 64px" /><label><input type="radio" name="primary-image" :checked="image.isPrimary" @change="form.images.forEach((img, i) => img.isPrimary = i === index)" />主圖</label><button type="button" class="btn btn-danger btn-sm" @click="form.images.splice(index, 1)">移除圖片</button></div>
      </template>

      <label><input v-model="form.isActive" type="checkbox" />啟用</label>
      <fieldset v-if="isNew" class="mt-2"><legend>第一個規格</legend><div class="row" style="flex-wrap: wrap"><input v-model="sku.skuCode" class="input" placeholder="SKU 編號" required /><input v-model="sku.specName" class="input" placeholder="規格名稱" required /><label>價格<input v-model.number="sku.price" class="input" type="number" min="0" step="0.01" required /></label><label>庫存<input v-model.number="sku.stock" class="input" type="number" min="0" step="1" required /></label></div></fieldset>
      <div class="row mt-2"><button class="btn btn-primary" :disabled="blocked">{{ saving ? '儲存中…' : '儲存' }}</button><RouterLink class="btn btn-ghost" :to="listTarget">返回列表</RouterLink></div>
      </fieldset>
    </form>


 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>
fieldset{border:0;padding:0;margin:0;min-width:0}
input[type=file]{max-width:100%}
.row{flex-wrap:wrap}label{min-width:0}
.card{overflow-wrap:anywhere}
</style>
