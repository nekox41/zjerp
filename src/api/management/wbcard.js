import request from '@/utils/request'

// 查询维保卡管理列表
export function listWbcard(query) {
  return request({
    url: '/management/wbcard/list',
    method: 'get',
    params: query
  })
}

// 查询维保卡管理详细
export function getWbcard(id) {
  return request({
    url: '/management/wbcard/' + id,
    method: 'get'
  })
}

// 新增维保卡管理
export function addWbcard(data) {
  return request({
    url: '/management/wbcard',
    method: 'post',
    data: data
  })
}

// 修改维保卡管理
export function updateWbcard(data) {
  return request({
    url: '/management/wbcard',
    method: 'put',
    data: data
  })
}

// 删除维保卡管理
export function delWbcard(id) {
  return request({
    url: '/management/wbcard/' + id,
    method: 'delete'
  })
}

// 生成维保卡
export function generateWbcard(data) {
  return request({
    url: '/wbcard/gen/create',
    method: 'post',
    data: data
  })
}
