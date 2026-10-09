import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { ApiError } from './errors'
import { apiBase, loginRouteName } from '@/config/surface'

/**
 * Shared axios instance.
 * baseURL is '/api' — in dev the Vite proxy forwards it to http://localhost:8080.
 */
const http = axios.create({
  withCredentials: true,
  baseURL: apiBase,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

const refreshes = new Map()
async function refreshAccess(failedToken) {
  const auth = useAuthStore()
  const epoch = auth.epoch
  const run = async () => {
    auth.syncFromStorage()
    if (auth.epoch !== epoch) throw new Error('登入狀態已變更')
    const latest = auth.token
    if (!latest) throw new Error('請重新登入')
    if (latest !== failedToken) return
    const response = await axios.post(`${apiBase}/auth/refresh`, {}, { withCredentials: true, timeout: 15000 })
    auth.syncFromStorage()
    if (auth.epoch !== epoch || auth.token !== latest) throw new Error('登入狀態已變更')
    const data = response.data.data
    auth.accept(data)
  }
  if (!refreshes.has(epoch)) {
    // Share rotation across tabs where Web Locks is available, and within this tab in all browsers.
    refreshes.set(epoch, (navigator.locks ? navigator.locks.request('catshop-refresh', run) : run())
      .finally(() => { refreshes.delete(epoch) }))
  }
  return refreshes.get(epoch)
}

// Request interceptor: attach the JWT if we have one.
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  auth.syncFromStorage()
  if (config._retried && config._authEpoch !== auth.epoch) throw new Error('登入狀態已變更，請重新操作')
  config._authEpoch = auth.epoch
  config._hadAuth = !!auth.token
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  } else delete config.headers.Authorization
  return config
})

// Response interceptor: unwrap the ApiResponse envelope and normalise errors.
http.interceptors.response.use(
  (response) => {
    const auth = useAuthStore()
    auth.syncFromStorage()
    const publicResource = /^\/(products|categories|images)(\/|$)|^\/health$/.test(response.config.url)
    if (((response.config._hadAuth && !publicResource) || ['/auth/login', '/auth/register'].includes(response.config.url))
        && response.config._authEpoch !== auth.epoch) {
      return Promise.reject(new Error('登入狀態已變更，舊請求結果已忽略'))
    }
    const body = response.data
    // Backend envelope: { success, data, error, timestamp }
    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success) return body.data
      return Promise.reject(new ApiError(body, response.status))
    }
    return body
  },
  async (error) => {
    const status = error.response?.status
    const body = error.response?.data
    const message = body?.error?.message || error.message || '網路錯誤'

    if (status === 401 && !error.config?.url?.startsWith('/auth/')) {
      const auth = useAuthStore()
      auth.syncFromStorage()
      if (error.config?._authEpoch !== auth.epoch) return Promise.reject(new Error('登入狀態已變更，請重新操作'))
      if (auth.token && !error.config._retried) {
        error.config._retried = true
        try {
          await refreshAccess(error.config.headers.Authorization?.replace('Bearer ', ''))
          return http(error.config)
        } catch { /* Refresh rejected: require a new login. */ }
      }
      if (error.config._authEpoch !== auth.epoch) return Promise.reject(new Error('登入狀態已變更'))
      auth.clearSession('expired')
      if (router.currentRoute.value.name !== loginRouteName) {
        router.push({ name: loginRouteName, query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    return Promise.reject(new ApiError(body, status, message))
  }
)

export default http
