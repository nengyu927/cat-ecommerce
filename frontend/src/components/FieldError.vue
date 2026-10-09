<script setup>
defineProps({ message: String, id: String })
</script>
<template>
  <p v-if="message" :id="id" class="field-error" role="alert">{{ message }}</p>
</template>
