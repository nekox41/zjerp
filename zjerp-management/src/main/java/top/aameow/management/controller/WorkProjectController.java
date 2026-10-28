package top.aameow.management.controller;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.core.page.TableDataInfo;
import top.aameow.management.service.WorkProjectService;
import top.aameow.common.annotation.Log;
import top.aameow.common.enums.BusinessType;
@RestController @RequestMapping("/management/work-project")
public class WorkProjectController extends BaseController {
 @Autowired private WorkProjectService service;
 @GetMapping("/list") @PreAuthorize("@ss.hasPermi('management:project:list')")
 public TableDataInfo list(@RequestParam Map<String,Object> q){if(!WorkProjectService.text(q,"projectType").isEmpty())service.type(WorkProjectService.text(q,"projectType"));startPage();return getDataTable(service.data().list(q));}
 @GetMapping("/contracts") @PreAuthorize("@ss.hasPermi('management:project:add') or @ss.hasPermi('management:project:edit')")
 public TableDataInfo contracts(@RequestParam String projectType,@RequestParam(defaultValue="") String name){service.type(projectType);startPage();return getDataTable(service.data().contracts(Map.of("projectType",projectType,"name",name)));}
 @GetMapping("/users") @PreAuthorize("@ss.hasPermi('management:project:add') or @ss.hasPermi('management:project:edit')")
 public TableDataInfo users(@RequestParam(defaultValue="") String name){startPage();return getDataTable(service.data().users(name));}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('management:project:query')") public AjaxResult get(@PathVariable Long id){return success(service.get(id));}
 @PostMapping @PreAuthorize("@ss.hasPermi('management:project:add')") @Log(title="项目创建与编号申请",businessType=BusinessType.INSERT)
 public AjaxResult add(@RequestBody Map<String,Object> v){return success(service.create(v));}
 @PostMapping("/{id}/apply-number") @PreAuthorize("@ss.hasPermi('management:project:edit')") @Log(title="历史项目正式编号申请",businessType=BusinessType.UPDATE)
 public AjaxResult applyNumber(@PathVariable Long id){return success(service.applyNumber(id));}
 @PutMapping("/{id}/section/{section}") @PreAuthorize("@ss.hasPermi('management:project:edit')") @Log(title="项目页面保存",businessType=BusinessType.UPDATE)
 public AjaxResult section(@PathVariable Long id,@PathVariable String section,@RequestBody Map<String,Object> v){service.saveSection(id,section,v);return success();}
 @GetMapping("/{id}/issues") @PreAuthorize("@ss.hasPermi('management:project:query')") public TableDataInfo issues(@PathVariable Long id){service.get(id);startPage();return getDataTable(service.data().issues(id));}
 @PostMapping("/{id}/issues") @PreAuthorize("@ss.hasPermi('management:project:edit')") @Log(title="项目问题",businessType=BusinessType.UPDATE)
 public AjaxResult issue(@PathVariable Long id,@RequestBody Map<String,Object> v){service.issue(id,v);return success();}
 @DeleteMapping("/{id}/issues/{issueId}") @PreAuthorize("@ss.hasPermi('management:project:edit')") @Log(title="项目问题",businessType=BusinessType.DELETE)
 public AjaxResult removeIssue(@PathVariable Long id,@PathVariable Long issueId){service.removeIssue(id,issueId);return success();}
 @GetMapping("/{id}/plans") @PreAuthorize("@ss.hasPermi('management:project:query')") public TableDataInfo plans(@PathVariable Long id){service.get(id);startPage();return getDataTable(service.data().plans(id));}
 @PostMapping("/{id}/plans") @PreAuthorize("@ss.hasPermi('management:project:edit')") @Log(title="项目计划",businessType=BusinessType.UPDATE)
 public AjaxResult plan(@PathVariable Long id,@RequestBody Map<String,Object> v){service.plan(id,v);return success();}
 @DeleteMapping("/{id}/plans/{planId}") @PreAuthorize("@ss.hasPermi('management:project:edit')") @Log(title="项目计划",businessType=BusinessType.DELETE)
 public AjaxResult removePlan(@PathVariable Long id,@PathVariable Long planId){service.removePlan(id,planId);return success();}
 @GetMapping("/{id}/expenses") @PreAuthorize("@ss.hasPermi('management:project:expenses')") public AjaxResult expenses(@PathVariable Long id){service.get(id);startPage();TableDataInfo table=getDataTable(service.data().expenses(id));AjaxResult r=success();r.put("rows",table.getRows());r.put("total",table.getTotal());r.put("summary",service.data().expenseSummary(id));r.put("legacySummary",service.data().expenseLegacy(id));r.put("users",service.data().expenseUsers(id));return r;}
}
