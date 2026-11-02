package top.aameow.management.controller;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.management.service.ReportCredentialService;
@RestController @RequestMapping("/management/report-credentials")
public class ReportCredentialController extends BaseController {
 @Autowired private ReportCredentialService service;
 @GetMapping @PreAuthorize("@ss.hasPermi('management:credential:list')")
 public AjaxResult list(){return success(service.list());}
 @PostMapping @PreAuthorize("@ss.hasPermi('management:credential:edit')")
 @top.aameow.common.annotation.Log(title="资质资料保存",businessType=top.aameow.common.enums.BusinessType.UPDATE)
 public AjaxResult save(@RequestBody Map<String,Object> v){service.save(v);return success();}
}