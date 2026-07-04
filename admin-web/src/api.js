import { ElMessage } from 'element-plus'

export function token() {
  return localStorage.getItem('adminToken') || ''
}

export function setToken(value) {
  if (value) localStorage.setItem('adminToken', value)
  else localStorage.removeItem('adminToken')
}

let refreshPromise = null

async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async response => {
        const result = await response.json().catch(() => null)
        if (!response.ok || result?.code !== 0 || !result?.data?.token) throw new Error('登录已失效')
        setToken(result.data.token)
        return result.data.token
      })
      .finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

export async function api(url, options = {}, retried = false) {
  const hadToken = Boolean(token())
  const headers = { ...(options.headers || {}) }
  const body = options.body
  if (token()) headers.Authorization = `Bearer ${token()}`
  if (body && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }
  const response = await fetch(url, { ...options, headers, credentials: 'include' })
  const result = await response.json()
  if (response.status === 401 && !retried && url !== '/api/auth/refresh') {
    try {
      await refreshAccessToken()
      return api(url, { ...options, body }, true)
    } catch {
      setToken('')
      const shouldReturnToLogin = hadToken || new URLSearchParams(location.search).get('view') === 'activities'
      if (shouldReturnToLogin) {
        const h5Base = location.port === '5173' ? `${location.protocol}//${location.hostname}:5174/activity/` : '/activity/'
        window.location.href = `${h5Base}?returnTo=${encodeURIComponent(location.href)}#/app`
      }
    }
  }
  if (result.code !== 0) {
    if (response.status !== 401) ElMessage.error(result.message || '请求失败')
    throw new Error(result.message || '请求失败')
  }
  return result.data
}
