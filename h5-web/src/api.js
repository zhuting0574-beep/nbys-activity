export function token() {
  return localStorage.getItem('h5Token') || ''
}

export function setToken(value) {
  if (value) localStorage.setItem('h5Token', value)
  else localStorage.removeItem('h5Token')
}

let errorHandler = null

export function setErrorHandler(handler) {
  errorHandler = handler
}

export async function api(url, options = {}) {
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
    response = await fetch(url, { ...options, headers, signal: controller.signal })
  } catch (error) {
    const message = error.name === 'AbortError' ? '请求超时，请确认后端服务已启动' : '网络异常，请确认后端服务已启动'
    if (errorHandler) errorHandler(message)
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
      if (errorHandler) errorHandler(message)
      throw new Error(message)
    }
  }
  if (!response.ok) {
    const message = result?.message || text.trim() || `请求失败(${response.status})`
    if (errorHandler) errorHandler(message)
    throw new Error(message)
  }
  if (!result) return null
  if (result.code !== 0) {
    const message = result.message || '请求失败'
    if (errorHandler) errorHandler(message)
    throw new Error(message)
  }
  return result.data
}
