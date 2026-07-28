import { useState } from 'react'
import { createReview } from '../api'

export default function ReviewForm({ productId, onSubmitted }) {
  const [title, setTitle] = useState('')
  const [rating, setRating] = useState(5)
  const [status, setStatus] = useState(null)

  async function submit(e) {
    e.preventDefault()
    setStatus('sending')
    try {
      await createReview({ productId, title, rating: Number(rating) })
      setTitle('')
      setStatus('published — rating aggregate updates in a moment')
      setTimeout(onSubmitted, 800) // allow the ReviewCreated event to propagate
    } catch (e2) {
      setStatus(`failed: ${e2.message}`)
    }
  }

  return (
    <form onSubmit={submit}>
      <input value={title} onChange={(e) => setTitle(e.target.value)}
             placeholder="Review title" required maxLength={200} style={{ width: 300 }} />
      <select value={rating} onChange={(e) => setRating(e.target.value)}>
        {[5, 4, 3, 2, 1].map((r) => <option key={r} value={r}>{r}★</option>)}
      </select>
      <button type="submit">Submit</button>
      {status && <span style={{ marginLeft: 8 }}>{status}</span>}
    </form>
  )
}
