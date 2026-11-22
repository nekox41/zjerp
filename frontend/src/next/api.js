const API_BASE = import.meta.env.VITE_NEXT_API_BASE || '/next-api'

export async function api(path, options = {}) {
  const token = sessionStorage.getItem('erp-next-token')
  const headers = new Headers(options.headers || {})
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (response.status === 204) return null
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(payload.detail || payload.message || `请求失败（${response.status}）`)
    error.status = response.status
    throw error
  }
  return payload
}