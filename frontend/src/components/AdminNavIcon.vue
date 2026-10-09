<script setup>
defineProps({ name: String })
const paths = {
  products: ['M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z'],
  categories: ['M3 6h7l2 2h9v12H3z', 'M3 6V4h7l2 2'],
  orders: ['M6 3h12v18H6z', 'M9 7h6M9 11h6M9 15h4'],
  inventory: ['M3 7l9-4 9 4-9 4z', 'M3 7v10l9 4 9-4V7M12 11v10'],
  movements: ['M4 7h16l-4-4M20 17H4l4 4'],
  members: ['M16 21v-2a5 5 0 0 0-10 0v2', 'M19 10a3 3 0 0 1 0 6M20 21v-1a4 4 0 0 0-2-3', 'M11 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8'],
  coupons: ['M3 6h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4z', 'M14 8v2m0 4v2M8 10h2M8 14h2']
}
</script>
<template>
  <svg class="admin-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path v-for="(d,index) in paths[name] || paths.products" :key="index" :d="d" /></svg>
</template>
