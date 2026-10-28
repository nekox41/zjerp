package top.aameow.system.controller;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.exception.ServiceException;
import top.aameow.system.domain.UserMaterial;
import top.aameow.system.mapper.UserMaterialMapper;
import top.aameow.system.service.ISysUserService;
import top.aameow.system.service.ISysFilePlusService;
@RestController
@RequestMapping("/system/user/{userId}/materials")
public class UserMaterialController {
 @Autowired private UserMaterialMapper mapper;
 @Autowired private ISysUserService users;
 @Autowired private ISysFilePlusService files;
 private void check(Long id) { if(users.selectUserById(id)==null) throw new ServiceException("用户不存在"); }
 @GetMapping
 @PreAuthorize("@ss.hasPermi('system:user:query')")
 public AjaxResult list(@PathVariable Long userId,@RequestParam(required=false) String key) {
  check(userId); List<UserMaterial> values=mapper.list(userId);
  if(key!=null) values.removeIf(v->!key.equals(v.getMaterialKey()));
  return AjaxResult.success(values);
 }
 @PutMapping
 @Transactional(rollbackFor=Exception.class)
 @PreAuthorize("@ss.hasPermi('system:user:edit')")
 public AjaxResult save(@PathVariable Long userId,@RequestBody UserMaterial value) throws Exception {
  check(userId);
  if(value.getMaterialKey()==null || value.getMaterialKey().trim().isEmpty() || value.getMaterialKey().length()>100) throw new ServiceException("资料 key 必须为1至100个字符");
  if(!Arrays.asList("string","png","jpg","pdf","file").contains(value.getValueType())) throw new ServiceException("不支持的资料类型");
  if(value.getContent()==null || value.getContent().length()>4000) throw new ServiceException("资料内容不能为空且不能超过4000字符");
  if(!"string".equals(value.getValueType())) {
   top.aameow.system.domain.SysFilePlus file=files.getByUrl(value.getContent());
   if(file==null || "2".equals(file.getDelFlag())) throw new ServiceException("请选择已上传的文件");
   String ext=file.getFileExt()==null?"":file.getFileExt().toLowerCase(java.util.Locale.ROOT);
   if(!"file".equals(value.getValueType()) && !value.getValueType().equals(ext) && !("jpg".equals(value.getValueType()) && "jpeg".equals(ext))) throw new ServiceException("文件格式与资料类型不一致");
  }
  value.setUserId(userId); value.setMaterialKey(value.getMaterialKey().trim()); mapper.save(value);
  List<String> urls=new ArrayList<>();
  for(UserMaterial item:mapper.list(userId)) if(!"string".equals(item.getValueType()) && !item.getContent().isEmpty()) urls.add(item.getContent());
  files.syncBiz(String.join(",",urls),"user_material",userId);
  return AjaxResult.success();
 }
 @DeleteMapping
 @Transactional(rollbackFor=Exception.class)
 @PreAuthorize("@ss.hasPermi('system:user:edit')")
 public AjaxResult delete(@PathVariable Long userId,@RequestParam String key) throws Exception {
  check(userId); mapper.delete(userId,key);
  List<String> urls=new ArrayList<>();
  for(UserMaterial item:mapper.list(userId)) if(!"string".equals(item.getValueType()) && !item.getContent().isEmpty()) urls.add(item.getContent());
  files.syncBiz(String.join(",",urls),"user_material",userId); return AjaxResult.success();
 }
}
