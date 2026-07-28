import { useState } from 'react'
import ProductList from './components/ProductList'
import ProductDetail from './components/ProductDetail'

export default function App() {
  const [selectedSlug, setSelectedSlug] = useState(null)

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 16, fontFamily: 'system-ui, sans-serif' }}>
      <h1 style={{ cursor: 'pointer' }} onClick={() => setSelectedSlug(null)}>
        Buraka Store
      </h1>
      {selectedSlug ? (
        <ProductDetail slug={selectedSlug} onBack={() => setSelectedSlug(null)} />
      ) : (
        <ProductList onSelect={setSelectedSlug} />
      )}
    </div>
  )
}
