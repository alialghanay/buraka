import { useEffect, useState } from 'react'
import { fetchProducts, fetchCategories } from '../api'

export default function ProductList({ onSelect }) {
  const [page, setPage] = useState(0)
  const [categoryId, setCategoryId] = useState('')
  const [data, setData] = useState({ content: [], totalElements: 0, last: true })
  const [categories, setCategories] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchCategories().then(setCategories).catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    fetchProducts({ page, size: 12, categoryId: categoryId || undefined })
      .then(setData)
      .catch((e) => setError(e.message))
  }, [page, categoryId])

  if (error) return <p>Error: {error} — is catalog-service running on :8081?</p>

  return (
    <div>
      <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0) }}>
        <option value="">All categories</option>
        {categories.map((c) => (
          <option key={c.categoryId} value={c.categoryId}>{c.title}</option>
        ))}
      </select>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginTop: 12 }}>
        {data.content.map((p) => (
          <div key={p.productId} onClick={() => onSelect(p.slug)}
               style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, cursor: 'pointer' }}>
            <div style={{ fontWeight: 600 }}>{p.title}</div>
            <div>★ {p.ratingsAverage.toFixed(1)} ({p.ratingsQuantity})</div>
            <div>{(p.price / 100).toFixed(2)} LYD</div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 12 }}>
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>Prev</button>
        <span style={{ margin: '0 8px' }}>Page {page + 1}</span>
        <button disabled={data.last} onClick={() => setPage(page + 1)}>Next</button>
      </div>
    </div>
  )
}
