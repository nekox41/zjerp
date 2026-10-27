package top.aameow.system.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import top.aameow.common.core.page.TableDataInfo;
import top.aameow.system.mapper.SysFilePlusMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.aameow.common.annotation.Log;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.enums.BusinessType;
import top.aameow.common.utils.StringUtils;
import top.aameow.system.domain.SysFilePlus;
import top.aameow.system.service.ISysFilePlusService;

@RestController
@RequestMapping("/system/file")
public class SysFileController extends BaseController {

    @Autowired
    private ISysFilePlusService fileService;

    @Autowired
    private SysFilePlusMapper fileMapper;

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/list")
    public TableDataInfo list(SysFilePlus query)
    {
        startPage();
        return getDataTable(fileMapper.selectList(query));
    }

    @PostMapping("/byUrls")
    public AjaxResult byUrls(@RequestBody List<String> urls)
    {
        if (urls == null || urls.size() > 100) return AjaxResult.error("一次最多查询100个文件");
        return AjaxResult.success(urls.isEmpty() ? java.util.Collections.emptyList() : fileMapper.selectByUrls(urls));
    }

    /**
     * 文件上传
     * 上传暂存文件；业务关联由业务保存接口完成。
     */
    @PostMapping("/upload")
    public AjaxResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizId", required = false) Long bizId) {
        try {
            SysFilePlus stored = fileService.upload(file, bizType, bizId);
            return AjaxResult.success(stored);
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 按 fileId 查询 */
    @GetMapping("/{fileId}")
    public AjaxResult getInfo(@PathVariable Long fileId) {
        return AjaxResult.success(fileService.getById(fileId));
    }

    /** 按业务查询 */
    @GetMapping("/listByBiz")
    public AjaxResult listByBiz(@RequestParam String bizType,
                                @RequestParam Long bizId) {
        return AjaxResult.success(fileService.listByBiz(bizType, bizId));
    }

    /** 按 ID 批量查询（前端 v-model 回显） */
    @GetMapping("/listByIds")
    public AjaxResult listByIds(@RequestParam String ids) {
        List<Long> fileIds = Arrays.stream(ids.split(","))
                .filter(StringUtils::isNotBlank)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return AjaxResult.success(fileService.listByIds(fileIds));
    }

    /** 删除文件 */
    @Log(title = "文件管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{fileId}")
    public AjaxResult remove(@PathVariable Long fileId) {
        fileService.delete(fileId);
        return AjaxResult.success();
    }

    /** 旧客户端兼容入口；拒绝绕过业务权限直接绑定 */
    @PostMapping("/bindBiz")
    public AjaxResult bindBiz(@RequestParam String ids,
                              @RequestParam String bizType,
                              @RequestParam Long bizId) {
        List<Long> fileIds = Arrays.stream(ids.split(","))
                .filter(StringUtils::isNotBlank)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        fileService.bindBiz(fileIds, bizType, bizId);
        return AjaxResult.success();
    }
}