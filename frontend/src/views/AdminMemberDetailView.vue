<script setup>
import ListPagination from '@/components/ListPagination.vue'
import ConfirmationDialog from '@/components/ConfirmationDialog.vue'
import {useAdminDraftGuard} from '@/composables/useAdminDraftGuard'
import {adminReturnQuery} from '@/utils/adminListQuery'
import {queryInteger,emptyPageReplacement} from '@/utils/listQuery'
import { computed,ref,watch,onBeforeUnmount,nextTick } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { adminApi } from '@/api/admin'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const member=ref(null),history=ref(null),loading=ref(false),error=ref(''),notice=ref('')
const dialog=ref(null),target=ref(null),reason=ref(''),actionError=ref(''),saving=ref(false)
let sequence=0,actionSequence=0
const dirty=computed(()=>!!target.value&&!!reason.value)
const isHistory=computed(()=>route.meta.memberHistory===true)
const listQuery=computed(()=>adminReturnQuery('members',route.query))
const listTarget=computed(()=>({name:'admin-members',query:listQuery.value}))
function validId(){const id=String(route.params.id||'');if(!/^[1-9][0-9]{0,18}$/.test(id)||BigInt(id)>9223372036854775807n)throw new Error('會員編號須為有效的正整數');return id}
function closeAction(){++actionSequence;dialog.value?.close();target.value=null;reason.value='';actionError.value=''}
async function load(){
 const current=++sequence;member.value=null;history.value=null;loading.value=true;error.value=''
 try{
  const id=validId(),page=isHistory.value?queryInteger(route.query.historyPage,0,0,2147483647,'紀錄頁碼'):0
  const size=isHistory.value?queryInteger(route.query.historySize,10,1,100,'紀錄每頁筆數'):10
  const data=await adminApi.member(id);if(current!==sequence)return
  if(isHistory.value){
   const entries=await adminApi.memberHistory(id,{page,size});if(current!==sequence)return
   const replacement=emptyPageReplacement(page,entries)
   if(replacement!==null){await router.replace({name:route.name,params:route.params,query:{...route.query,historyPage:replacement},hash:route.hash});return}
   history.value=entries
  }
  member.value=data
 }catch(e){if(current===sequence)error.value=e.message}
 finally{if(current===sequence)loading.value=false}
}
watch(()=>route.fullPath,()=>{closeAction();notice.value='';load()},{immediate:true})
const {confirmation,blocked,retry}=useAdminDraftGuard({dirty,saving,loading,version:()=>sequence,label:'操作原因',reload:()=>{closeAction();notice.value='';load()}})
watch(()=>auth.epoch,()=>closeAction(),{flush:'sync'})
function currentAction(readSequence,current,epoch){auth.syncFromStorage();return readSequence===sequence&&current===actionSequence&&epoch===auth.epoch&&auth.isLoggedIn&&auth.member?.role==='ADMIN'}
async function cancelAction(){
 if(blocked.value||!target.value)return
 const readSequence=sequence,current=actionSequence,epoch=auth.epoch
 if(dirty.value&&!await confirmation.ask({title:'放棄操作原因？',description:'尚未提交的操作原因將被清除，會員狀態不會變更。',cancelLabel:'繼續編輯',confirmLabel:'放棄原因並關閉'}))return
 if(currentAction(readSequence,current,epoch)&&!saving.value)closeAction()
}
function beforeUnload(e){if(saving.value||(target.value&&reason.value)){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload)
onBeforeUnmount(()=>{++sequence;closeAction();window.removeEventListener('beforeunload',beforeUnload)})
async function openAction(){
 auth.syncFromStorage()
 if(blocked.value||loading.value||target.value||isHistory.value||!auth.isLoggedIn||auth.member?.role!=='ADMIN'||!member.value||member.value.role==='ADMIN')return
 closeAction();target.value={...member.value};const readSequence=sequence,current=actionSequence,epoch=auth.epoch
 await nextTick();if(!currentAction(readSequence,current,epoch)||!target.value)return
 dialog.value?.showModal();dialog.value?.querySelector('[data-cancel-action]')?.focus()
}
async function save(){
 auth.syncFromStorage()
 if(blocked.value||loading.value||isHistory.value||!auth.isLoggedIn||auth.member?.role!=='ADMIN'||!target.value||target.value.role==='ADMIN'||member.value?.id!==target.value.id)return
 actionError.value=''
 if(!reason.value.trim()||reason.value.trim().length>200){actionError.value='請填寫 1 至 200 字的操作原因';return}
 const item={...target.value},current=++actionSequence,readSequence=sequence,epoch=auth.epoch
 saving.value=true
 try{
  const updated=await adminApi.memberStatus(item.id,{status:item.status==='ACTIVE'?'DISABLED':'ACTIVE',version:item.statusVersion,reason:reason.value.trim()})
  if(!currentAction(readSequence,current,epoch))return
  member.value=updated;closeAction();notice.value='會員狀態已更新；恢復帳號後仍須重新登入。'
 }catch(e){if(currentAction(readSequence,current,epoch))actionError.value=e.message+(e.status===409?'（取消後重新載入會員再操作）':'')}
 finally{saving.value=false}
}
</script>
<template>
 <div class="page"><div class="container">
  <RouterLink class="btn btn-ghost" :to="listTarget">返回會員列表</RouterLink>
  <h1 class="page-title mt-2">{{ isHistory?'會員操作紀錄':'會員詳情' }} #{{ route.params.id }}</h1>
  <div class="row mt-2">
   <RouterLink class="btn btn-ghost" :to="{name:'admin-member-detail',params:{id:route.params.id},query:listQuery}">會員詳情</RouterLink>
   <RouterLink class="btn btn-ghost" :to="{name:'admin-member-history',params:{id:route.params.id},query:listQuery}">操作紀錄</RouterLink>
   <button class="btn btn-ghost" :disabled="blocked||loading" @click="retry">重新載入會員</button>
  </div>
  <p v-if="loading" role="status">載入會員中…</p>
  <p v-if="error" class="alert alert-error" role="alert"><span>{{ error }}</span> <button v-if="isHistory" class="btn btn-ghost btn-sm" @click="router.replace({name:route.name,params:route.params,query:listQuery})">清除紀錄查詢</button></p>
  <p v-if="notice" class="alert alert-ok" role="status">{{ notice }}</p>
  <template v-if="member&&!loading&&!error">
   <section class="card member-profile mt-3" aria-label="會員基本資料">
    <h2>{{ member.name }}</h2>
    <dl><dt>電子郵件</dt><dd>{{ member.email }}</dd><dt>電話</dt><dd>{{ member.phone||'未提供電話' }}</dd>
     <dt>角色</dt><dd>{{ member.role==='ADMIN'?'管理員':'一般會員' }}</dd><dt>狀態</dt><dd>{{ member.status==='ACTIVE'?'啟用':'停用' }} · v{{ member.statusVersion }}</dd>
     <dt>註冊時間</dt><dd>{{ member.createdAt?.replace('T',' ') }}</dd><dt>更新時間</dt><dd>{{ member.updatedAt?.replace('T',' ')||'未提供' }}</dd></dl>
    <template v-if="!isHistory"><p v-if="member.role==='ADMIN'" class="muted">管理員帳號受保護</p>
     <button v-else class="btn btn-ghost" :aria-label="`${member.status==='ACTIVE'?'停用':'恢復'}會員 #${member.id}`" :disabled="blocked" @click="openAction">{{ member.status==='ACTIVE'?'停用':'恢復' }}會員</button>
     <p class="muted">不提供密碼、角色修改或帳號刪除；狀態變更會留下原因、操作者與版本。</p></template>
   </section>
   <section v-if="isHistory" class="card member-profile mt-3" aria-label="會員狀態歷程">
   <template v-if="history">
    <p v-if="!history.content.length" class="muted">尚無狀態變更紀錄；不補建歷史。</p>
    <div v-else style="overflow-x:auto"><table class="table"><thead><tr><th>時間</th><th>狀態</th><th>操作原因</th><th>操作者／版本</th></tr></thead><tbody><tr v-for="entry in history.content" :key="entry.id"><td>{{ entry.createdAt?.replace('T',' ') }}</td><td>{{ entry.fromStatus }} → {{ entry.toStatus }}</td><td>{{ entry.reason }}</td><td>管理員 #{{ entry.actorMemberId }} · v{{ entry.statusVersion }}</td></tr></tbody></table></div>
  <ListPagination :page="history.page" :total-pages="history.totalPages" :loading="loading" :default-size="10" page-key="historyPage" size-key="historySize" label-prefix="紀錄" />
   </template>

   </section>
  </template>
  <dialog ref="dialog" class="card member-dialog" aria-labelledby="member-status-title" aria-describedby="member-status-impact" @cancel.prevent="cancelAction">
   <form v-if="target" @submit.prevent="save"><p class="member-dialog-eyebrow">MEOW DAILY / 會員狀態確認</p><h2 id="member-status-title">{{ target.status==='ACTIVE'?'停用':'恢復' }}會員</h2>
    <p class="member-dialog-summary">#{{ target.id }} · {{ target.name }}<br />{{ target.email }}<br />狀態版本 v{{ target.statusVersion }}</p><p id="member-status-impact" class="muted">{{ target.status==='ACTIVE'?'停用會撤銷所有登入及重設連結，但保留訂單、購物車與收藏。':'恢復不會還原先前登入，會員必須重新登入。' }}</p>
    <div v-if="actionError" class="alert alert-error" role="alert">{{ actionError }}</div>
    <div class="field"><label for="member-status-reason">操作原因</label><textarea id="member-status-reason" v-model="reason" class="input" required maxlength="200" rows="3" :disabled="blocked"></textarea></div>
    <div class="row member-dialog-actions"><button type="button" data-cancel-action class="btn btn-ghost" :disabled="blocked" @click="cancelAction">取消</button><button class="btn" :class="target.status==='ACTIVE'?'btn-danger':'btn-primary'" :disabled="blocked">{{ saving?'處理中…':'確認變更' }}</button></div>
   </form>
  </dialog>

 </div></div>
 <ConfirmationDialog :request="confirmation.request.value" @answer="confirmation.answer" />
</template>
<style scoped>
:global(body:has(.member-dialog[open])){overflow:hidden}
.member-profile{padding:24px;overflow-wrap:anywhere}
dl{display:grid;grid-template-columns:100px minmax(0,1fr);gap:12px}dd{margin:0}dt{color:var(--muted)}
.row{flex-wrap:wrap}
.member-dialog{border:1px solid var(--line);width:min(480px,calc(100vw - 32px));max-height:calc(100dvh - 40px);overflow:auto;padding:28px;overflow-wrap:anywhere;border-radius:24px;background:#fffdf8;box-shadow:0 24px 80px #203d3533}
.member-dialog::backdrop{background:#173c3266}.member-dialog-eyebrow{font-size:10px;letter-spacing:1.5px;color:var(--ink-soft)}
.member-dialog-summary{padding:14px;border:1px solid var(--line);border-radius:12px;background:var(--brand-soft);font-size:13px;line-height:1.8}
.member-dialog-actions{display:flex;gap:12px}.member-dialog-actions .btn{flex:1}
</style>
