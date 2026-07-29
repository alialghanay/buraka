import { useCallback, useEffect, useState } from 'react'
import { fetchProduct, fetchReviews } from '../api'
import ReviewForm from './ReviewForm'

export default function ProductDetail({ slug, onBack }) {
  const [product, setProduct] = useState(null)
  const [reviews, setReviews] = useState([])
  const [error, setError] = useState(null)

  const reload = useCallback(() => {
    fetchProduct(slug)
      .then((p) => {
        setProduct(p)
        return fetchReviews(p.productId)
      })
      .then(setReviews)
      .catch((e) => setError(e.message))
  }, [slug])

  useEffect(() => {
    reload()
  }, [reload])

  if (error) return <p>Error: {error}</p>
  if (!product) return <p>Loading…</p>

  return (
    <div>
      <button onClick={onBack}>← Back</button>
      <h2>{product.title}</h2>
      <p>{product.description}</p>
      <p>Price: {(product.price / 100).toFixed(2)} LYD · In stock: {product.quantity}</p>
      <p>★ {product.ratingsAverage.toFixed(1)} ({product.ratingsQuantity} reviews)</p>

      <h3>Reviews</h3>
      {reviews.length === 0 && <p>No reviews yet.</p>}
      {reviews.map((r) => (
        <div key={r.reviewId} style={{ borderTop: '1px solid #eee', padding: '8px 0' }}>
          <strong>{r.title}</strong> — ★ {r.rating} <em>(user {r.userId})</em>
        </div>
      ))}

      <h3>Write a review</h3>
      <ReviewForm productId={product.productId} onSubmitted={reload} />
    </div>
  )
}
