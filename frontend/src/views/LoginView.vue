<script setup>
import FeedbackNotice from '@/components/FeedbackNotice.vue'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import FieldError from '@/components/FieldError.vue'
import { useFormErrors } from '@/composables/useFormErrors'
import { demoLoginEnabled, demoAccounts } from '@/config/demo'
import { isAdminApp, isStorefrontApp, adminPath } from '@/config/surface'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const form = reactive({ email: '', password: '' })
const { error, fields, clear, capture } = useFormErrors()
const notice = computed(() => route.query.reason === 'password-changed' ? '密碼已變更，所有裝置已登出，請重新登入' : '')
const loading = ref(false)
const isAdminLogin = computed(() => route.name === 'admin-login')

async function submit(demoKind = null) {
  if (loading.value) return
  if (demoKind && !demoLoginEnabled) return
  clear()
  loading.value = true
  try {
    const adminRequired = isAdminLogin.value
    const data = await auth.login(demoKind ? { ...demoAccounts[demoKind] } : { ...form })
    if (adminRequired && data.member.role !== 'ADMIN') {
      await auth.logout()
      throw new Error('此帳號沒有管理員權限，請使用管理員帳號登入')
    }
    const redirect = route.query.redirect
    const safeRedirect = typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
      && !redirect.startsWith('/login') && !redirect.startsWith('/admin/login')
      && (!redirect.startsWith('/admin/') || data.member.role === 'ADMIN')
    const destination = safeRedirect ? redirect : adminRequired ? adminPath('products') : '/'
    await router.replace(destination)
  } catch (e) {
    capture(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="container" style="max-width: 420px;">
      <h1 class="page-title">{{ isAdminLogin ? '後台登入' : '會員登入' }}</h1>
      <p class="page-sub">{{ isAdminLogin ? '管理員登入後可管理商品、分類與訂單' : '登入後即可使用購物車與結帳' }}</p>

      <div class="card" style="padding: 26px;">
        <div v-if="error" class="alert alert-error">{{ error }}</div>
<FeedbackNotice :message="notice" />
        <form @submit.prevent="submit()">
          <div class="field">
            <label for="login-email">電子郵件</label>
            <input id="login-email" v-model="form.email" type="email" class="input" placeholder="customer@catshop.local" required :aria-invalid="!!fields.email" :aria-describedby="fields.email ? 'login-email-error' : undefined" />
            <FieldError id="login-email-error" :message="fields.email" />
          </div>
          <div class="field">
            <label for="login-password">密碼</label>
            <input id="login-password" v-model="form.password" type="password" class="input" placeholder="password123" required :aria-invalid="!!fields.password" :aria-describedby="fields.password ? 'login-password-error' : undefined" />
            <FieldError id="login-password-error" :message="fields.password" />
          </div>
          <button class="btn btn-primary btn-block" :disabled="loading">{{ loading ? '登入中…' : '登入' }}</button>
        </form>
        <p class="mt-2" style="text-align:right"><RouterLink :to="{name:'forgot-password'}">忘記密碼？</RouterLink></p>
        <div v-if="demoLoginEnabled" class="mt-2">
          <button type="button" class="btn btn-ghost btn-block" :disabled="loading" @click="submit(isAdminLogin ? 'admin' : 'customer')">
            {{ loading ? '登入中…' : isAdminLogin ? '一鍵登入管理員（展示用）' : '一鍵登入會員（展示用）' }}
          </button>
          <p class="muted" style="font-size: 12px;">僅供展示，使用共用測試帳號。資料異動會被其他展示者看到，請勿輸入真實個資。</p>
        </div>
        <p v-if="!isAdminLogin" class="muted mt-2" style="text-align: center; font-size: 13px;">
          還沒有帳號？<RouterLink to="/register" style="color: var(--brand); font-weight: 600;">立即註冊</RouterLink>
        </p>
        <p v-if="!isAdminApp && !isStorefrontApp" class="mt-2" style="text-align: center;"><RouterLink :to="isAdminLogin ? '/login' : '/admin/login'">{{ isAdminLogin ? '返回會員登入' : '前往後台登入' }}</RouterLink></p>
      </div>
    </div>
  </div>
</template>
