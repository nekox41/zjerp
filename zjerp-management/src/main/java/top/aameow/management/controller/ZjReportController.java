package top.aameow.management.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.aameow.common.annotation.Log;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.enums.BusinessType;
import top.aameow.common.utils.file.FileUploadUtils;
import top.aameow.common.utils.file.FileUtils;
import top.aameow.common.utils.uuid.Seq;
import top.aameow.framework.config.ServerConfig;
import top.aameow.management.domain.ZjReport;
import top.aameow.management.service.IZjReportService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 报告管理Controller
 * 
 * @author aa
 * @date 2025-05-13
 */
@RestController
@RequestMapping("/management/report")
public class ZjReportController extends BaseController
{
    @Autowired
    private IZjReportService zjReportService;

    @Autowired
    private ServerConfig serverConfig;
    /**
     * 查询报告管理列表
     */
    @PreAuthorize("@ss.hasPermi('management:report:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(defaultValue = "updateTime") String sortBy, @RequestParam(defaultValue = "DESC") String orderBy, ZjReport zjReport)
    {
        startPage();
        List<ZjReport> list = zjReportService.selectZjReportList(sortBy, orderBy, zjReport);
        return getDataTable(list);
    }

    /**
     * 导出报告管理列表
     */
    @PreAuthorize("@ss.hasPermi('management:report:export')")
    @Log(title = "报告管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ZjReport zjReport)
    {
        List<ZjReport> list = zjReportService.selectZjReportList("id", "ASC", zjReport);
        ExcelUtil<ZjReport> util = new ExcelUtil<ZjReport>(ZjReport.class);
        util.exportExcel(response, list, "报告管理数据");
    }

    /**
     * 获取报告管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('management:report:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(zjReportService.selectZjReportById(id));
    }

    /**
     * 新增报告管理
     */
    @PreAuthorize("@ss.hasPermi('management:report:add')")
    @Log(title = "报告管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ZjReport zjReport)
    {
        zjReport.setCreateBy(getNickname());
        return toAjax(zjReportService.insertZjReport(zjReport));
    }

    /**
     * 修改报告管理
     */
    @PreAuthorize("@ss.hasPermi('management:report:edit')")
    @Log(title = "报告管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ZjReport zjReport)
    {
        zjReport.setUpdateBy(getNickname());
        return toAjax(zjReportService.updateZjReport(zjReport));
    }

    /**
     * 删除报告管理
     */
    @PreAuthorize("@ss.hasPermi('management:report:remove')")
    @Log(title = "报告管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(zjReportService.deleteZjReportByIds(ids));
    }

    @Log(title = "上传报告", businessType = BusinessType.OTHER)
    @PostMapping("/upload")
    public AjaxResult uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("isFinal") boolean isFinal) throws Exception {
        try {
            // 上传文件路径
            String filePath = RuoYiConfig.getUploadPath();
            // 根据 isFinal 参数选择序列类型
            String seqType = isFinal ? Seq.finalReportSeqType : Seq.reportSeqType;
            // 上传并返回新文件名称
            String fileName = FileUploadUtils.upload(filePath, file, seqType);
            String url = serverConfig.getUrl() + fileName;
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

}
