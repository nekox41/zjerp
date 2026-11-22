import request from '@/utils/request'

// 查询分类管理列表
export function listCategories(query) {
  return request({
    url: '/common/categories/list',
    method: 'get',
    params: query
  })
}

// 查询分类管理详细
export function getCategories(id) {
  return request({
    url: '/common/categories/' + id,
    method: 'get'
  })
}

// 新增分类管理
export function addCategories(data) {
  return request({
    url: '/common/categories',
    method: 'post',
    data: data
  })
}

// 修改分类管理
export function updateCategories(data) {
  return request({
    url: '/common/categories',
    method: 'put',
    data: data
  })
}

// 删除分类管理
export function delCategories(id) {
  return request({
    url: '/common/categories/' + id,
    method: 'delete'
  })
}


// 查询待办管理列表
export function listTodos(query) {
  return request({
    url: '/common/todos/list',
    method: 'get',
    params: query
  })
}

// 查询待办管理详细
export function getTodos(id) {
  return request({
    url: '/common/todos/' + id,
    method: 'get'
  })
}

// 新增待办管理
export function addTodos(data) {
  return request({
    url: '/common/todos',
    method: 'post',
    data: data
  })
}

// 修改待办管理
export function updateTodos(data) {
  return request({
    url: '/common/todos',
    method: 'put',
    data: data
  })
}

// 删除待办管理
export function delTodos(id) {
  return request({
    url: '/common/todos/' + id,
    method: 'delete'
  })
}

// GET http://localhost:8080/common/categories/todos
// 获取所有分类及分类下数据
export function listCategoriesWithTodos(query) {
  return request({
    url: '/common/categories/todos',
    method: 'get',
    params: query
  })
}

// 获取制定分类下的所有待办事项
export function getTodosWithCategoriesId(id) {
    return request({
        url: '/common/categories/' + id + '/todos',
        method: 'get'
    })
}

// 批量更新待办排序
export function updateTodosSort(data) {
  return request({
    url: '/common/todos/batch-sort',
    method: 'put',
    data: data
  })
}