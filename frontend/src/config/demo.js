// Public demonstration credentials only. Never configure a real account here.
export const demoLoginEnabled = import.meta.env.VITE_DEMO_LOGIN === 'true'
  || (import.meta.env.DEV && import.meta.env.VITE_DEMO_LOGIN !== 'false')
export const demoAccounts = Object.freeze({
  customer: { email: 'customer@catshop.local', password: 'password123' },
  admin: { email: 'admin@catshop.local', password: 'password123' }
})
