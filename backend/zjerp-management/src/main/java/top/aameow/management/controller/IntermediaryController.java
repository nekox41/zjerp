package top.aameow.management.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.core.page.TableDataInfo;
import top.aameow.common.exception.ServiceException;
import top.aameow.management.domain.Intermediary;
import top.aameow.management.mapper.IntermediaryMapper;
@RestController @RequestMapping("/management/intermediary")
public class IntermediaryController extends BaseController {
 @Autowired private IntermediaryMapper mapper;
 @GetMapping("/list") @PreAuthorize("@ss.hasPermi('management:intermediary:list')")
 public TableDataInfo list(@RequestParam(defaultValue="") String name){ startPage();return getDataTable(mapper.list(name)); }
 @GetMapping("/options") @PreAuthorize("@ss.hasPermi('management:intermediary:list') or @ss.hasPermi('management:contract:edit') or @ss.hasPermi('management:contract:add')")
 public TableDataInfo options(@RequestParam(defaultValue="") String name){startPage();return getDataTable(mapper.list(name));}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('management:intermediary:list')")
 public AjaxResult get(@PathVariable Long id){return success(mapper.get(id));}
 private void validate(Intermediary v){
  if(v.getName()==null || v.getName().isBlank() || v.getName().length()>100)throw new ServiceException("姓名须为1至100字符");
  if(v.getPhone()!=null && v.getPhone().length()>50 || v.getCompany()!=null && v.getCompany().length()>200 || v.getEmail()!=null && v.getEmail().length()>200 || v.getRemark()!=null && v.getRemark().length()>2000)throw new ServiceException("信息超出长度限制");
 }
 @PostMapping @PreAuthorize("@ss.hasPermi('management:intermediary:edit')")
 @top.aameow.common.annotation.Log(title="中间人管理",businessType=top.aameow.common.enums.BusinessType.INSERT)
 public AjaxResult add(@RequestBody Intermediary v){validate(v);mapper.add(v);return success(v);}
 @PutMapping @PreAuthorize("@ss.hasPermi('management:intermediary:edit')")
 @top.aameow.common.annotation.Log(title="中间人管理",businessType=top.aameow.common.enums.BusinessType.UPDATE)
 public AjaxResult edit(@RequestBody Intermediary v){validate(v);return toAjax(mapper.edit(v));}
 @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermi('management:intermediary:edit')")
 @top.aameow.common.annotation.Log(title="中间人管理",businessType=top.aameow.common.enums.BusinessType.DELETE)
 public AjaxResult remove(@PathVariable Long id){
  try { return toAjax(mapper.remove(id)); }
  catch(org.springframework.dao.DataIntegrityViolationException e){throw new ServiceException("该中间人已被合同引用，请先调整合同归属后再删除");}
 }
}
