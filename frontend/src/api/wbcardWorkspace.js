import request from '@/utils/request'
export const cardList=params=>request({url:'/management/wbcard/list',params})
export const generateCard=data=>request({url:'/wbcard/gen/create',method:'post',data})
export const previewCard=data=>request({url:'/wbcard/gen/preview',method:'post',data,responseType:'blob'})
export const saveCard=data=>request({url:'/management/wbcard',method:'put',data})
export const removeCard=id=>request({url:'/management/wbcard/'+id,method:'delete'})
export const resourceList=(kind,params)=>request({url:'/wbcard/resources/'+kind,params})
export const saveResource=(kind,data)=>request({url:'/wbcard/resources/'+kind,method:'post',data})
export const removeResource=(kind,id)=>request({url:`/wbcard/resources/${kind}/${id}`,method:'delete'})
