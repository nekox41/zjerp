import request from '@/utils/request'

// 查询员工资料列表
export function listUser(query) {
  return request({
    url: '/management/user/list',
    method: 'get',
    params: query
  })
}

// 查询员工资料详细
export function getUser(id) {
  return request({
    url: '/management/user/' + id,
    method: 'get'
  })
}

// 新增员工资料
export function addUser(data) {
  return request({
    url: '/management/user',
    method: 'post',
    data: data
  })
}

// 修改员工资料
export function updateUser(data) {
  return request({
    url: '/management/user',
    method: 'put',
    data: data
  })
}

// 删除员工资料
export function delUser(id) {
  return request({
    url: '/management/user/' + id,
    method: 'delete'
  })
}
