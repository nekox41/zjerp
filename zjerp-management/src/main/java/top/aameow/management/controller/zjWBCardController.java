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
import top.aameow.management.domain.WBCardPhone;
import top.aameow.management.domain.zjWBCard;
import top.aameow.management.service.IzjWBCardService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 维保卡管理Controller
 * 
 * @author aa
 * @date 2025-11-05
 */
@RestController
@RequestMapping("/management/wbcard")
public class zjWBCardController extends BaseController
{
    @Autowired
    private IzjWBCardService zjWBCardService;

    /**
     * 查询维保卡管理列表
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:list')")
    @GetMapping("/list")
    public TableDataInfo list(zjWBCard zjWBCard)
    {
        startPage();
        List<zjWBCard> list = zjWBCardService.selectzjWBCardList(zjWBCard);
        return getDataTable(list);
    }

    /**
     * 导出维保卡管理列表
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:export')")
    @Log(title = "维保卡管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, zjWBCard zjWBCard)
    {
        List<zjWBCard> list = zjWBCardService.selectzjWBCardList(zjWBCard);
        ExcelUtil<zjWBCard> util = new ExcelUtil<zjWBCard>(zjWBCard.class);
        util.exportExcel(response, list, "维保卡管理数据");
    }

    /**
     * 获取维保卡管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(zjWBCardService.selectzjWBCardById(id));
    }

    /**
     * 新增维保卡管理
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:add')")
    @Log(title = "维保卡管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody zjWBCard zjWBCard)
    {
        zjWBCard.setCreateTime(DateUtils.getNowDate());
        zjWBCard.setUpdateTime(DateUtils.getNowDate());
        zjWBCard.setCreateBy(getNickname());
        zjWBCard.setUpdateBy(getNickname());
        return toAjax(zjWBCardService.insertzjWBCard(zjWBCard));
    }

    /**
     * 修改维保卡管理
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:edit')")
    @Log(title = "维保卡管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody zjWBCard zjWBCard)
    {
        zjWBCard.setUpdateTime(DateUtils.getNowDate());
        zjWBCard.setUpdateBy(getNickname());
        return toAjax(zjWBCardService.updatezjWBCard(zjWBCard));
    }

    /**
     * 删除维保卡管理
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:remove')")
    @Log(title = "维保卡管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(zjWBCardService.deletezjWBCardByIds(ids));
    }

    /**
     * 获取生成维保卡时可用的手机号列表
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:list')")
    @GetMapping("/listphone")
    public TableDataInfo getPhoneList(WBCardPhone phone) {
        startPage();
        List<WBCardPhone> list = zjWBCardService.getPhoneList(phone);
        return getDataTable(list);
    }

    /**
     * 添加新手机号
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:add')")
    @PostMapping("/addphone")
    public AjaxResult addPhone(@RequestBody WBCardPhone phone) {
        return toAjax(zjWBCardService.insertWBCardPhone(phone));
    }

    /**
     * 删除手机号
     */
    @PreAuthorize("@ss.hasPermi('management:wbcard:remove')")
    @DeleteMapping("/phone/{id}")
    public AjaxResult removePhone(@PathVariable Long id) {
        return toAjax(zjWBCardService.deleteWBCardPhoneById(id));
    }
}
