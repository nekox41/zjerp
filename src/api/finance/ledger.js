import request from '@/utils/request'

export const ledgerOverview = () => request({ url: '/finance/ledger/overview' })
export const ledgerList = (kind, params) => request({ url: '/finance/ledger/' + kind, params })
export const ledgerDetail = (kind, id) => request({ url: `/finance/ledger/${kind}/${id}` })
export const ledgerSave = (kind, data) => request({
  url: '/finance/ledger/' + kind,
  method: 'post',
  data,
  headers: { repeatSubmit: false }
})
export const ledgerVoid = (kind, id, data) => request({ url: `/finance/ledger/${kind}/${id}/void`, method: 'put', data })
export const ledgerOptions = (kind, name) => request({ url: `/finance/ledger/options/${kind}`, params: { name } })
