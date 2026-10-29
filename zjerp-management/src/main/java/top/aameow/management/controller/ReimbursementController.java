package top.aameow.management.controller;
import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.security.access.prepost.PreAuthorize;
import top.aameow.common.annotation.Anonymous;import top.aameow.common.annotation.Log;import top.aameow.common.enums.BusinessType;import top.aameow.common.core.controller.BaseController;import top.aameow.common.core.domain.AjaxResult;import top.aameow.common.core.page.TableDataInfo;import top.aameow.management.service.ReimbursementService;
@RestController @RequestMapping("/finance")
public class ReimbursementController extends BaseController {
 @Autowired private ReimbursementService service;
 @GetMapping("/reimbursements") @PreAuthorize("@ss.hasPermi('zhongji:finance:list')") public TableDataInfo list(@RequestParam Map<String,Object> q){service.filter(q);startPage();return getDataTable(service.data().list(q));}
 @GetMapping("/reimbursements/{id}") @PreAuthorize("@ss.hasPermi('zhongji:finance:list')") public AjaxResult get(@PathVariable Long id){return success(service.data().get(id));}
 @GetMapping("/analysis") @PreAuthorize("@ss.hasPermi('zhongji:finance:list')") public AjaxResult analysis(@RequestParam Map<String,Object> q){service.filter(q);return success(Map.of("summary",service.data().summary(q),"months",service.data().months(q),"people",service.data().people(q),"projects",service.data().projectsSummary(q)));}
 @PostMapping(value="/reimbursements",consumes="multipart/form-data") @PreAuthorize("@ss.hasPermi('zhongji:finance:add')") @Log(title="报账录入",businessType=BusinessType.INSERT)
 public AjaxResult submit(@RequestParam Map<String,Object> v,@RequestParam(required=false) List<MultipartFile> invoices) throws Exception{return success(Map.of("receipt",service.submit(v,invoices,false)));}
 @Anonymous @PostMapping(value="/public/submit",consumes="multipart/form-data")
 public AjaxResult mobile(@RequestParam Map<String,Object> v,@RequestParam(required=false) List<MultipartFile> invoices) throws Exception{return success(Map.of("receipt",service.submit(v,invoices,true)));}
 @GetMapping("/users") @PreAuthorize("@ss.hasPermi('zhongji:finance:add') or @ss.hasPermi('zhongji:finance:list')") public AjaxResult users(@RequestParam(defaultValue="") String name){return success(service.data().users(name));}
 @GetMapping("/projects") @PreAuthorize("@ss.hasPermi('zhongji:finance:add') or @ss.hasPermi('zhongji:finance:list')") public AjaxResult projects(@RequestParam(defaultValue="") String name){return success(service.data().projects(name));}
 @Anonymous @GetMapping("/public/users") public AjaxResult publicUsers(@RequestParam(defaultValue="") String name){return success(name.trim().length()<2?List.of():service.data().users(name.trim()));}
 @Anonymous @GetMapping("/public/projects") public AjaxResult publicProjects(@RequestParam(defaultValue="") String name){return success(name.trim().length()<2?List.of():service.data().projects(name.trim()));}
 @PutMapping("/reimbursements/{id}/status") @PreAuthorize("@ss.hasPermi('zhongji:finance:edit')") @Log(title="报账状态",businessType=BusinessType.UPDATE) public AjaxResult status(@PathVariable Long id,@RequestBody Map<String,Object> v){service.status(id,v);return success();}
}
