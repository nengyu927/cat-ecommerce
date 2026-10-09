<script setup>
import { ref, watch } from 'vue'
import fallback from '@/assets/cat-daily.svg'
const props = defineProps({ src: String, alt: { type: String, default: '商品示意圖' } })
const failed = ref(false)
watch(() => props.src, () => { failed.value = false })
</script>
<template>
  <img :src="!failed && src ? src : fallback" :alt="alt" @error="failed = true" />
</template>
