const API_BASE = import.meta.env.VITE_NEXT_API_BASE || '/next-api'

export async function api(path, options = {}) {
  const token = sessionStorage.getItem('erp-next-token')
  const headers = new Headers(options.headers || {})
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
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
export async function apiBlob(path, options = {}) {
  const token = sessionStorage.getItem('erp-next-token')
  const headers = new Headers(options.headers || {})
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}))
    throw new Error(payload.detail || payload.message || `请求失败（${response.status}）`)
  }
  return response.blob()
}

export async function uploadFile(file) {
  const form = new FormData()
  form.append('file', file)
  return api('/files', { method: 'POST', body: form })
}

export async function downloadFile(id,name='') {
  const token=sessionStorage.getItem('erp-next-token')
  const response=await fetch(`${API_BASE}/files/${id}/download`,{headers:token?{Authorization:`Bearer ${token}`}:{}});
  if(!response.ok){const payload=await response.json().catch(()=>({}));throw new Error(payload.detail||payload.message||'文件下载失败')}
  const blob=await response.blob(),url=URL.createObjectURL(blob),a=document.createElement('a');
  a.href=url;a.download=name||'attachment';document.body.appendChild(a);a.click();a.remove();setTimeout(() => URL.revokeObjectURL(url), 1500);
}
