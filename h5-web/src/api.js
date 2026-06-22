export function token() {
  return localStorage.getItem('h5Token') || ''
}

export function setToken(value) {
  if (value) localStorage.setItem('h5Token', value)
  else localStorage.removeItem('h5Token')
}

export async function api(url, options = {}) {
  const headers = { ...(options.headers || {}) }
  const body = options.body
  if (token()) headers.Authorization = `Bearer ${token()}`
  if (body && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }
  const response = await fetch(url, { ...options, headers })
  const result = await response.json()
  if (result.code !== 0) {
    alert(result.message || '请求失败')
    throw new Error(result.message || '请求失败')
  }
  return result.data
}
