import { api } from '../api'

const ROOT = '/api/escape/admin'

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function withQuery(path, params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) query.set(key, value)
  })
  const suffix = query.toString()
  return `${ROOT}${path}${suffix ? `?${suffix}` : ''}`
}

export function escapeGet(path, params) {
  return api(withQuery(path, params))
}

export function escapeCreate(path, body) {
  return api(`${ROOT}${path}`, { method: 'POST', body })
}

export function escapeUpdate(path, id, body) {
  return api(`${ROOT}${path}/${id}`, { method: 'PUT', body })
}

export function escapeRemove(path, id) {
  return api(`${ROOT}${path}/${id}`, { method: 'DELETE' })
}

export function escapeAction(path, id, action, body = {}) {
  return api(`${ROOT}${path}/${id}/${action}`, {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey() }
  })
}

export function rowsOf(result) {
  if (Array.isArray(result)) return result
  return result?.items || result?.records || result?.content || result?.list || []
}
