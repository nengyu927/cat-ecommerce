<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
const props = defineProps({ request: Object })
const emit = defineEmits(['answer'])
const dialog = ref(null), cancelButton = ref(null)
let generation = 0, overflow = null
function restore() { if (overflow !== null) { document.body.style.overflow = overflow; overflow = null } }
watch(() => props.request, async request => {
  const current = ++generation
  if (!request) { dialog.value?.close(); restore(); return }
  await nextTick()
  if (current !== generation || props.request !== request) return
  if (!dialog.value.open) {
    overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.value.showModal()
    cancelButton.value.focus()
  }
})
function cancel() { emit('answer', false) }
function closed() { if (dialog.value && !dialog.value.open) cancel() }
onBeforeUnmount(() => { ++generation; dialog.value?.close(); restore() })
</script>
<template>
  <dialog ref="dialog" class="confirmation-dialog" aria-labelledby="confirmation-title" aria-describedby="confirmation-description" @cancel.prevent="cancel" @close="closed">
    <template v-if="request">
      <div class="confirmation-mark" aria-hidden="true">!</div>
      <p class="confirmation-eyebrow">MEOW DAILY / 請確認操作</p>
      <h2 id="confirmation-title">{{ request.title }}</h2>
      <p id="confirmation-description">{{ request.description }}</p>
      <p v-if="request.detail" class="confirmation-detail">{{ request.detail }}</p>
      <div class="confirmation-actions">
        <button ref="cancelButton" type="button" class="btn btn-ghost" autofocus @click="cancel">{{ request.cancelLabel || '保留不變' }}</button>
        <button type="button" class="btn" :class="request.tone==='primary'?'btn-primary':'btn-danger'" @click="emit('answer', true)">{{ request.confirmLabel }}</button>
      </div>
    </template>
  </dialog>
</template>
<style scoped>
.confirmation-dialog{border:1px solid #e5e7dc;border-radius:24px;padding:32px;width:min(440px,calc(100vw - 32px));max-height:calc(100dvh - 40px);overflow:auto;color:var(--ink);background:#fffdf8;box-shadow:0 24px 80px #203d3533}
.confirmation-dialog[open]{animation:confirmation-in .18s ease-out}.confirmation-dialog::backdrop{background:#173c3266}
.confirmation-mark{display:grid;place-items:center;width:48px;height:48px;border-radius:16px;background:#f6e7dc;color:#9e4a2e;font-size:24px;font-weight:800;margin-bottom:20px}
.confirmation-eyebrow{font-size:10px;letter-spacing:1.5px;color:var(--ink-soft)}h2{font-size:24px;line-height:1.4;margin:10px 0}
#confirmation-description{font-size:14px;line-height:1.8;color:var(--ink-soft)}
.confirmation-detail{padding:14px;border:1px solid var(--line);border-radius:12px;background:var(--brand-soft);font-size:13px;white-space:pre-line;overflow-wrap:anywhere;max-height:min(36dvh,320px);overflow:auto;overscroll-behavior:contain}
.confirmation-actions{display:flex;gap:12px;margin-top:24px}.confirmation-actions button{flex:1;min-height:44px}
@keyframes confirmation-in{from{opacity:0;transform:translateY(8px) scale(.98)}to{opacity:1;transform:none}}
@media(prefers-reduced-motion:reduce){.confirmation-dialog[open]{animation:none}}
</style>
