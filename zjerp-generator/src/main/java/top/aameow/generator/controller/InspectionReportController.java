package top.aameow.generator.controller;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.annotation.Log;
import top.aameow.common.enums.BusinessType;
import top.aameow.generator.service.InspectionReportService;
@RestController @RequestMapping("/inspection-reports")
public class InspectionReportController extends BaseController {
 @Autowired private InspectionReportService service;
 @GetMapping("/{projectId}") @PreAuthorize("@ss.hasPermi('management:report:list') and @ss.hasPermi('management:project:query')")
 public AjaxResult list(@PathVariable Long projectId){return success(service.list(projectId));}
 @PostMapping("/preview") @PreAuthorize("@ss.hasPermi('management:report:generate') and @ss.hasPermi('management:project:query') and @ss.hasPermi('management:credential:list')")
 public ResponseEntity<byte[]> preview(@RequestBody Map<String,Object> v) throws Exception {return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(service.preview(v));}
 @PostMapping @PreAuthorize("@ss.hasPermi('management:report:generate') and @ss.hasPermi('management:project:query') and @ss.hasPermi('management:credential:list')") @Log(title="检测报告生成",businessType=BusinessType.INSERT)
 public AjaxResult create(@RequestBody Map<String,Object> v) throws Exception{return success(service.create(v));}
}
