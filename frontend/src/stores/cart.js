import { defineStore } from 'pinia'
import { cartApi } from '@/api'
import { useAuthStore } from './auth'
const pendingFetches = new WeakMap()
const queues = new WeakMap()

function invalidateFetch(store) {
  pendingFetches.delete(store)
  store.loading = false
}

function enqueue(store, request, apply = data => store.apply(data)) {
  const auth = useAuthStore()
  auth.syncFromStorage()
  const epoch = auth.epoch
  let queue = queues.get(store)
  if (!queue) {
    queue = { tail: Promise.resolve() }
    queues.set(store, queue)
  }
  const current = () => {
    auth.syncFromStorage()
    if (!auth.isLoggedIn || auth.epoch !== epoch || queues.get(store) !== queue) {
      throw new Error('購物車狀態已變更，請重新操作')
    }
  }
  const promise = queue.tail.then(async () => {
    current() // Do not send queued writes under a different member's credentials.
    const data = await request()
    current() // Reset/logout must also invalidate responses already in flight.
    apply(data)
    return data
  })
  queue.tail = promise.catch(() => {}) // A failed request must not poison the queue.
  return promise
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    totalQuantity: 0,
    totalAmount: 0,
    loading: false,
    previewOpen: false
  }),
  getters: {
    count: (state) => state.totalQuantity,
    isEmpty: (state) => state.items.length === 0
  },
  actions: {
    openPreview() { this.previewOpen = true },
    closePreview() { this.previewOpen = false },
    apply(data) {
      this.items = data?.items || []
      this.totalQuantity = data?.totalQuantity || 0
      this.totalAmount = data?.totalAmount || 0
    },
    async fetch() {
      const auth = useAuthStore()
      auth.syncFromStorage()
      if (!auth.isLoggedIn) {
        this.reset()
        return
      }
      const epoch = auth.epoch
      const pending = pendingFetches.get(this)
      if (pending?.epoch === epoch) return pending.promise
      this.loading = true
      const promise = (async () => {
        try {
          await enqueue(this, () => cartApi.get())
        } finally {
          if (pendingFetches.get(this)?.promise === promise) {
            pendingFetches.delete(this)
            this.loading = false
          }
        }
      })()
      pendingFetches.set(this, { epoch, promise })
      return promise
    },
    async addItem(skuId, quantity = 1) {
      invalidateFetch(this)
      return enqueue(this, () => cartApi.addItem({ skuId, quantity }))
    },
    async updateItem(itemId, quantity) {
      invalidateFetch(this)
      return enqueue(this, () => cartApi.updateItem(itemId, { quantity }))
    },
    async removeItem(itemId) {
      invalidateFetch(this)
      return enqueue(this, () => cartApi.removeItem(itemId))
    },
    async clear() {
      invalidateFetch(this)
      return enqueue(this, () => cartApi.clear(), () => this.apply(null))
    },
    reset() {
      this.closePreview()
      queues.delete(this)
      pendingFetches.delete(this)
      this.loading = false
      this.apply(null)
    }
  }
})
