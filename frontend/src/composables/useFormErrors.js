import { ref } from 'vue'

export function useFormErrors() {
  const error = ref('')
  const fields = ref({})
  function clear() { error.value = ''; fields.value = {} }
  function capture(cause) {
    error.value = cause.message || '操作失敗，請稍後重試'
    fields.value = cause.fields || {}
  }
  return { error, fields, clear, capture }
}
