import request from '@/utils/request'

export const expenseList = params => request({ url: '/finance/reimbursements', params })
export const expenseDetail = id => request({ url: '/finance/reimbursements/' + id })
export const expenseAnalysis = params => request({ url: '/finance/analysis', params })
export const expenseStatus = (id, data) => request({ url: `/finance/reimbursements/${id}/status`, method: 'put', data })
export const expenseOptions = (kind, name, publicMode = false) => request({
  url: `/finance/${publicMode ? 'public/' : ''}${kind}`,
  params: { name },
  headers: { isToken: !publicMode }
})
export const submitExpense = (data, publicMode = false) => request({
  url: publicMode ? '/finance/public/submit' : '/finance/reimbursements',
  method: 'post',
  data,
  headers: { 'Content-Type': 'multipart/form-data', isToken: !publicMode, repeatSubmit: false },
  timeout: 120000
})
