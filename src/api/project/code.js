import request from '@/utils/request'

// 查询编号申请列表
export function listCode(query) {
  return request({
    url: '/project/code/list',
    method: 'get',
    params: query
  })
}

// 查询编号申请详细
export function getCode(id) {
  return request({
    url: '/project/code/' + id,
    method: 'get'
  })
}

// 新增编号申请
export function addCode(data) {
  return request({
    url: '/project/code',
    method: 'post',
    data: data
  })
}

// 修改编号申请
export function updateCode(data) {
  return request({
    url: '/project/code',
    method: 'put',
    data: data
  })
}

// 删除编号申请
export function delCode(id) {
  return request({
    url: '/project/code/' + id,
    method: 'delete'
  })
}
