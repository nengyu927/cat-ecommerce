import { defineStore } from 'pinia'
import { wishlistApi } from '@/api'
import { useAuthStore } from './auth'
const queues = new WeakMap()
const pendingLoads = new WeakMap()

function enqueue(store, request) {
  const auth = useAuthStore()
  auth.syncFromStorage()
  const epoch = auth.epoch
  let queue = queues.get(store)
  if (!queue) { queue = { tail: Promise.resolve() }; queues.set(store, queue) }
  const current = () => {
    auth.syncFromStorage()
    if (!auth.isLoggedIn || auth.epoch !== epoch || queues.get(store) !== queue) throw new Error('登入狀態已變更，請重新操作收藏')
  }
  const result = queue.tail.then(async () => {
    current()
    const ids = await request()
    current()
    store.ids = ids
    store.loadedEpoch = epoch
  })
  queue.tail = result.catch(() => {})
  return result
}

export const useWishlistStore = defineStore('wishlist', {
  state: () => ({ ids: [], loadedEpoch: null, pendingIds: [], loading: false }),
  getters: { contains: state => id => state.ids.includes(id) },
  actions: {
    async fetch() {
      const auth = useAuthStore(); auth.syncFromStorage()
      if (!auth.isLoggedIn) { this.reset(); return }
      const pending = pendingLoads.get(this)
      if (pending?.epoch === auth.epoch) return pending.promise
      this.loading = true
      const promise = enqueue(this, () => wishlistApi.ids()).finally(() => {
        if (pendingLoads.get(this)?.promise === promise) { pendingLoads.delete(this); this.loading = false }
      })
      pendingLoads.set(this, { epoch: auth.epoch, promise })
      return promise
    },
    async toggle(productId) {
      if (this.pendingIds.includes(productId)) return
      const auth = useAuthStore(); auth.syncFromStorage()
      const epoch = auth.epoch
      const pendingIds = this.pendingIds
      pendingIds.push(productId)
      try {
        if (this.loadedEpoch !== epoch) await this.fetch()
        if (auth.epoch !== epoch || this.pendingIds !== pendingIds) throw new Error('登入狀態已變更，請重新操作收藏')
        pendingLoads.delete(this); this.loading = false
        await enqueue(this, () => this.contains(productId) ? wishlistApi.remove(productId) : wishlistApi.add(productId))
      } finally {
        if (this.pendingIds === pendingIds) {
          const index = pendingIds.indexOf(productId)
          if (index >= 0) pendingIds.splice(index, 1)
        }
      }
    },
    reset() {
      queues.delete(this); pendingLoads.delete(this)
      this.ids = []; this.loadedEpoch = null; this.pendingIds = []; this.loading = false
    }
  }
})
