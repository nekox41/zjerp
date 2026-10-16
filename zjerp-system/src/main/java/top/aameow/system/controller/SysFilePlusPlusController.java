package top.aameow.system.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
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
public class SysFilePlusPlusController extends BaseController {

    @Autowired
    private ISysFilePlusService SysFilePlusService;

    /**
     * 文件上传
     * 参数：file（必填）、bizType（可选）、bizId（可选）
     */
    @PostMapping("/upload")
    public AjaxResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizId", required = false) Long bizId) {
        try {
            SysFilePlus SysFilePlus = SysFilePlusService.upload(file, bizType, bizId);
            return AjaxResult.success(SysFilePlus);
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 按 fileId 查询 */
    @GetMapping("/{fileId}")
    public AjaxResult getInfo(@PathVariable Long fileId) {
        return AjaxResult.success(SysFilePlusService.getById(fileId));
    }

    /** 按业务查询（合同、工程师资料等） */
    @GetMapping("/listByBiz")
    public AjaxResult listByBiz(@RequestParam String bizType,
                                @RequestParam Long bizId) {
        return AjaxResult.success(SysFilePlusService.listByBiz(bizType, bizId));
    }

    /** 按 ID 批量查询（前端 v-model 回显） */
    @GetMapping("/listByIds")
    public AjaxResult listByIds(@RequestParam String ids) {
        List<Long> fileIds = Arrays.stream(ids.split(","))
                .filter(StringUtils::isNotBlank)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return AjaxResult.success(SysFilePlusService.listByIds(fileIds));
    }

    /** 删除文件 */
    @Log(title = "文件管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{fileId}")
    public AjaxResult remove(@PathVariable Long fileId) {
        SysFilePlusService.delete(fileId);
        return AjaxResult.success();
    }

    /** 业务保存后回填 biz_id（前端在业务保存成功时调一次） */
    @PostMapping("/bindBiz")
    public AjaxResult bindBiz(@RequestParam String ids,
                              @RequestParam String bizType,
                              @RequestParam Long bizId) {
        List<Long> fileIds = Arrays.stream(ids.split(","))
                .filter(StringUtils::isNotBlank)
                .map(Long::valueOf)
                .collect(Collectors.toList());
        SysFilePlusService.bindBiz(fileIds, bizType, bizId);
        return AjaxResult.success();
    }
}