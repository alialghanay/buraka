import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchProducts, fetchProduct, createReview } from './api'

describe('api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('builds products URL with pagination and category', async () => {
    const json = vi.fn().mockResolvedValue({ content: [] })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json }))

    await fetchProducts({ page: 2, size: 12, categoryId: 5 })

    expect(fetch).toHaveBeenCalledWith('/api/products?page=2&size=12&categoryId=5')
  })

  it('fetches product detail by slug', async () => {
    const json = vi.fn().mockResolvedValue({ slug: 'product-1' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json }))

    const product = await fetchProduct('product-1')

    expect(fetch).toHaveBeenCalledWith('/api/products/product-1')
    expect(product.slug).toBe('product-1')
  })

  it('throws when response is not ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))

    await expect(fetchProducts()).rejects.toThrow('500')
  })

  it('sends review with X-User-Id header and JSON body', async () => {
    const json = vi.fn().mockResolvedValue({ reviewId: 1 })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json }))

    await createReview({ productId: 10, title: 'Great', rating: 5, userId: 7 })

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/reviews')
    expect(options.headers['X-User-Id']).toBe('7')
    expect(JSON.parse(options.body)).toEqual({ productId: 10, title: 'Great', rating: 5 })
  })
})
