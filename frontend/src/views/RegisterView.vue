<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import FieldError from '@/components/FieldError.vue'
import { useFormErrors } from '@/composables/useFormErrors'

const auth = useAuthStore()
const router = useRouter()

const form = reactive({ email: '', password: '', name: '', phone: '' })
const { error, fields, clear, capture } = useFormErrors()
const loading = ref(false)

async function submit() {
  if (loading.value) return
  clear()
  loading.value = true
  try {
    await auth.register({ ...form })
    router.push('/')
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
      <h1 class="page-title">會員註冊</h1>
      <p class="page-sub">建立帳號，開始為主子選購</p>

      <div class="card" style="padding: 26px;">
        <div v-if="error" class="alert alert-error">{{ error }}</div>
        <form @submit.prevent="submit">
          <div class="field">
            <label for="register-email">電子郵件 *</label>
            <input id="register-email" v-model="form.email" type="email" class="input" required :aria-invalid="!!fields.email" :aria-describedby="fields.email ? 'register-email-error' : undefined" />
            <FieldError id="register-email-error" :message="fields.email" />
          </div>
          <div class="field">
            <label for="register-password">密碼 *（至少 8 字元）</label>
            <input id="register-password" v-model="form.password" type="password" class="input" minlength="8" maxlength="64" required :aria-invalid="!!fields.password" :aria-describedby="fields.password ? 'register-password-error' : undefined" />
            <FieldError id="register-password-error" :message="fields.password" />
          </div>
          <div class="field">
            <label for="register-name">姓名 *</label>
            <input id="register-name" v-model="form.name" class="input" required maxlength="100" :aria-invalid="!!fields.name" :aria-describedby="fields.name ? 'register-name-error' : undefined" />
            <FieldError id="register-name-error" :message="fields.name" />
          </div>
          <div class="field">
            <label for="register-phone">電話</label>
            <input id="register-phone" v-model="form.phone" class="input" maxlength="30" :aria-invalid="!!fields.phone" :aria-describedby="fields.phone ? 'register-phone-error' : undefined" />
            <FieldError id="register-phone-error" :message="fields.phone" />
          </div>
          <button class="btn btn-primary btn-block" :disabled="loading">{{ loading ? '註冊中…' : '註冊' }}</button>
        </form>
        <p class="muted mt-2" style="text-align: center; font-size: 13px;">
          已有帳號？<RouterLink to="/login" style="color: var(--brand); font-weight: 600;">前往登入</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>
