<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
defineProps({ name: String, skus: Array })
const emit=defineEmits(['choose'])
const dialog=ref(null),selected=ref(''),select=ref(null)
let overflow
onMounted(()=>{overflow=document.body.style.overflow;document.body.style.overflow='hidden';dialog.value.showModal();select.value.focus()})
onBeforeUnmount(()=>{dialog.value?.close();document.body.style.overflow=overflow})
</script>
<template>
 <dialog ref="dialog" class="sku-dialog" aria-labelledby="quick-sku-title" @cancel.prevent="emit('choose',null)">
  <h2 id="quick-sku-title">選擇商品規格</h2><p>{{ name }}</p>
  <label for="quick-sku-select">規格與價格</label>
  <select id="quick-sku-select" ref="select" v-model="selected" class="select">
   <option disabled value="">請選擇規格</option>
   <option v-for="sku in skus" :key="sku.id" :value="sku.id" :disabled="sku.stock<=0">{{ sku.specName }} · NT$ {{ Number(sku.price).toLocaleString() }}{{ sku.stock<=0?'（缺貨）':'' }}</option>
  </select>
  <div class="row mt-3"><button class="btn btn-ghost" @click="emit('choose',null)">取消</button><button class="btn btn-primary" :disabled="!selected" @click="emit('choose',Number(selected))">確認加入購物車</button></div>
 </dialog>
</template>
<style scoped>
.sku-dialog{border:1px solid var(--line);border-radius:20px;padding:28px;width:min(420px,calc(100vw - 32px));max-height:calc(100dvh - 40px);overflow:auto;background:#fffdf8;color:var(--ink);box-shadow:var(--shadow-lg)}.sku-dialog::backdrop{background:#173c3266}.sku-dialog h2{font-size:22px;margin:0}.sku-dialog label{display:block;font-size:13px;margin-bottom:8px}.sku-dialog .row{flex-wrap:wrap;justify-content:flex-end}
</style>
