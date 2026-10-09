export function createAdminRoutes(standalone) {
  const prefix = standalone ? '' : '/admin'
  const protectedMeta = { requiresAuth: true, requiresAdmin: true }
  const modules = [
    ['coupons', 'admin-coupons', () => import('@/views/AdminCouponView.vue')],
    ['members', 'admin-members', () => import('@/views/AdminMemberView.vue')],
    ['movements', 'admin-movements', () => import('@/views/StockMovementView.vue')],
    ['inventory', 'admin-inventory', () => import('@/views/LowStockView.vue')]
  ]
  const routes = [
    { path: prefix+'/login', name: 'admin-login', component: () => import('@/views/LoginView.vue') },
    {path:prefix+'/members/:id/history',name:'admin-member-history',component:()=>import('@/views/AdminMemberDetailView.vue'),meta:{...protectedMeta,memberHistory:true}},
    {path:prefix+'/members/:id',name:'admin-member-detail',component:()=>import('@/views/AdminMemberDetailView.vue'),meta:protectedMeta},
    {path:prefix+'/coupons/new',name:'admin-coupon-new',component:()=>import('@/views/AdminCouponEditView.vue'),meta:protectedMeta},
    {path:prefix+'/coupons/:id/edit',name:'admin-coupon-edit',component:()=>import('@/views/AdminCouponEditView.vue'),meta:protectedMeta},
    { path:prefix+'/categories/new',name:'admin-category-new',component:()=>import('@/views/AdminCategoryView.vue'),meta:protectedMeta },
    { path:prefix+'/categories/:id/edit',name:'admin-category-edit',component:()=>import('@/views/AdminCategoryView.vue'),meta:protectedMeta },
    ...modules.map(([path,name,component]) => ({ path: prefix+'/'+path, name, component, meta: protectedMeta })),
    { path: prefix+'/products/new', name:'admin-product-new', component:()=>import('@/views/AdminProductView.vue'), meta:protectedMeta },
    ...['skus','images'].map(kind=>({path:prefix+'/products/:id/'+kind,name:'admin-product-'+kind,component:()=>import('@/views/AdminProductAssetView.vue'),meta:{...protectedMeta,productAssets:kind}})),
    { path: prefix+'/products/:id/edit', name:'admin-product-edit', component:()=>import('@/views/AdminProductView.vue'), meta:protectedMeta },
    { path: prefix+'/orders/:id/shipment', name: 'admin-order-shipment', component: () => import('@/views/AdminOrderView.vue'), meta: { ...protectedMeta, shipmentForm: true } },
    { path: prefix+'/orders/:id', name: 'admin-order-detail', component: () => import('@/views/AdminOrderView.vue'), meta: protectedMeta },
    { path: prefix+'/:section(products|categories|orders)', name: 'admin', component: () => import('@/views/AdminView.vue'), meta: protectedMeta }
  ]
  if (standalone) routes.push(
    { path: '/', redirect: '/products' },
    {path:'/admin/members/:id/history',redirect:to=>({name:'admin-member-history',params:to.params,query:to.query,hash:to.hash})},
    {path:'/admin/members/:id',redirect:to=>({name:'admin-member-detail',params:to.params,query:to.query,hash:to.hash})},
    {path:'/admin/coupons/new',redirect:to=>({name:'admin-coupon-new',query:to.query,hash:to.hash})},
    {path:'/admin/coupons/:id/edit',redirect:to=>({name:'admin-coupon-edit',params:to.params,query:to.query,hash:to.hash})},
    { path:'/admin/categories/new',redirect:to=>({name:'admin-category-new',query:to.query,hash:to.hash}) },
    { path:'/admin/categories/:id/edit',redirect:to=>({name:'admin-category-edit',params:to.params,query:to.query,hash:to.hash}) },
    { path:'/admin/products/new', redirect:to=>({name:'admin-product-new',query:to.query,hash:to.hash}) },
    ...['skus','images'].map(kind=>({path:'/admin/products/:id/'+kind,redirect:to=>({name:'admin-product-'+kind,params:to.params,query:to.query,hash:to.hash})})),
    { path:'/admin/products/:id/edit', redirect:to=>({name:'admin-product-edit',params:to.params,query:to.query,hash:to.hash}) },
    { path: '/admin/orders/:id/shipment', redirect: to => ({ name: 'admin-order-shipment', params: to.params, query: to.query, hash: to.hash }) },
    { path: '/admin/orders/:id', redirect: to => ({ name: 'admin-order-detail', params: to.params, query: to.query, hash: to.hash }) },
    { path: '/admin/:section(products|categories|orders)', redirect: to => ({ name: 'admin', params: to.params, query: to.query, hash: to.hash }) },
    ...['login', ...modules.map(([path]) => path)].map(path => ({
      path: '/admin/'+path, redirect: to => ({ path: '/'+path, query: to.query, hash: to.hash })
    }))
  )
  return routes
}
