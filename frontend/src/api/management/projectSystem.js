import request from '@/utils/request'

// 查询项目系统列表
export function listProjectSystem(query) {
  return request({
    url: '/management/projectSystem/list',
    method: 'get',
    params: query
  })
}

// 查询项目系统详细
export function getProjectSystem(id) {
  return request({
    url: '/management/projectSystem/' + id,
    method: 'get'
  })
}

// 新增项目系统
export function addProjectSystem(data) {
  return request({
    url: '/management/projectSystem',
    method: 'post',
    data: data
  })
}

// 修改项目系统
export function updateProjectSystem(data) {
  return request({
    url: '/management/projectSystem',
    method: 'put',
    data: data
  })
}

// 删除项目系统
export function delProjectSystem(id) {
  return request({
    url: '/management/projectSystem/' + id,
    method: 'delete'
  })
}
