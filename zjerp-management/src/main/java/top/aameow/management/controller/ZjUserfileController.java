package top.aameow.management.controller;

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
import top.aameow.management.domain.UserFileAndUser;
import top.aameow.management.domain.ZjUserfile;
import top.aameow.management.service.IZjUserfileService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 资料附件Controller
 * 
 * @author aa
 * @date 2025-11-11
 */
@RestController
@RequestMapping("/management/userfile")
public class ZjUserfileController extends BaseController
{
    @Autowired
    private IZjUserfileService zjUserfileService;

    /**
     * 查询资料附件列表
     */
    @PreAuthorize("@ss.hasPermi('management:userfile:list')")
    @GetMapping("/list")
    public TableDataInfo list(ZjUserfile zjUserfile)
    {
        startPage();
//        List<ZjUserfile> list = zjUserfileService.selectZjUserfileList(zjUserfile);
        List<UserFileAndUser> list = zjUserfileService.selectUserFileAndUserList(zjUserfile);
        return getDataTable(list);
    }

    /**
     * 导出资料附件列表
     */
    @PreAuthorize("@ss.hasPermi('management:userfile:export')")
    @Log(title = "资料附件", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ZjUserfile zjUserfile)
    {
        List<ZjUserfile> list = zjUserfileService.selectZjUserfileList(zjUserfile);
        ExcelUtil<ZjUserfile> util = new ExcelUtil<ZjUserfile>(ZjUserfile.class);
        util.exportExcel(response, list, "资料附件数据");
    }

    /**
     * 获取资料附件详细信息
     */
    @PreAuthorize("@ss.hasPermi('management:userfile:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(zjUserfileService.selectZjUserfileById(id));
    }

    /**
     * 新增资料附件
     */
    @PreAuthorize("@ss.hasPermi('management:userfile:add')")
    @Log(title = "资料附件", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ZjUserfile zjUserfile)
    {
        zjUserfile.setCreateBy(getNickname());
        zjUserfile.setCreateTime(DateUtils.getNowDate());
        return toAjax(zjUserfileService.insertZjUserfile(zjUserfile));
    }

    /**
     * 修改资料附件
     */
    @PreAuthorize("@ss.hasPermi('management:userfile:edit')")
    @Log(title = "资料附件", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ZjUserfile zjUserfile)
    {
        zjUserfile.setUpdateBy(getNickname());
        zjUserfile.setUpdateTime(DateUtils.getNowDate());
        return toAjax(zjUserfileService.updateZjUserfile(zjUserfile));
    }

    /**
     * 删除资料附件
     */
    @PreAuthorize("@ss.hasPermi('management:userfile:remove')")
    @Log(title = "资料附件", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(zjUserfileService.deleteZjUserfileByIds(ids));
    }
}
