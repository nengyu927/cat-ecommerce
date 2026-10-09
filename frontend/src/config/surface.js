export const appSurface = import.meta.env.VITE_APP_SURFACE || 'combined'
export const isAdminApp = appSurface === 'admin'
export const isStorefrontApp = appSurface === 'storefront'
export const apiBase = isAdminApp ? '/admin-api' : '/api'
export const loginRouteName = isAdminApp ? 'admin-login' : 'login'
export const adminPath = section => isAdminApp ? `/${section}` : `/admin/${section}`
