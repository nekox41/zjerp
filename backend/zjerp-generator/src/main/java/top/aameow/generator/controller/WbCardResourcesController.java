package top.aameow.generator.controller;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.aameow.generator.service.WbCardResourcesService;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.core.page.TableDataInfo;
import top.aameow.common.annotation.Log;
import top.aameow.common.enums.BusinessType;
@RestController @RequestMapping("/wbcard/resources")
public class WbCardResourcesController extends BaseController {
 @Autowired private WbCardResourcesService service;
 @GetMapping("/materials") @PreAuthorize("@ss.hasPermi('management:wbcard:materials:list')")
 public TableDataInfo materials(@RequestParam(defaultValue="") String name){startPage();return getDataTable(service.data().materials(name));}
 @PostMapping("/materials") @PreAuthorize("@ss.hasPermi('management:wbcard:materials:edit')") @Log(title="维保附带资料",businessType=BusinessType.UPDATE)
 public AjaxResult material(@RequestBody Map<String,Object> v) throws Exception{return success(service.saveMaterial(v));}
 @DeleteMapping("/materials/{id}") @PreAuthorize("@ss.hasPermi('management:wbcard:materials:edit')") @Log(title="维保附带资料",businessType=BusinessType.DELETE)
 public AjaxResult removeMaterial(@PathVariable Long id){service.removeMaterial(id);return success();}
 @GetMapping("/contacts") @PreAuthorize("@ss.hasPermi('management:wbcard:contacts:list') or @ss.hasPermi('management:wbcard:add')")
 public TableDataInfo contacts(@RequestParam(defaultValue="") String name){startPage();return getDataTable(service.data().contacts(name));}
 @PostMapping("/contacts") @PreAuthorize("@ss.hasPermi('management:wbcard:contacts:edit')") @Log(title="维保联系人",businessType=BusinessType.UPDATE)
 public AjaxResult contact(@RequestBody Map<String,Object> v){return success(service.saveContact(v));}
 @DeleteMapping("/contacts/{id}") @PreAuthorize("@ss.hasPermi('management:wbcard:contacts:edit')") @Log(title="维保联系人",businessType=BusinessType.DELETE)
 public AjaxResult removeContact(@PathVariable Long id){service.removeContact(id);return success();}
}
