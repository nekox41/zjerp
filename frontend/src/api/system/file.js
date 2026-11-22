import request from '@/utils/request'

// 按 fileId 列表批量查询文件信息（回显用）
export function listByIds(ids) {
  return request({
    url: '/system/file/listByIds',
    method: 'get',
    params: { ids: Array.isArray(ids) ? ids.join(',') : ids }
  })
}

// 按业务查文件
export function listByBiz(bizType, bizId) {
  return request({
    url: '/system/file/listByBiz',
    method: 'get',
    params: { bizType, bizId }
  })
}

// 删除文件
export function delFile(fileId) {
  return request({
    url: '/system/file/' + fileId,
    method: 'delete'
  })
}

// 业务保存后回填 biz_id
export function bindBiz(ids, bizType, bizId) {
  return request({
    url: '/system/file/bindBiz',
    method: 'post',
    params: {
      ids: Array.isArray(ids) ? ids.join(',') : ids,
      bizType,
      bizId
    }
  })
}
export function listFiles(params) {
  return request({ url: '/system/file/list', method: 'get', params })
}

export function filesByUrls(urls) {
  return request({ url: '/system/file/byUrls', method: 'post', data: urls })
}

export function migrateFiles() {
  return request({ url: '/system/file/migrate', method: 'post', timeout: 0 })
}
