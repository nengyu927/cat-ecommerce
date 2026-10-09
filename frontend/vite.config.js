import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const admin = env.VITE_APP_SURFACE === 'admin'
  const storefront = env.VITE_APP_SURFACE === 'storefront'
  const target = env.VITE_API_TARGET || 'http://127.0.0.1:8080'
  const proxy = {
    '/api': { target, changeOrigin: true },
    ...(admin ? { '/admin-api': {
      target, changeOrigin: true,
      rewrite: path => path.replace(/^\/admin-api/, '/api'),
      // Cookies are host/path scoped, not port scoped. Isolate admin refresh from storefront.
      cookiePathRewrite: { '/api/auth': '/admin-api/auth' }
    } } : {})
  }
  return {
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: { port: admin ? 5174 : 5173, strictPort: true, proxy },
    preview: { port: admin ? 5174 : 5173, strictPort: true, proxy },
    build: { outDir: admin ? 'dist/admin' : storefront ? 'dist/storefront' : 'dist' }
  }
})
