import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import AdminApp from './AdminApp.vue'
import { isAdminApp } from './config/surface'
import router from './router'
import './assets/main.css'
import './assets/refinement.css'

const app = createApp(isAdminApp ? AdminApp : App)
document.title = isAdminApp ? '喵喵商城 Admin' : '喵喵商城'
app.use(createPinia())
app.use(router)
app.mount('#app')
