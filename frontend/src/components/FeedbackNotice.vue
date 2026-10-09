<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ message: { type: String, default: '' } })
const dismissed = ref(false)
watch(() => props.message, () => { dismissed.value = false })
</script>
<template>
  <Transition name="feedback-notice">
    <div v-if="message && !dismissed" class="alert alert-ok feedback-notice" role="status">
      <span class="feedback-notice-text">{{ message }}</span>
      <button type="button" class="feedback-notice-close" aria-label="關閉成功提示" @click="dismissed = true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg>
      </button>
    </div>
  </Transition>
</template>
