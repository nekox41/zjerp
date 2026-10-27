package top.aameow.management.migrate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.aameow.common.core.domain.AjaxResult;

@RestController
@RequestMapping("/system/file/migrate")
public class FileMigrateController
{
    @Autowired private FileMigrateService migration;

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping
    public AjaxResult migrate() { return AjaxResult.success(migration.migrate()); }
}
