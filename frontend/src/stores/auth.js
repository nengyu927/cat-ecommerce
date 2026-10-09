import { defineStore } from 'pinia'
import { authApi, memberApi } from '@/api'
import axios from 'axios'
import { apiBase } from '@/config/surface'
import { clearCheckoutAttempt } from '@/utils/checkoutAttempt'

const TOKEN_KEY = 'catshop_token'
const MEMBER_KEY = 'catshop_member'
const SESSION_KEY = 'catshop_session'
function parse(value) { try { return JSON.parse(value || 'null') } catch { return null } }
function snapshot() {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const session = parse(localStorage.getItem(SESSION_KEY))
  if (typeof session?.token === 'string' && typeof session.epoch === 'string') {
    return { token: session.token, member: session.member || null, epoch: session.epoch, reason: session.reason || '' }
  }
  return { token, member: parse(localStorage.getItem(MEMBER_KEY)),
    epoch: session?.token === token && typeof session.epoch === 'string' ? session.epoch : token,
    reason: session?.token === token ? session.reason || '' : '' }
}

export const useAuthStore = defineStore('auth', {
  state: snapshot,
  getters: {
    isLoggedIn: (state) => !!state.token,
    displayName: (state) => state.member?.name || '訪客'
  },
  actions: {
    persist() {
      if (this.token) localStorage.setItem(TOKEN_KEY, this.token)
      else localStorage.removeItem(TOKEN_KEY)
      if (this.member) localStorage.setItem(MEMBER_KEY, JSON.stringify(this.member))
      else localStorage.removeItem(MEMBER_KEY)
      // Write last so other tabs observe a complete member/token pair.
      localStorage.setItem(SESSION_KEY, JSON.stringify({ token: this.token, member: this.member, epoch: this.epoch, reason: this.reason }))
    },
    syncFromStorage() {
      const latest = snapshot()
      if (latest.epoch !== this.epoch) { try { clearCheckoutAttempt() } catch { /* Read guard blocks a mismatched record. */ } }
      Object.assign(this, latest)
    },
    startSync() {
      const listener = event => {
        if (event.storageArea === localStorage && (event.key === SESSION_KEY || event.key === null)) this.syncFromStorage()
      }
      window.addEventListener('storage', listener)
      return () => window.removeEventListener('storage', listener)
    },
    accept(data, newLogin = false) {
      if (newLogin) { try { clearCheckoutAttempt() } catch { /* Owner/epoch must still match before reuse. */ } }
      this.token = data.token; this.member = data.member
      if (newLogin) this.epoch = crypto.randomUUID()
      this.reason = ''
      this.persist()
    },
    async login(payload) {
      const data = await authApi.login(payload)
      this.accept(data, true)
      return data
    },
    async register(payload) {
      const data = await authApi.register(payload)
      this.accept(data, true)
      return data
    },
    async refreshProfile() {
      if (!this.token) return
      const epoch = this.epoch
      const member = await memberApi.me()
      this.syncFromStorage()
      if (this.epoch !== epoch || !this.token) return
      this.member = member
      this.persist()
    },
    logout() {
      // Send immediately while the cookie still identifies this session; local logout is unconditional.
      const request = axios.post(`${apiBase}/auth/logout`, {}, { withCredentials: true, timeout: 15000 })
        .catch(() => { /* Offline logout clears this browser; server revocation requires connectivity. */ })
      this.clearSession()
      return request
    },
    clearSession(reason = 'signed-out') {
      try { clearCheckoutAttempt() } catch { /* No old record can be reused by another session. */ }
      this.token = ''
      this.member = null
      this.epoch = crypto.randomUUID()
      this.reason = reason
      this.persist()
    }
  }
})
