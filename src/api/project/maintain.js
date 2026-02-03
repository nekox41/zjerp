import request from '@/utils/request'

// 查询维保项目列表
export function listMaintain(query) {
  return request({
    url: '/project/maintain/list',
    method: 'get',
    params: query
  })
}

// 查询维保项目详细
export function getMaintain(id) {
  return request({
    url: '/project/maintain/' + id,
    method: 'get'
  })
}

// 新增维保项目
export function addMaintain(data) {
  return request({
    url: '/project/maintain',
    method: 'post',
    data: data
  })
}

// 修改维保项目
export function updateMaintain(data) {
  return request({
    url: '/project/maintain',
    method: 'put',
    data: data
  })
}

// 删除维保项目
export function delMaintain(id) {
  return request({
    url: '/project/maintain/' + id,
    method: 'delete'
  })
}
