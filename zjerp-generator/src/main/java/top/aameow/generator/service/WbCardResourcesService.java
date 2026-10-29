package top.aameow.generator.service;
import java.util.*;
import java.nio.file.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aameow.generator.mapper.WbCardResourcesMapper;
import top.aameow.common.exception.ServiceException;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.management.service.BusinessFiles;
import top.aameow.system.service.ISysFilePlusService;
import top.aameow.system.domain.SysFilePlus;
@Service
public class WbCardResourcesService {
 @Autowired private WbCardResourcesMapper mapper;
 @Autowired private ISysFilePlusService files;
 @Autowired private BusinessFiles business;
 public WbCardResourcesMapper data(){return mapper;}
 private void field(Map<String,Object> v,String key,int max,boolean required){String s=Objects.toString(v.get(key),"").trim();if((required&&s.isEmpty())||s.length()>max)throw new ServiceException(key+"不能为空或超出长度限制");v.put(key,s);}
 private Long id(Map<String,Object> v){if(v.get("id")==null)return null;try{return Long.valueOf(v.get("id").toString());}catch(Exception e){throw new ServiceException("记录ID无效");}}
 private void pdf(String url) throws Exception {
  SysFilePlus f=files.getByUrl(url);if(f==null||!"pdf".equalsIgnoreCase(f.getFileExt())||!"0".equals(f.getDelFlag()))throw new ServiceException("请选择通过文件上传接口上传的PDF");
  Path base=Paths.get(RuoYiConfig.getProfile()).toRealPath(),path=base.resolve(f.getFilePath()).normalize().toRealPath();
  if(!path.startsWith(base)||Files.size(path)>50L*1024*1024)throw new ServiceException("PDF文件无效或超过50MB");
  try(PDDocument doc=Loader.loadPDF(path.toFile())){if(doc.isEncrypted()||doc.getNumberOfPages()<1||doc.getNumberOfPages()>300)throw new ServiceException("仅支持未加密的1至300页PDF");}
 }
 @Transactional(rollbackFor=Exception.class)
 public Long saveMaterial(Map<String,Object> v) throws Exception {
  field(v,"name",200,true);field(v,"remark",2000,false);field(v,"file",600,true);pdf(v.get("file").toString());Long id=id(v);
  if((id==null?mapper.addMaterial(v):mapper.editMaterial(v))!=1)throw new ServiceException("资料不存在或已删除");
  id=id(v);business.save("wbcard_material",id,v.get("file").toString());return id;
 }
 @Transactional(rollbackFor=Exception.class)
 public void removeMaterial(Long id){if(mapper.removeMaterial(id)!=1)throw new ServiceException("资料不存在");business.save("wbcard_material",id,"");}
 @Transactional(rollbackFor=Exception.class)
 public Long saveContact(Map<String,Object> v){field(v,"name",100,true);field(v,"phone",50,true);Long id=id(v);if((id==null?mapper.addContact(v):mapper.editContact(v))!=1)throw new ServiceException("联系人不存在或已删除");return id(v);}
 public void removeContact(Long id){if(mapper.removeContact(id)!=1)throw new ServiceException("联系人不存在");}
}
