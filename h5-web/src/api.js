export function token() {
  return localStorage.getItem('h5Token') || ''
}

export function setToken(value) {
  if (value) localStorage.setItem('h5Token', value)
  else localStorage.removeItem('h5Token')
}

let errorHandler = null
let refreshPromise = null

export function setErrorHandler(handler) {
  errorHandler = handler
}

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
  const silent = options.silent === true
  const headers = { ...(options.headers || {}) }
  const body = options.body
  if (token()) headers.Authorization = `Bearer ${token()}`
  if (body && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), options.timeout || 10000)
  let response
  try {
    response = await fetch(url, { ...options, headers, signal: controller.signal, credentials: 'include' })
  } catch (error) {
    const message = error.name === 'AbortError' ? '请求超时，请确认后端服务已启动' : '网络异常，请确认后端服务已启动'
    if (!silent && errorHandler) errorHandler(message)
    throw new Error(message)
  } finally {
    clearTimeout(timeout)
  }
  const text = await response.text()
  let result = null
  if (text) {
    try {
      result = JSON.parse(text)
    } catch {
      const message = text.trim() || `请求失败(${response.status})`
      if (!silent && errorHandler) errorHandler(message)
      throw new Error(message)
    }
  }
  if (!response.ok) {
    if (response.status === 401 && !retried && url !== '/api/auth/refresh') {
      try {
        await refreshAccessToken()
        return api(url, { ...options, body }, true)
      } catch {
        setToken('')
        window.dispatchEvent(new CustomEvent('nbys-auth-expired'))
      }
    }
    const message = result?.message || (response.status >= 500 ? '服务暂时不可用，请稍后重试' : `请求失败(${response.status})`)
    if (response.status !== 401 && !silent && errorHandler) errorHandler(message)
    throw new Error(message)
  }
  if (!result) return null
  if (result.code !== 0) {
    const message = result.message || '请求失败'
    if (!silent && errorHandler) errorHandler(message)
    throw new Error(message)
  }
  return result.data
}
