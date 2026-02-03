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
import top.aameow.project.domain.MaintainProject;
import top.aameow.project.service.IMaintainProjectService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 维保项目Controller
 * 
 * @author aa
 * @date 2026-01-24
 */
@RestController
@RequestMapping("/project/maintain")
public class MaintainProjectController extends BaseController
{
    @Autowired
    private IMaintainProjectService maintainProjectService;

    /**
     * 查询维保项目列表
     */
    @PreAuthorize("@ss.hasPermi('project:maintain:list')")
    @GetMapping("/list")
    public TableDataInfo list(MaintainProject maintainProject)
    {
        startPage();
        List<MaintainProject> list = maintainProjectService.selectMaintainProjectList(maintainProject);
        return getDataTable(list);
    }

    /**
     * 导出维保项目列表
     */
    @PreAuthorize("@ss.hasPermi('project:maintain:export')")
    @Log(title = "维保项目", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, MaintainProject maintainProject)
    {
        List<MaintainProject> list = maintainProjectService.selectMaintainProjectList(maintainProject);
        ExcelUtil<MaintainProject> util = new ExcelUtil<MaintainProject>(MaintainProject.class);
        util.exportExcel(response, list, "维保项目数据");
    }

    /**
     * 获取维保项目详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:maintain:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(maintainProjectService.selectMaintainProjectById(id));
    }

    /**
     * 新增维保项目
     */
    @PreAuthorize("@ss.hasPermi('project:maintain:add')")
    @Log(title = "维保项目", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody MaintainProject maintainProject)
    {
        return toAjax(maintainProjectService.insertMaintainProject(maintainProject));
    }

    /**
     * 修改维保项目
     */
    @PreAuthorize("@ss.hasPermi('project:maintain:edit')")
    @Log(title = "维保项目", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody MaintainProject maintainProject)
    {
        return toAjax(maintainProjectService.updateMaintainProject(maintainProject));
    }

    /**
     * 删除维保项目
     */
    @PreAuthorize("@ss.hasPermi('project:maintain:remove')")
    @Log(title = "维保项目", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(maintainProjectService.deleteMaintainProjectByIds(ids));
    }
}
