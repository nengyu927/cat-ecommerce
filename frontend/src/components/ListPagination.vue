<script setup>
import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {queryInteger} from '@/utils/listQuery'
const props=defineProps({page:{type:Number,default:0},totalPages:{type:Number,default:0},loading:Boolean,defaultSize:{type:Number,default:20},pageKey:{type:String,default:'page'},sizeKey:{type:String,default:'size'},labelPrefix:{type:String,default:''}})
const route=useRoute(),router=useRouter(),draft=ref(''),error=ref('')
const size=computed(()=>{try{return queryInteger(route.query[props.sizeKey],props.defaultSize,1,100)}catch{return props.defaultSize}})
const sizes=computed(()=>[...new Set([10,12,20,50,100,size.value])].sort((a,b)=>a-b))
watch([()=>props.page,()=>route.fullPath],()=>{draft.value=String(props.page+1);error.value=''},{immediate:true})
function navigate(changes){
 if(props.loading)return
 error.value=''
 const target={name:route.name,params:route.params,query:{...route.query,...changes},hash:route.hash}
 if(router.resolve(target).fullPath!==route.fullPath)router.push(target)
}
function changeSize(event){navigate({[props.sizeKey]:Number(event.target.value),[props.pageKey]:0})}
function jump(){
 if(props.loading||props.totalPages<1)return
 try{const page=queryInteger(draft.value,1,1,props.totalPages,'頁碼');navigate({[props.pageKey]:page-1})}
 catch(e){error.value=e.message}
}
</script>
<template>
 <section class="list-pagination" :aria-label="labelPrefix+'分頁操作'">
  <div class="paging-row">
   <label>{{ labelPrefix }}每頁筆數<select class="select" :aria-label="labelPrefix+'每頁筆數'" :value="size" :disabled="loading" @change="changeSize"><option v-for="value in sizes" :key="value" :value="value">{{ value }} 筆</option></select></label>
   <div class="paging-buttons"><button class="btn btn-ghost btn-sm" :disabled="loading||page<=0||totalPages===0" @click="navigate({[pageKey]:page-1})">{{ labelPrefix }}上一頁</button><span class="muted" aria-live="polite">{{ totalPages>0?`第 ${page+1} / ${totalPages} 頁`:'沒有分頁資料' }}</span><button class="btn btn-ghost btn-sm" :disabled="loading||page+1>=totalPages" @click="navigate({[pageKey]:page+1})">{{ labelPrefix }}下一頁</button></div>
   <form class="paging-jump" novalidate @submit.prevent="jump"><label>{{ labelPrefix }}前往頁碼<input v-model="draft" class="input" :aria-label="labelPrefix+'前往頁碼'" inputmode="numeric" maxlength="10" :disabled="loading||totalPages===0" /></label><button class="btn btn-ghost btn-sm" :disabled="loading||totalPages===0">{{ labelPrefix }}前往</button></form>
  </div>
  <p v-if="error" class="field-error" role="alert">{{ error }}</p>
 </section>
</template>
<style scoped>
.list-pagination{margin-top:24px;padding:14px;border:1px solid var(--border,#e3e8df);border-radius:14px;background:var(--surface,#fff)}
.paging-row,.paging-buttons,.paging-jump{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.paging-row{justify-content:space-between}.paging-row label{display:flex;align-items:center;gap:8px;font-size:13px}.select{width:88px}.paging-jump .input{width:72px}.paging-buttons{justify-content:center;font-size:13px}.field-error{margin:10px 0 0}.paging-jump{margin:0}
@media(max-width:600px){.paging-row{justify-content:center}.paging-buttons{width:100%;order:2}.paging-jump{justify-content:center}.paging-buttons .btn{padding:8px 10px}.list-pagination{padding:12px}}
</style>
