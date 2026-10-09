import http from './http'

export const adminApi = {
  updateProductImages: (id,payload) => http.put(`/admin/products/${id}/images`,payload),
  coupons: params => http.get('/admin/coupons', { params }),
  coupon: id => http.get(`/admin/coupons/${id}`),
  createCoupon: payload => http.post('/admin/coupons', payload),
  updateCoupon: (id,payload) => http.put(`/admin/coupons/${id}`,payload),
  members: params => http.get('/admin/members', { params }),
  member: id => http.get(`/admin/members/${id}`),
  memberHistory: (id, params) => http.get(`/admin/members/${id}/history`, { params }),
  memberStatus: (id, payload) => http.patch(`/admin/members/${id}/status`, payload),
  movements: params => http.get('/admin/inventory/movements', { params }),
  lowStock: params => http.get('/admin/inventory/low-stock', { params }),
  uploadImage: file => {
    const data = new FormData()
    data.append('file', file)
    return http.post('/admin/uploads/images', data, { headers: { 'Content-Type': undefined } })
  },
  products: params => http.get('/admin/products', { params }),
  product: id => http.get(`/admin/products/${id}`),
  createProduct: data => http.post('/admin/products', data),
  updateProduct: (id, data) => http.put(`/admin/products/${id}`, data),
  offShelf: id => http.delete(`/admin/products/${id}`),
  addSku: (id, data) => http.post(`/admin/products/${id}/skus`, data),
  updateSku: (id, data) => http.put(`/admin/skus/${id}`, data),
  categories: params => http.get('/admin/categories', { params }),
  createCategory: data => http.post('/admin/categories', data),
  updateCategory: (id, data) => http.put(`/admin/categories/${id}`, data),
  deactivateCategory: id => http.delete(`/admin/categories/${id}`),
  orders: params => http.get('/admin/orders', { params }),
  order: id => http.get(`/admin/orders/${id}`),
  shipment: id => http.get(`/admin/orders/${id}/shipment`),
  ship: (id, payload) => http.post(`/admin/orders/${id}/shipment`, payload),
  updateStatus: (id, status) => http.patch(`/admin/orders/${id}/status`, { status })
}
