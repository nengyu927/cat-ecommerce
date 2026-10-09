import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/stores/auth'
import { useWishlistStore } from '../src/stores/wishlist'
import { wishlistApi } from '../src/api'
export async function runRegression() {
  const original = { ...wishlistApi }, passed = []
  const check = (value, message) => { if (!value) throw new Error(message) }
  const flush = async () => { for (let i = 0; i < 30; i++) await Promise.resolve() }
  let auth, store, calls
  const setup = () => {
    localStorage.clear(); setActivePinia(createPinia()); auth = useAuthStore(); store = useWishlistStore()
    auth.accept({ token: 'one', member: { id: 1 } }, true); calls = []
    for (const method of Object.keys(original)) wishlistApi[method] = (...args) => new Promise((resolve, reject) => calls.push({ method, args, resolve, reject }))
  }
  const scenario = async (name, test) => { setup(); await test(); passed.push(name) }
  try {
    await scenario('Shared initial load, duplicate-click guard and concurrent busy flags', async () => {
      const a = store.toggle(1), b = store.toggle(2), duplicate = store.toggle(1)
      await flush(); check(calls.length === 1 && calls[0].method === 'ids', 'Duplicate initial fetch')
      calls[0].resolve([]); await flush(); check(calls[1].method === 'add', 'Missing add')
      calls[1].resolve([1]); await a; await duplicate; await flush()
      check(store.pendingIds.length === 1 && store.pendingIds[0] === 2, 'Busy flag lost')
      calls[2].resolve([2, 1]); await b; check(store.pendingIds.length === 0 && store.ids.length === 2, 'Busy flag stuck')
    })
    await scenario('Toggle removes an existing saved product', async () => {
      store.ids = [1]; store.loadedEpoch = auth.epoch
      const job = store.toggle(1); await flush(); check(calls[0].method === 'remove', 'Wrong toggle action')
      calls[0].resolve([]); await job; check(!store.contains(1), 'Remove failed')
    })
    await scenario('Logout cannot restore saved ids from a late request', async () => {
      const job = store.fetch().then(() => false, () => true); await flush()
      auth.clearSession(); store.reset(); calls[0].resolve([1]); check(await job, 'Stale response accepted')
      check(store.ids.length === 0 && !store.loading, 'Private data restored')
    })
    await scenario('Member switch rejects queued old-member writes', async () => {
      store.loadedEpoch = auth.epoch
      const a = store.toggle(1).then(() => false, () => true), b = store.toggle(2).then(() => false, () => true)
      await flush(); auth.accept({ token: 'two', member: { id: 2 } }, true); store.reset()
      calls[0].resolve([1]); check(await a && await b, 'Old operation succeeded')
      check(calls.length === 1 && store.ids.length === 0, 'Old write sent under new member')
    })
    await scenario('A failed write does not poison the next write', async () => {
      store.loadedEpoch = auth.epoch
      const a = store.toggle(1).then(() => false, () => true), b = store.toggle(2)
      await flush(); calls[0].reject(new Error('收藏失敗')); check(await a, 'Error swallowed'); await flush()
      calls[1].resolve([2]); await b; check(store.contains(2) && store.pendingIds.length === 0, 'Queue stuck')
    })
    await scenario('A pending read cannot overwrite a later mutation', async () => {
      store.loadedEpoch = auth.epoch
      const read = store.fetch(), write = store.toggle(1), last = store.fetch()
      await flush(); calls[0].resolve([]); await read; await flush()
      check(calls[1].method === 'add', 'Write did not follow pending read')
      calls[1].resolve([1]); await write; await flush()
      check(calls[2].method === 'ids', 'Final read did not follow write')
      calls[2].resolve([1]); await last; check(store.contains(1) && !store.loading, 'Old read won')
    })
    return passed
  } finally { Object.assign(wishlistApi, original) }
}
