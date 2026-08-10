import { api } from '../api'

const base = '/api/escape/h5'

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function writeOptions(method, body) {
  return {
    method,
    body,
    headers: { 'Idempotency-Key': idempotencyKey() }
  }
}

export const escapeApi = {
  dashboard: () => api(`${base}/dashboard`, { silent: true }),
  matches: () => api(`${base}/matches`, { silent: true }),
  managedMatches: () => api(`${base}/managed-matches`, { silent: true }),
  managedMatch: matchId => api(`${base}/managed-matches/${matchId}`, { silent: true }),
  matchOptions: () => api(`${base}/managed-matches/options`, { silent: true }),
  createManagedMatch: body => api(`${base}/managed-matches`, { method: 'POST', body }),
  updateManagedMatch: (matchId, body) => api(`${base}/managed-matches/${matchId}`, { method: 'PUT', body }),
  cancelManagedMatch: matchId => api(`${base}/managed-matches/${matchId}`, { method: 'DELETE' }),
  deleteManagedMatch: matchId => api(`${base}/managed-matches/${matchId}/permanent`, { method: 'DELETE' }),
  matchDetail: matchId => api(`${base}/matches/${matchId}`),
  joinMatch: matchId => api(`${base}/matches/${matchId}/join`, { method: 'POST' }),
  warehouse: type => api(`${base}/warehouses/${encodeURIComponent(type)}`, { silent: true }),
  shop: category => api(`${base}/shop/products?category=${encodeURIComponent(category)}`, { silent: true }),
  records: () => api(`${base}/records`, { silent: true }),
  recordDetail: matchId => api(`${base}/records/${matchId}`),
  loadoutOptions: matchId => api(`${base}/matches/${matchId}`),
  saveLoadout: (matchId, body) => api(`${base}/matches/${matchId}/loadout`, { method: 'PUT', body }),
  lockLoadout: matchId => api(`${base}/matches/${matchId}/loadout/lock`, writeOptions('POST')),
  matchControl: matchId => api(`${base}/matches/${matchId}/control`),
  startMatch: matchId => api(`${base}/matches/${matchId}/control/start`, writeOptions('POST')),
  settlementPreview: matchId => api(`${base}/matches/${matchId}/control/settlement`),
  settleMatch: (matchId, body) => api(`${base}/matches/${matchId}/control/settle`, writeOptions('POST', body)),
  purchase: (productId, quantity = 1) => api(`${base}/shop/products/${productId}/purchase`, {
    ...writeOptions('POST', { quantity })
  }),
  sellItem: itemId => api(`${base}/inventory/${itemId}/sell`, writeOptions('POST')),
  sellAll: warehouseType => api(`${base}/inventory/sell-all`, writeOptions('POST', { warehouse_type: warehouseType })),
  moveItem: (itemId, body) => api(`${base}/inventory/${itemId}/move`, writeOptions('POST', body))
}
