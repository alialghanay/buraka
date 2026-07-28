/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/products': 'http://localhost:8081',
      '/api/categories': 'http://localhost:8081',
      '/api/brands': 'http://localhost:8081',
      '/api/reviews': 'http://localhost:8082',
    },
  },
  test: {
    environment: 'node',
  },
})
