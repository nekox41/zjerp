package top.aameow.project.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aameow.common.annotation.Log;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.enums.BusinessType;
import top.aameow.common.utils.DateUtils;
import top.aameow.project.domain.ProjectCode;
import top.aameow.project.service.IProjectCodeService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 编号申请Controller
 * 
 * @author aa
 * @date 2026-02-03
 */
@RestController
@RequestMapping("/project/code")
public class ProjectCodeController extends BaseController
{
    @Autowired
    private IProjectCodeService projectCodeService;

    /**
     * 查询编号申请列表
     */
    @PreAuthorize("@ss.hasPermi('project:code:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProjectCode projectCode)
    {
        startPage();
        List<ProjectCode> list = projectCodeService.selectProjectCodeList(projectCode);
        return getDataTable(list);
    }

    /**
     * 导出编号申请列表
     */
    @PreAuthorize("@ss.hasPermi('project:code:export')")
    @Log(title = "编号申请", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProjectCode projectCode)
    {
        List<ProjectCode> list = projectCodeService.selectProjectCodeList(projectCode);
        ExcelUtil<ProjectCode> util = new ExcelUtil<ProjectCode>(ProjectCode.class);
        util.exportExcel(response, list, "编号申请数据");
    }

    /**
     * 获取编号申请详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:code:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(projectCodeService.selectProjectCodeById(id));
    }

    /**
     * 新增编号申请
     */
    @PreAuthorize("@ss.hasPermi('project:code:add')")
    @Log(title = "编号申请", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProjectCode projectCode)
    {
        // insert 会调用 genCode方法生成对应的编号并为 projectCode 设置
        projectCodeService.insertProjectCode(projectCode);
        // 返回 projectCode 让其展示
        return new AjaxResult(200, "申请成功.", projectCode);
    }

    /**
     * 修改编号申请
     */
    @PreAuthorize("@ss.hasPermi('project:code:edit')")
    @Log(title = "编号申请", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProjectCode projectCode)
    {
        return toAjax(projectCodeService.updateProjectCode(projectCode));
    }

    /**
     * 删除编号申请
     */
    @PreAuthorize("@ss.hasPermi('project:code:remove')")
    @Log(title = "编号申请", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(projectCodeService.deleteProjectCodeByIds(ids));
    }
}
