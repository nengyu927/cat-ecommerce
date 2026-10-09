import { defineStore } from 'pinia'
import { categoryApi, productApi } from '@/api'

export const useProductStore = defineStore('product', {
  state: () => ({
    categories: [],
    products: [],
    page: 0,
    size: 12,
    totalElements: 0,
    totalPages: 0,
    last: true,
    loading: false,
    requestVersion: 0,
    filters: { categoryId: null, keyword: '' }
  }),
  actions: {
    async fetchCategories() {
      const tree = await categoryApi.list()
      const flatten = (nodes) => nodes.flatMap(node => [node, ...flatten(node.children || [])])
      this.categories = flatten(tree)
    },
    async fetchProducts({ page = 0, size = 12, categoryId = null, keyword = '', sort = 'createdAt,desc', brand = '', minPrice = '', maxPrice = '', inStock = false } = {}) {
      const version = ++this.requestVersion
      this.loading = true
      this.filters = { categoryId, keyword }
      try {
        const data = await productApi.list({ page, size, categoryId, keyword, sort, brand: brand || undefined, minPrice: minPrice === '' ? undefined : minPrice, maxPrice: maxPrice === '' ? undefined : maxPrice, inStock: inStock ? true : undefined })
        if (version !== this.requestVersion) return
        this.products = data.content
        this.page = data.page
        this.size = data.size
        this.totalElements = data.totalElements
        this.totalPages = data.totalPages
        this.last = data.last
        return data
      } finally {
        if (version === this.requestVersion) this.loading = false
      }
    },
    async fetchProduct(id) {
      return productApi.getById(id)
    }
  }
})
