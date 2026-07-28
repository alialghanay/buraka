export async function fetchProducts({ page = 0, size = 12, categoryId } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (categoryId) params.set('categoryId', String(categoryId))
  const res = await fetch(`/api/products?${params}`)
  if (!res.ok) throw new Error(`products fetch failed: ${res.status}`)
  return res.json()
}

export async function fetchProduct(slug) {
  const res = await fetch(`/api/products/${slug}`)
  if (!res.ok) throw new Error(`product fetch failed: ${res.status}`)
  return res.json()
}

export async function fetchCategories() {
  const res = await fetch('/api/categories')
  if (!res.ok) throw new Error(`categories fetch failed: ${res.status}`)
  return res.json()
}

export async function fetchReviews(productId) {
  const res = await fetch(`/api/reviews?productId=${productId}`)
  if (!res.ok) throw new Error(`reviews fetch failed: ${res.status}`)
  return res.json()
}

export async function createReview({ productId, title, rating, userId = 1 }) {
  const res = await fetch('/api/reviews', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User-Id': String(userId) },
    body: JSON.stringify({ productId, title, rating }),
  })
  if (!res.ok) throw new Error(`review create failed: ${res.status}`)
  return res.json()
}
