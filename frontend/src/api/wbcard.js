import request from '@/utils/request'

export const nextCardNumber = () => request({ url: '/wbcard/gen/next-number' })
