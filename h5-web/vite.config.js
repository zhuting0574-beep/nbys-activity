import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    base: '/activity/',
    publicDir: fileURLToPath(new URL('./public', import.meta.url)),
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
