import { filesByUrls } from '@/api/system/file'

export function fileUrl(url) {
  if (!url || /^(https?:|blob:|data:)/i.test(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

export async function restoreFileNames(list) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API
  const canonical = url => url?.startsWith(baseUrl + '/profile/') ? url.slice(baseUrl.length) : url
  const urls = [...new Set(list.map(file => canonical(file.url)).filter(url => url?.startsWith('/profile/')))]
  if (!urls.length) return
  const { data } = await filesByUrls(urls.slice(0, 100))
  const names = new Map((data || []).map(file => [file.fileUrl, file.fileName]))
  list.forEach(file => { if (names.has(canonical(file.url))) file.name = names.get(canonical(file.url)) })
}
