import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = fileURLToPath(new URL('.', import.meta.url))
export default {
  root: resolve(currentDir, '../..'),
  server: { host: '0.0.0.0', port: 5175 },
  build: { rollupOptions: { input: resolve(currentDir, 'index.html') } }
}
