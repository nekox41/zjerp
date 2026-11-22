package top.aameow.web.controller.system;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;

/**
 * Admin-only entry point for the embedded ERP migration center.
 *
 * This endpoint is intentionally read-only. Import, upload and execution are
 * disabled until persistent jobs, staging isolation, and the target schema
 * have been implemented and verified.
 */
@RestController
@RequestMapping("/admin/migrations")
@PreAuthorize("isAuthenticated() and T(top.aameow.common.utils.SecurityUtils).isAdmin()")
public class MigrationController extends BaseController
{
    @GetMapping("/capabilities")
    public AjaxResult capabilities()
    {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("embedded", true);
        capabilities.put("status", "FOUNDATION_ONLY");
        capabilities.put("sourceUploadEnabled", false);
        capabilities.put("scanEnabled", false);
        capabilities.put("dryRunEnabled", false);
        capabilities.put("executeEnabled", false);
        capabilities.put("resumeEnabled", false);
        capabilities.put("reason", "Migration execution is disabled until staging, persisted jobs, mapping, and reconciliation are verified.");
        return success(capabilities);
    }
}
