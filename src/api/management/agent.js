import request from '@/utils/request'

// 查询代理人列表
export function listAgent(query) {
  return request({
    url: '/management/agent/list',
    method: 'get',
    params: query
  })
}

// 查询代理人详细
export function getAgent(id) {
  return request({
    url: '/management/agent/' + id,
    method: 'get'
  })
}

// 新增代理人
export function addAgent(data) {
  return request({
    url: '/management/agent',
    method: 'post',
    data: data
  })
}

// 修改代理人
export function updateAgent(data) {
  return request({
    url: '/management/agent',
    method: 'put',
    data: data
  })
}

// 删除代理人
export function delAgent(id) {
  return request({
    url: '/management/agent/' + id,
    method: 'delete'
  })
}
