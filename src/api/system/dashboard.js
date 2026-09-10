import request from '@/utils/request'

// 首页员工统计
export function getUserStat() {
  return request({
    url: '/system/dashboard/userStat',
    method: 'get'
  })
}