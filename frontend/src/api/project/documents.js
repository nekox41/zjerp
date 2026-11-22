import request from '@/utils/request'

export const projectDocuments = (id, params) => request({ url: `/management/work-project/${id}/documents`, params })
export const saveProjectDocument = (id, data) => request({ url: `/management/work-project/${id}/documents`, method: 'post', data })
export const deleteProjectDocument = (projectId, documentId) => request({
  url: `/management/work-project/${projectId}/documents/${documentId}`,
  method: 'delete'
})
export const projectMaterial = id => request({ url: `/management/work-project/${id}/materials` })
export const saveProjectMaterial = (id, data) => request({ url: `/management/work-project/${id}/materials`, method: 'put', data })
export const projectResourceOptions = (id, kind, name) => request({
  url: `/management/work-project/${id}/${kind}-options`,
  params: { name }
})
