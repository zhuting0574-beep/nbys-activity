import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = fileURLToPath(new URL('.', import.meta.url))

export default {
  root: resolve(currentDir, '../..'),
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/uploads': 'http://127.0.0.1:8080'
    }
  }
}
