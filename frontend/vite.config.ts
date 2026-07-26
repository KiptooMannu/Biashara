import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    // Proxy the API so the browser sees a single origin in development and
    // cookies/CORS never become a problem during the demo.
    proxy: {
      '/api': {
        target: 'https://biashara-zl2z.onrender.com',
        changeOrigin: true,
        secure: true,
      },
    },
  },
})
