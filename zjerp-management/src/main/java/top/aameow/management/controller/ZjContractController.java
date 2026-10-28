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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aameow.common.annotation.Anonymous;
import top.aameow.common.annotation.Log;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.enums.BusinessType;
import top.aameow.common.utils.DateUtils;
import top.aameow.management.domain.ZjContract;
import top.aameow.management.service.IZjContractService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 合同管理Controller
 * 
 * @author ruoyi
 * @date 2025-05-09
 */
@RestController
@RequestMapping("/management/contract")
public class ZjContractController extends BaseController
{
    @Autowired
    private IZjContractService zjContractService;

    /**
     * 查询合同管理列表
     */
    @PreAuthorize("@ss.hasPermi('management:contract:list')")
    @GetMapping("/list")
    public TableDataInfo list(ZjContract zjContract)
    {
        startPage();
        List<ZjContract> list = zjContractService.selectZjContractList(zjContract);
        return getDataTable(list);
    }

    /**
     * 导出合同管理列表
     */
    @PreAuthorize("@ss.hasPermi('management:contract:export')")
    @Log(title = "合同管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ZjContract zjContract)
    {
        List<ZjContract> list = zjContractService.selectZjContractList(zjContract);
        ExcelUtil<ZjContract> util = new ExcelUtil<ZjContract>(ZjContract.class);
        util.exportExcel(response, list, "合同管理数据");
    }

    /**
     * 获取合同管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('management:contract:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(zjContractService.selectZjContractById(id));
    }

    /**
     * 新增合同管理
     */
    @PreAuthorize("@ss.hasPermi('management:contract:add')")
    @Log(title = "合同管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ZjContract zjContract)
    {
        zjContract.setCreateBy(getNickname());
        zjContract.setUpdateBy(getNickname());
        zjContract.setCreateTime(DateUtils.getNowDate());
        zjContract.setUpdateTime(DateUtils.getNowDate());
        return toAjax(zjContractService.insertZjContract(zjContract));
    }

    /**
     * 修改合同管理
     */
    @PreAuthorize("@ss.hasPermi('management:contract:edit')")
    @Log(title = "合同管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ZjContract zjContract)
    {
        zjContract.setUpdateBy(getNickname());
        zjContract.setUpdateTime(DateUtils.getNowDate());
        return toAjax(zjContractService.updateZjContract(zjContract));
    }

    /**
     * 删除合同管理
     */
    @PreAuthorize("@ss.hasPermi('management:contract:remove')")
    @Log(title = "合同管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(zjContractService.deleteZjContractByIds(ids));
    }

    /**
     * 查询某个类型的合同的数量
     */
    @PreAuthorize("@ss.hasPermi('management:contract:list')")
    @GetMapping("/count/{contractType}")
    public AjaxResult countByContractType(@PathVariable String contractType)
    {
        return success(zjContractService.countByContractType(contractType));
    }

    @PreAuthorize("@ss.hasPermi('management:contract:list')")
    @GetMapping("/wbsummary")
    public AjaxResult wbSummary() {
        return success(zjContractService.getWbSummary());
    }
}
