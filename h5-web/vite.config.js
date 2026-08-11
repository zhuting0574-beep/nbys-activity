import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    base: '/activity/',
    plugins: [vue()],
    server: {
      proxy: {
        '/api': 'http://localhost:8080',
        '/uploads': env.UPLOAD_PROXY_TARGET || 'http://localhost:8080'
      }
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true
    }
  }
})
