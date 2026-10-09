import http from './http'

export const authApi = {
  register: (payload) => http.post('/auth/register', payload),
  login: (payload) => http.post('/auth/login', payload)
}

export const memberApi = {
  me: () => http.get('/members/me'),
  update: (payload) => http.put('/members/me', payload),
  changePassword: (payload) => http.put('/members/me/password', payload),
  listAddresses: () => http.get('/members/me/addresses'),
  address: id => http.get(`/members/me/addresses/${id}`),
  createAddress: (payload) => http.post('/members/me/addresses', payload),
  updateAddress: (id, payload) => http.put(`/members/me/addresses/${id}`, payload),
  deleteAddress: (id) => http.delete(`/members/me/addresses/${id}`)
}

export const categoryApi = {
  list: () => http.get('/categories'),
  tree: () => http.get('/categories/tree'),
  getBySlug: (slug) => http.get(`/categories/slug/${slug}`)
}

export const productApi = {
  brands: () => http.get('/products/brands'),
  list: (params) => http.get('/products', { params }),
  getById: (id) => http.get(`/products/${id}`),
  getBySlug: (slug) => http.get(`/products/slug/${slug}`),
  related: (id) => http.get(`/products/${id}/related`)
}

export const cartApi = {
  get: () => http.get('/cart'),
  addItem: (payload) => http.post('/cart/items', payload),
  updateItem: (itemId, payload) => http.patch(`/cart/items/${itemId}`, payload),
  removeItem: (itemId) => http.delete(`/cart/items/${itemId}`),
  clear: () => http.delete('/cart')
}

export const wishlistApi = {
  ids: () => http.get('/wishlist/ids'),
  list: (params) => http.get('/wishlist', { params }),
  add: (productId) => http.put(`/wishlist/${productId}`),
  remove: (productId) => http.delete(`/wishlist/${productId}`)
}

export const orderApi = {
  requestIdentity: () => http.get('/orders/request-identity'),
  checkoutQuote: () => http.get('/orders/checkout-quote'),
  byRequestId: requestId => http.get(`/orders/requests/${encodeURIComponent(requestId)}`),
  quote: code => http.post('/orders/quote', { code }),
  shipment: id => http.get(`/orders/${id}/shipment`),
  create: (payload) => http.post('/orders', payload),
  list: (params) => http.get('/orders', { params }),
  detail: (id) => http.get(`/orders/${id}`),
  cancel: (id) => http.post(`/orders/${id}/cancel`)
}
