package top.aameow.management.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import top.aameow.management.mapper.ReportCredentialMapper;
import top.aameow.common.exception.ServiceException;
import top.aameow.common.utils.SecurityUtils;
@Service
public class ReportCredentialService {
 @Autowired private ReportCredentialMapper mapper;
 @Autowired private BusinessFiles files;
 public List<Map<String,Object>> list(){return mapper.list();}
 @Transactional(rollbackFor=Exception.class)
 public void save(Map<String,Object> v){
  if(!Arrays.asList("company","person").contains(WorkProjectService.text(v,"kind")))throw new ServiceException("资质类型无效");
  for(String key:Arrays.asList("title","holder","certificateNo","files")){
   String value=WorkProjectService.text(v,key);if(value.length()>(key.equals("files")?20000: key.equals("title")?200:100))throw new ServiceException("字段超长");v.put(key,value);
  }
  if(WorkProjectService.text(v,"title").isEmpty()||WorkProjectService.text(v,"files").isEmpty())throw new ServiceException("请填写资质名称并上传文件");
  if("person".equals(v.get("kind"))&&WorkProjectService.text(v,"holder").isEmpty())throw new ServiceException("请填写持证人员");
  String date=WorkProjectService.text(v,"validUntil");if(!date.isEmpty()){try{java.time.LocalDate.parse(date);}catch(Exception e){throw new ServiceException("有效期格式无效");}}
  v.put("validUntil",date.isEmpty()?null:date);v.put("actor",SecurityUtils.getUsername());
  if(v.get("id")==null)mapper.add(v);else if(mapper.edit(v)!=1)throw new ServiceException("资质已被修改，请刷新后重试");
  files.save("report_credential",WorkProjectService.number(v.get("id")),WorkProjectService.text(v,"files"));
 }
}
