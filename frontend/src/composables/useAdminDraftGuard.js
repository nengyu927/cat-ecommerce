import {computed,unref} from 'vue'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {useAuthStore} from '@/stores/auth'
import {useConfirmation} from './useConfirmation'

// Shared presentation guard only. Each form retains its own DTO, validation and version.
export function useAdminDraftGuard({dirty,saving,loading,version,reload,label}){
 const auth=useAuthStore(),confirmation=useConfirmation()
 const blocked=computed(()=>saving.value||confirmation.pending.value)
 function current(sequence,epoch){auth.syncFromStorage();return version()===sequence&&auth.epoch===epoch&&auth.isLoggedIn&&auth.member?.role==='ADMIN'&&!saving.value}
 async function guard(to){
  auth.syncFromStorage()
  if(!auth.isLoggedIn||auth.member?.role!=='ADMIN'||to.name==='admin-login')return true
  if(blocked.value)return false
  if(!dirty.value)return true
  const sequence=version(),epoch=auth.epoch
  const accepted=await confirmation.ask({title:`${unref(label)}尚未儲存`,description:'離開後，尚未儲存的修改將不會保留。',cancelLabel:'繼續編輯',confirmLabel:'放棄修改並離開'})
  return accepted&&current(sequence,epoch)
 }
 async function retry(){
  if(blocked.value||loading.value)return
  const sequence=version(),epoch=auth.epoch
  if(dirty.value&&!await confirmation.ask({title:`重新載入${unref(label)}？`,description:'尚未儲存的修改將被清除，並以最新資料取代。',cancelLabel:'繼續編輯',confirmLabel:'放棄修改並重新載入'}))return
  if(current(sequence,epoch)&&!loading.value)reload()
 }
 onBeforeRouteLeave(guard);onBeforeRouteUpdate(guard)
 return {confirmation,blocked,retry}
}
