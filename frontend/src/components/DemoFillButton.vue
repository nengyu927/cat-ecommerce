<script setup>
defineProps({ disabled: Boolean, label: { type: String, default: '一鍵填入完整資訊' } })
defineEmits(['fill'])
</script>

<template>
  <button type="button" class="btn btn-ghost btn-sm demo-fill" :disabled="disabled" @click="$emit('fill')">{{ label }}</button>
</template>

<style scoped>
.demo-fill { margin: 8px 0 16px; border: 1px dashed var(--brand); color: var(--brand); white-space: normal; }
</style>
