<script setup>
import DemoFillButton from '@/components/DemoFillButton.vue'
import { skuDraft } from '@/utils/demoFill'
import {adminReturnQuery} from '@/utils/adminListQuery'
import ProductImage from '@/components/ProductImage.vue'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useAdminDraftGuard} from '@/composables/useAdminDraftGuard'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi } from '@/api/admin'
const route=useRoute(),isImages=computed(()=>route.meta.productAssets==='images')
const ready=ref(false),loading=ref(false),saving=ref(false),error=ref(''),message=ref(''),detail=ref(null)
const form=reactive({images:[],imageUrl:''}),sku=reactive({})
const baseline=ref(''),skuBaseline=ref(''),newSkuBaseline=ref('')
const copy=data=>JSON.parse(JSON.stringify(data))
let sequence=0,expectedImageIds=[]
const dirty=computed(()=>ready.value&&(isImages.value?JSON.stringify(form)!==baseline.value:JSON.stringify(detail.value.skus)!==skuBaseline.value||JSON.stringify(sku)!==newSkuBaseline.value))
const listQuery=computed(()=>adminReturnQuery('products',route.query))
const editTarget=computed(()=>({name:'admin-product-edit',params:{id:route.params.id},query:listQuery.value}))
const listTarget=computed(()=>({name:'admin',params:{section:'products'},query:listQuery.value}))
function resetSku(price){Object.assign(sku,{skuCode:'',specName:'',price,stock:0,safetyStock:0,isActive:true});newSkuBaseline.value=JSON.stringify(sku)}
async function load(){
 const current=++sequence;ready.value=false;loading.value=true;error.value='';detail.value=null
 try{
  const id=String(route.params.id)
  if(!/^[1-9][0-9]{0,18}$/.test(id)||BigInt(id)>9223372036854775807n)throw new Error('商品編號格式不正確')
  const data=await adminApi.product(id);if(current!==sequence)return
  detail.value=copy(data);form.images=copy(data.images);form.imageUrl=''
  expectedImageIds=data.images.map(img=>img.id);baseline.value=JSON.stringify(form)
  skuBaseline.value=JSON.stringify(data.skus);resetSku(data.basePrice);ready.value=true
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
watch([()=>route.params.id,isImages],()=>{message.value='';load()},{immediate:true})
const {confirmation,blocked,retry}=useAdminDraftGuard({dirty,saving,loading,version:()=>sequence,reload:()=>{message.value='';load()},label:computed(()=>isImages.value?'圖片資料':'SKU 資料')})
function beforeUnload(e){if(dirty.value||saving.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{++sequence;window.removeEventListener('beforeunload',beforeUnload)})
function selectPrimary(index){if(blocked.value)return;form.images.forEach((img,i)=>img.isPrimary=i===index)}
function removeImage(index){if(blocked.value)return;form.images.splice(index,1);if(form.images.length&&!form.images.some(img=>img.isPrimary))selectPrimary(0)}
function moveImage(index,offset){if(blocked.value)return;const target=index+offset;if(target<0||target>=form.images.length)return;const [image]=form.images.splice(index,1);form.images.splice(target,0,image)}
function appendImage(){
 if(!form.imageUrl.trim()||blocked.value)return
 if(form.images.length>=100){error.value='最多 100 張圖片';return}
 form.images.push({url:form.imageUrl.trim(),altText:detail.value.name,sortOrder:form.images.length,isPrimary:!form.images.length});form.imageUrl=''
}
async function saveImages(){
 if(blocked.value||!ready.value)return
 if(form.imageUrl.trim()&&form.images.length>=100){error.value='最多 100 張圖片';return}
 if(form.imageUrl.trim())appendImage()
 if(form.images.length>100){error.value='最多 100 張圖片';return}
 if(form.images.length&&form.images.filter(img=>img.isPrimary).length!==1){error.value='圖片清單須選擇一張主圖';return}
 const current=sequence;saving.value=true;error.value='';message.value=''
 try{
  const images=form.images.map(({url,altText,isPrimary},sortOrder)=>({url,altText,isPrimary,sortOrder}))
  const result=await adminApi.updateProductImages(route.params.id,{expectedImageIds:[...expectedImageIds],images})
  if(current!==sequence)return
  form.images=copy(result);expectedImageIds=result.map(img=>img.id);baseline.value=JSON.stringify(form);message.value='圖片已儲存'
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false}
}
async function saveSku(item){
 if(blocked.value||!ready.value)return
 if(!item.specName?.trim()||item.specName.length>200||(!item.id&&(!item.skuCode?.trim()||item.skuCode.length>64))){error.value='請填寫有效的 SKU 編號與規格名稱';return}
 if(!Number.isFinite(item.price)||item.price<0||[item.stock,item.safetyStock].some(n=>!Number.isInteger(n)||n<0||n>2147483647)){error.value='價格不可為負，庫存與安全庫存須為有效的非負整數';return}
 const current=sequence;saving.value=true;error.value='';message.value=''
 try{
  const result=item.id?await adminApi.updateSku(item.id,{...item}):await adminApi.addSku(route.params.id,{...sku})
  if(current!==sequence)return
  const clean=JSON.parse(skuBaseline.value)
  if(item.id){detail.value.skus[detail.value.skus.findIndex(s=>s.id===item.id)]=copy(result);clean[clean.findIndex(s=>s.id===item.id)]=copy(result)}
  else{detail.value.skus.push(copy(result));clean.push(copy(result));resetSku(form.basePrice)}
  skuBaseline.value=JSON.stringify(clean);message.value='規格已儲存'
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false}
}

async function uploadImage(event){
 const file=event.target.files?.[0];if(!file||blocked.value)return
 if(!['image/png','image/jpeg'].includes(file.type)||file.size>5*1024*1024){error.value='請選擇 5 MB 以下的 PNG／JPEG 圖片';event.target.value='';return}
 const current=sequence;saving.value=true;error.value='';message.value=''
 try{const result=await adminApi.uploadImage(file);if(current===sequence){form.imageUrl=result.url;message.value='圖片已上傳，儲存圖片清單後生效'}}
 catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false;event.target.value=''}
}
function fillImages() {
 if (blocked.value || !ready.value) return
 form.imageUrl = detail.value.images?.[0]?.url || '/product-images/orijen-cat-dry.png'
 form.images.forEach(image => { image.altText = detail.value.name + ' 商品外觀' })
 error.value = ''; message.value = ''
}
function fillSku(item = sku) {
 if (blocked.value || !ready.value) return
 const draft = skuDraft(detail.value.basePrice)
 if (item.id) { delete draft.skuCode; draft.specName = item.specName || draft.specName }
 Object.assign(item, draft); error.value = ''; message.value = ''
}
</script>
<template>
 <div class="page"><div class="container">
  <nav class="row" aria-label="商品操作導覽"><RouterLink class="btn btn-ghost" :to="editTarget">返回商品編輯</RouterLink><RouterLink class="btn btn-ghost" :to="listTarget">返回商品列表</RouterLink></nav>
  <h1 class="page-title mt-2">{{ isImages?'商品圖片管理':'SKU 規格管理' }}</h1>
  <p v-if="loading" role="status">載入商品中…</p>
  <p v-if="error" class="alert alert-error" role="alert"><span>{{ error }}</span> <button class="btn btn-ghost" :disabled="blocked||loading" @click="retry">重新載入</button></p>
  <button v-if="ready&&!error" class="btn btn-ghost btn-sm" :disabled="blocked||loading" @click="retry">重新載入</button>
  <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
  <p v-if="ready">{{ detail.name }} · #{{ detail.id }}</p>
  <form v-if="ready&&isImages" class="card image-panel" aria-label="商品圖片管理" @submit.prevent="saveImages">
   <fieldset :disabled="blocked">
<DemoFillButton :disabled="blocked" @fill="fillImages" />
    <p class="muted">只儲存圖片關聯，不更改名稱、分類、價格或 SKU。上傳取得網址後，仍需儲存清單。</p>
    <div class="field"><label>圖片網址<input v-model="form.imageUrl" class="input" maxlength="500" /></label></div>
    <label>上傳 PNG / JPEG（最多 5 MB）<input type="file" accept="image/png,image/jpeg" @change="uploadImage" /></label>
    <button type="button" class="btn btn-ghost" :disabled="!form.imageUrl.trim()" @click="appendImage">加入圖片清單</button>
    <p v-if="!form.images.length" class="muted">尚無圖片。空清單儲存後會移除商品圖片關聯，不刪除上傳檔案。</p>
    <div v-for="(image,index) in form.images" :key="index" class="image-row">
     <ProductImage :src="image.url" :alt="image.altText||'商品圖片'" />
     <label>替代文字<input v-model="image.altText" class="input" maxlength="200" :aria-label="'圖片 '+(index+1)+' 替代文字'" /></label>
     <label><input type="radio" name="primary-image" :checked="image.isPrimary" @change="selectPrimary(index)" />主圖</label>
     <button type="button" class="btn btn-ghost btn-sm" :disabled="index===0" @click="moveImage(index,-1)">往前</button>
     <button type="button" class="btn btn-ghost btn-sm" :disabled="index+1===form.images.length" @click="moveImage(index,1)">往後</button>
     <button type="button" class="btn btn-danger btn-sm" @click="removeImage(index)">移除圖片</button>
    </div>
    <button class="btn btn-primary mt-2">{{ saving?'儲存中…':'儲存圖片' }}</button>
   </fieldset>
  </form>
    <section v-if="ready && !isImages" class="card mt-3" style="padding: 24px; overflow-x: auto">
      <fieldset :disabled="blocked"><h2>SKU 價格與庫存</h2>
      <table class="table"><thead><tr><th>編號</th><th>規格</th><th>價格</th><th>庫存</th><th>安全庫存</th><th>啟用</th><th></th></tr></thead><tbody><tr v-for="item in detail.skus" :key="item.id"><td>{{ item.skuCode }}</td><td><input v-model="item.specName" :aria-label="item.skuCode+' 規格名稱'" maxlength="200" class="input" /></td><td><input v-model.number="item.price" :aria-label="item.skuCode+' 價格'" class="input" type="number" min="0" step="0.01" /></td><td><input v-model.number="item.stock" :aria-label="item.skuCode+' 庫存'" class="input" type="number" min="0" /></td><td><input v-model.number="item.safetyStock" :aria-label="item.skuCode+' 安全庫存'" class="input" type="number" min="0" /></td><td><input v-model="item.isActive" :aria-label="item.skuCode+' 啟用'" type="checkbox" /></td><td><DemoFillButton :disabled="blocked" @fill="fillSku(item)" /><button class="btn btn-primary btn-sm" :disabled="saving" @click="saveSku(item)">儲存</button></td></tr></tbody></table>
      <form class="row mt-2" style="flex-wrap: wrap" @submit.prevent="saveSku(sku)"><DemoFillButton :disabled="blocked" @fill="fillSku()" /><input v-model="sku.skuCode" class="input" placeholder="新 SKU 編號" aria-label="新 SKU 編號" maxlength="64" required /><input v-model="sku.specName" class="input" placeholder="規格" aria-label="新 SKU 規格" maxlength="200" required /><label>價格<input v-model.number="sku.price" class="input" type="number" min="0" step="0.01" required /></label><label>庫存<input v-model.number="sku.stock" class="input" type="number" min="0" step="1" required /></label><label>安全庫存<input v-model.number="sku.safetyStock" class="input" type="number" min="0" step="1" required /></label><button class="btn btn-primary" :disabled="saving">新增規格</button></form>
      </fieldset>
    </section>
 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>
fieldset{border:0;padding:0;margin:0;min-width:0}
.row,.image-row{display:flex;gap:12px;align-items:center;flex-wrap:wrap}
.image-panel{padding:24px}.image-row{margin-top:18px}
.image-row img{width:80px;height:80px;object-fit:contain}
input[type=file]{max-width:100%}label{min-width:0}.card{overflow-wrap:anywhere}
</style>
