import request from '@/utils/request'

// 查询开销记录列表
export function listCost(query) {
  return request({
    url: '/outside/cost/list',
    method: 'get',
    params: query
  })
}

// 查询开销记录详细
export function getCost(id) {
  return request({
    url: '/outside/cost/' + id,
    method: 'get'
  })
}

// 新增开销记录
export function addCost(data) {
  return request({
    url: '/outside/cost',
    method: 'post',
    data: data
  })
}

// 修改开销记录
export function updateCost(data) {
  return request({
    url: '/outside/cost',
    method: 'put',
    data: data
  })
}

// 删除开销记录
export function delCost(id) {
  return request({
    url: '/outside/cost/' + id,
    method: 'delete'
  })
}
