// Loaded only by the isolated regression runner, not by the production app.
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/stores/auth'
import { useCartStore } from '../src/stores/cart'
import { cartApi } from '../src/api'

export async function runRegression() {
  const original = { ...cartApi }
  const passed = []
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  const flush = async () => { for (let i = 0; i < 20; i++) await Promise.resolve() }
  const data = quantity => ({ items: quantity ? [{ id: 1, quantity }] : [], totalQuantity: quantity, totalAmount: quantity * 100 })
  let auth, cart, calls
  const setup = () => {
    localStorage.clear()
    setActivePinia(createPinia())
    auth = useAuthStore(); cart = useCartStore()
    auth.accept({ token: 'member-one', member: { id: 1 } }, true)
    calls = []
    for (const method of Object.keys(original)) {
      cartApi[method] = (...args) => new Promise((resolve, reject) => calls.push({ method, args, resolve, reject }))
    }
  }
  const scenario = async (name, test) => { setup(); await test(); passed.push(name) }
  try {
    await scenario('Read before write cannot overwrite the write; reads coalesce', async () => {
      const read = cart.fetch(), sameRead = cart.fetch(), write = cart.updateItem(1, 3)
      await flush(); check(calls.length === 1 && calls[0].method === 'get', 'Write overtook read')
      calls[0].resolve(data(1)); await read; await sameRead; await flush()
      check(calls.length === 2 && calls[1].method === 'updateItem', 'Duplicate read or missing write')
      calls[1].resolve(data(3)); await write
      check(cart.count === 3 && !cart.loading, 'Wrong final state or stuck loading')
    })
    await scenario('Read after write does not reuse a pre-write pending read', async () => {
      const first = cart.fetch(), write = cart.addItem(1), last = cart.fetch()
      await flush(); calls[0].resolve(data(1)); await first; await flush()
      check(calls[1].method === 'addItem', 'Missing add')
      calls[1].resolve(data(2)); await write; await flush()
      check(calls.length === 3 && calls[2].method === 'get', 'Reused stale fetch')
      calls[2].resolve(data(2)); await last
      check(cart.count === 2 && !cart.loading, 'Read after write failed')
    })
    await scenario('Add, update, remove and clear execute in invocation order', async () => {
      const jobs = [cart.addItem(1), cart.updateItem(1, 4), cart.removeItem(1), cart.clear()]
      for (const [index, method] of ['addItem', 'updateItem', 'removeItem', 'clear'].entries()) {
        await flush(); check(calls.length === index + 1 && calls[index].method === method, 'Mutation order wrong')
        calls[index].resolve(data(index + 1)); await jobs[index]
      }
      check(cart.isEmpty && cart.count === 0 && cart.totalAmount === 0, 'Clear did not win')
    })
    await scenario('Failed mutation rejects but does not block the next mutation', async () => {
      const failed = cart.updateItem(1, 99).then(() => false, () => true), next = cart.addItem(1)
      await flush(); calls[0].reject(new Error('庫存不足')); check(await failed, 'Failure was swallowed')
      await flush(); calls[1].resolve(data(2)); await next; check(cart.count === 2, 'Queue poisoned')
    })
    await scenario('Logout rejects an active result and never sends queued writes', async () => {
      const active = cart.addItem(1).then(() => false, () => true)
      const queued = cart.updateItem(1, 5).then(() => false, () => true)
      await flush(); auth.clearSession(); cart.reset(); calls[0].resolve(data(5))
      check(await active && await queued, 'Stale mutation succeeded')
      check(calls.length === 1 && cart.isEmpty && !cart.loading, 'Private state restored or old write sent')
    })
    await scenario('Switching member invalidates old requests without an explicit reset', async () => {
      const active = cart.removeItem(1).then(() => false, () => true)
      const queued = cart.clear().then(() => false, () => true)
      await flush(); auth.accept({ token: 'member-two', member: { id: 2 } }, true)
      calls[0].resolve(data(8)); check(await active && await queued, 'Old member result accepted')
      check(calls.length === 1 && cart.isEmpty, 'Old queued write reached new member')
    })
    await scenario('Reset allows a new read without waiting for an old request', async () => {
      const old = cart.fetch().then(() => false, () => true)
      await flush(); cart.reset(); const fresh = cart.fetch(); await flush()
      check(calls.length === 2 && cart.loading, 'New queue blocked by reset')
      calls[0].resolve(data(9)); check(await old, 'Reset result accepted')
      check(cart.loading && cart.isEmpty, 'Old finally cleared fresh loading or restored state')
      calls[1].resolve(data(2)); await fresh; check(cart.count === 2 && !cart.loading, 'Fresh read failed')
    })
    await scenario('Logged-out actions send no API request', async () => {
      auth.clearSession()
      const rejected = await cart.addItem(1).then(() => false, () => true)
      await cart.fetch(); check(rejected && calls.length === 0 && cart.isEmpty, 'Logged-out write sent')
    })
    return passed
  } finally { Object.assign(cartApi, original) }
}
