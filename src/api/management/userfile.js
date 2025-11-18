import request from '@/utils/request'

// 查询资料附件列表
export function listUserfile(query) {
  return request({
    url: '/management/userfile/list',
    method: 'get',
    params: query
  })
}

// 查询资料附件详细
export function getUserfile(id) {
  return request({
    url: '/management/userfile/' + id,
    method: 'get'
  })
}

// 新增资料附件
export function addUserfile(data) {
  return request({
    url: '/management/userfile',
    method: 'post',
    data: data
  })
}

// 修改资料附件
export function updateUserfile(data) {
  return request({
    url: '/management/userfile',
    method: 'put',
    data: data
  })
}

// 删除资料附件
export function delUserfile(id) {
  return request({
    url: '/management/userfile/' + id,
    method: 'delete'
  })
}
