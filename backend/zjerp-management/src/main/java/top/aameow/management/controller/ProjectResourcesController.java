package top.aameow.management.controller;
import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.security.access.prepost.PreAuthorize;import top.aameow.common.core.controller.BaseController;import top.aameow.common.core.domain.AjaxResult;import top.aameow.common.core.page.TableDataInfo;import top.aameow.management.service.ProjectResourcesService;
@RestController @RequestMapping("/management/work-project/{projectId}") public class ProjectResourcesController extends BaseController {
 @Autowired private ProjectResourcesService service;
 @GetMapping("/documents") @PreAuthorize("@ss.hasPermi('management:project:query')") public TableDataInfo documents(@PathVariable Long projectId){service.project(projectId);startPage();return getDataTable(service.data().documents(projectId));}
 @PostMapping("/documents") @PreAuthorize("@ss.hasPermi('management:project:edit')") public AjaxResult document(@PathVariable Long projectId,@RequestBody Map<String,Object> v){service.document(projectId,v);return success();}
 @DeleteMapping("/documents/{id}") @PreAuthorize("@ss.hasPermi('management:project:edit')") public AjaxResult remove(@PathVariable Long projectId,@PathVariable Long id){service.remove(projectId,id);return success();}
 @GetMapping("/materials") @PreAuthorize("@ss.hasPermi('management:project:query')") public AjaxResult materials(@PathVariable Long projectId){service.maintenance(projectId);Map<String,Object> v=service.data().materials(projectId);return success(v==null?Map.of("version",0):v);}
 @PutMapping("/materials") @PreAuthorize("@ss.hasPermi('management:project:edit')") public AjaxResult material(@PathVariable Long projectId,@RequestBody Map<String,Object> v){service.material(projectId,v);return success();}
 @GetMapping("/material-options") @PreAuthorize("@ss.hasPermi('management:project:edit')") public AjaxResult options(@PathVariable Long projectId,@RequestParam(defaultValue="") String name){service.maintenance(projectId);return success(service.data().materialOptions(name));}
 @GetMapping("/card-options") @PreAuthorize("@ss.hasPermi('management:project:edit')") public AjaxResult cards(@PathVariable Long projectId,@RequestParam(defaultValue="") String name){service.maintenance(projectId);return success(service.data().cardOptions(name));}
}
