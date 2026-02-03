import request from '@/utils/request'

// 查询中间人列表
export function listAgent(query) {
  return request({
    url: '/project/agent/list',
    method: 'get',
    params: query
  })
}

// 查询中间人详细
export function getAgent(id) {
  return request({
    url: '/project/agent/' + id,
    method: 'get'
  })
}

// 新增中间人
export function addAgent(data) {
  return request({
    url: '/project/agent',
    method: 'post',
    data: data
  })
}

// 修改中间人
export function updateAgent(data) {
  return request({
    url: '/project/agent',
    method: 'put',
    data: data
  })
}

// 删除中间人
export function delAgent(id) {
  return request({
    url: '/project/agent/' + id,
    method: 'delete'
  })
}
