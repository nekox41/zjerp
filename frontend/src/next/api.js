const API_BASE = import.meta.env.VITE_NEXT_API_BASE || '/next-api'

export async function api(path, options = {}) {
  const token = sessionStorage.getItem('erp-next-token')
  const headers = new Headers(options.headers || {})
  if (options.body && !(options.body instanceof FormData) && !(options.body instanceof Blob) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
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

export async function privateFileBlob(id) {
  const token = sessionStorage.getItem('erp-next-token')
  const response = await fetch(`${API_BASE}/files/${id}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}))
    throw new Error(payload.detail || payload.message || '文件预览失败')
  }
  return response.blob()
}

export async function downloadFile(id, name = '') {
  const blob = await privateFileBlob(id)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name || 'attachment'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 2000)
}
