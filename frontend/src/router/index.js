import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { isAdminApp, isStorefrontApp } from '@/config/surface'
import { storefrontRoutes } from './storefront'
import { createAdminRoutes } from './admin'

const passwordRoutes = [
  { path: '/forgot-password', name: 'forgot-password', component: () => import('@/views/PasswordResetView.vue') },
  { path: '/reset-password', name: 'reset-password', component: () => import('@/views/PasswordResetView.vue') }
]
const router = createRouter({
  history: createWebHistory(),
  routes: [
    ...passwordRoutes,
    ...(!isAdminApp ? storefrontRoutes : []),
    ...(!isStorefrontApp ? createAdminRoutes(isAdminApp) : []),
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') }
  ],
  scrollBehavior: () => ({ top: 0 })
})
router.beforeEach(to => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn)
    return { name: to.meta.requiresAdmin ? 'admin-login' : 'login', query: { redirect: to.fullPath } }
  if (to.meta.requiresAdmin && auth.member?.role !== 'ADMIN')
    return { name: 'admin-login', query: { redirect: to.fullPath } }
  return true
})
export default router
