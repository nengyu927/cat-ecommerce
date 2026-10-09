<script setup>
import {adminReturnQuery} from '@/utils/adminListQuery'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useAdminDraftGuard} from '@/composables/useAdminDraftGuard'
import { adminApi } from '@/api/admin'
const route=useRoute(),router=useRouter()
const isNew=computed(()=>route.name==='admin-category-new')
const categories=ref([]),ready=ref(false),loading=ref(false),saving=ref(false),error=ref(''),message=ref('')
const defaults=()=>({name:'',slug:'',parentId:null,sortOrder:0,isActive:true})
const form=reactive(defaults()),baseline=ref('')
let sequence=0
const dirty=computed(()=>ready.value&&JSON.stringify(form)!==baseline.value)
const listQuery=computed(()=>adminReturnQuery('categories',route.query))
const listTarget=computed(()=>({name:'admin',params:{section:'categories'},query:listQuery.value}))
const parentCategories=computed(()=>{
 const children=new Map(),excluded=new Set()
 for(const category of categories.value){
  const key=String(category.parentId),siblings=children.get(key)||[]
  siblings.push(String(category.id));children.set(key,siblings)
 }
 const pending=isNew.value?[]:[String(route.params.id)]
 while(pending.length){const id=pending.pop();if(excluded.has(id))continue;excluded.add(id);pending.push(...(children.get(id)||[]))}
 return categories.value.filter(category=>!excluded.has(String(category.id)))
})
function setForm(data){Object.assign(form,Object.fromEntries(Object.keys(defaults()).map(key=>[key,data[key]??defaults()[key]])));baseline.value=JSON.stringify(form)}
async function load(){
 const current=++sequence;ready.value=false;loading.value=true;error.value='';categories.value=[]
 try{
  const id=String(route.params.id||'')
  if(!isNew.value&&(!/^[1-9][0-9]{0,18}$/.test(id)||BigInt(id)>9223372036854775807n))throw new Error('分類編號格式不正確')
  const all=[]
  for(let page=0;;page++){
   const result=await adminApi.categories({page,size:100})
   if(current!==sequence)return
   all.push(...result.content)
   if(result.last||page+1>=result.totalPages)break
  }
  if(current!==sequence)return
  const data=isNew.value?defaults():all.find(category=>String(category.id)===id)
  if(!data)throw new Error('找不到分類')
  categories.value=all;setForm(data);ready.value=true
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
watch([()=>route.params.id,isNew],()=>{message.value='';load()},{immediate:true})
const {confirmation,blocked,retry}=useAdminDraftGuard({dirty,saving,loading,version:()=>sequence,reload:()=>{message.value='';load()},label:'分類資料'})
function beforeUnload(e){if(dirty.value||saving.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{++sequence;window.removeEventListener('beforeunload',beforeUnload)})
async function save(){
 if(blocked.value||!ready.value)return
 if(!form.name.trim()||form.name.length>100||!/^[a-z0-9-]+$/.test(form.slug)||form.slug.length>120){error.value='請填寫有效的分類名稱與網址代稱';return}
 if(!Number.isInteger(form.sortOrder)||form.sortOrder<-2147483648||form.sortOrder>2147483647){error.value='排序須為有效的整數';return}
 if(form.parentId!==null&&!parentCategories.value.some(category=>category.id===form.parentId)){error.value='上層分類不可為自己或子孫，請重新選擇';return}
 const current=sequence;saving.value=true;error.value='';message.value=''
 try{
  const payload={...form}
  const result=await(isNew.value?adminApi.createCategory(payload):adminApi.updateCategory(route.params.id,payload))
  if(current!==sequence)return
  setForm(result);message.value='分類已儲存'
  if(isNew.value){saving.value=false;await router.replace({name:'admin-category-edit',params:{id:result.id},query:listQuery.value});message.value='分類已儲存'}
  else{const index=categories.value.findIndex(category=>String(category.id)===String(route.params.id));if(index>=0)categories.value[index]=result}
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)saving.value=false}
}
</script>
<template>
 <div class="page"><div class="container">
  <RouterLink class="btn btn-ghost" :to="listTarget">返回分類列表</RouterLink>
  <h1 class="page-title mt-2">{{ isNew?'新增分類':'編輯分類' }}</h1>
  <p v-if="loading" role="status">載入分類中…</p>
  <p v-if="error" class="alert alert-error" role="alert"><span>{{ error }}</span> <button class="btn btn-ghost" :disabled="blocked||loading" @click="retry">重新載入</button></p>
  <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
  <button v-if="ready&&!error" type="button" class="btn btn-ghost mb-2" :disabled="blocked||loading" @click="retry">重新載入</button>
  <form v-if="ready" class="card category-form" aria-label="分類編輯" @submit.prevent="save">
   <fieldset :disabled="blocked">
    <div class="field"><label>名稱<input v-model="form.name" class="input" maxlength="100" required /></label></div>
    <div class="field"><label>網址代稱（小寫英數字與連字號）<input v-model="form.slug" class="input" maxlength="120" pattern="[a-z0-9-]+" required /></label></div>
    <div class="field"><label>上層分類<select v-model="form.parentId" class="select" aria-label="上層分類"><option :value="null">無（根分類）</option><option v-for="category in parentCategories" :key="category.id" :value="category.id">{{ category.name }}{{ category.isActive?'':'（停用）' }}</option></select></label><small>已排除自己與所有子孫（含停用分類）；儲存時由後端再次檢查最新階層。</small></div>
    <div class="field"><label>排序<input v-model.number="form.sortOrder" class="input" type="number" step="1" min="-2147483648" max="2147483647" required /></label></div>
    <label><input v-model="form.isActive" type="checkbox" />啟用</label>
    <div class="row mt-2"><button class="btn btn-primary">{{ saving?'儲存中…':'儲存' }}</button><RouterLink class="btn btn-ghost" :to="listTarget">返回列表</RouterLink></div>
   </fieldset>
  </form>
 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>
.category-form{padding:24px;max-width:760px;overflow-wrap:anywhere}
fieldset{border:0;padding:0;margin:0;min-width:0}.row{flex-wrap:wrap}
</style>
