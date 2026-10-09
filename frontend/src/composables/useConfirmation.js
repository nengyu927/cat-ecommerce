import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export function useConfirmation() {
  const request = ref(null), route = useRoute(), auth = useAuthStore()
  let resolve
  function answer(accepted = false) {
    const pending = resolve
    resolve = undefined
    request.value = null
    pending?.(accepted === true)
  }
  function ask(options) {
    if (resolve) return Promise.resolve(false)
    request.value = options
    return new Promise(done => { resolve = done })
  }
  // Confirmation is never carried to another page or another member's session.
  watch([() => route.fullPath, () => auth.epoch], () => answer(false), { flush: 'sync' })
  onBeforeUnmount(() => answer(false))
  return { request, pending: computed(() => request.value !== null), ask, answer }
}
