import request from '@/utils/request';
export const contracts=params=>request({url:'/management/contract/list',params});
export const contract=id=>request({url:'/management/contract/'+id});
export const saveContract=data=>request({url:'/management/contract',method:data.id?'put':'post',data});
export const removeContract=id=>request({url:'/management/contract/'+id,method:'delete'});
export const intermediaries=params=>request({url:'/management/intermediary/list',params});
export const intermediaryOptions=params=>request({url:'/management/intermediary/options',params});
export const saveIntermediary=data=>request({url:'/management/intermediary',method:data.id?'put':'post',data});
export const removeIntermediary=id=>request({url:'/management/intermediary/'+id,method:'delete'});
