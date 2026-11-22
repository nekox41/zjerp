import request from '@/utils/request'

// 查询报账总览列表
export function listOutside(query) {
  return request({
    url: '/financial/outside/list',
    method: 'get',
    params: query
  })
}

// 查询报账总览详细
export function getOutside(id) {
  return request({
    url: '/financial/outside/' + id,
    method: 'get'
  })
}

// 新增报账总览
export function addOutside(data) {
  return request({
    url: '/financial/outside',
    method: 'post',
    data: data
  })
}

// 修改报账总览
export function updateOutside(data) {
  return request({
    url: '/financial/outside',
    method: 'put',
    data: data
  })
}

// 删除报账总览
export function delOutside(id) {
  return request({
    url: '/financial/outside/' + id,
    method: 'delete'
  })
}
