package top.aameow.web.controller.system;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import top.aameow.migration.StagedSourceStore;
import top.aameow.migration.StagedSourceStore.StagedSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    @Autowired
    private StagedSourceStore stagedSourceStore;

    @GetMapping("/sources")
    public AjaxResult listSources()
    {
        try
        {
            List<StagedSource> sources = stagedSourceStore.list();
            return success(sources);
        }
        catch (IOException e)
        {
            logger.error("Unable to list staged migration sources", e);
            return error("无法读取迁移来源，请检查服务器存储状态");
        }
    }

    @PostMapping(value = "/sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult uploadSource(@RequestParam("file") MultipartFile file)
    {
        try
        {
            StagedSource source = stagedSourceStore.stage(file);
            return AjaxResult.success("数据库备份已安全暂存，尚未执行或导入", source);
        }
        catch (IllegalArgumentException e)
        {
            return error(e.getMessage());
        }
        catch (IOException e)
        {
            logger.error("Unable to stage migration source", e);
            return error("数据库备份暂存失败，请检查服务器私有存储空间");
        }
    }
    @GetMapping("/capabilities")
    public AjaxResult capabilities()
    {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("embedded", true);
        capabilities.put("status", "SOURCE_STAGING_ENABLED");
        capabilities.put("sourceUploadEnabled", true);
        capabilities.put("scanEnabled", false);
        capabilities.put("dryRunEnabled", false);
        capabilities.put("executeEnabled", false);
        capabilities.put("resumeEnabled", false);
        capabilities.put("reason", "SQL dumps can be staged privately. Scanning, dry-run, execution, and resume remain disabled until their safety controls are implemented.");
        return success(capabilities);
    }
}
