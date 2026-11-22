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
import top.aameow.management.domain.Agent;
import top.aameow.management.service.IAgentService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 代理人Controller
 * 
 * @author aameow
 * @date 2026-10-16
 */
@RestController
@RequestMapping("/management/agent")
public class AgentController extends BaseController
{
    @Autowired
    private IAgentService agentService;

    /**
     * 查询代理人列表
     */
    @PreAuthorize("@ss.hasPermi('management:agent:list')")
    @GetMapping("/list")
    public TableDataInfo list(Agent agent)
    {
        startPage();
        List<Agent> list = agentService.selectAgentList(agent);
        return getDataTable(list);
    }

    /**
     * 导出代理人列表
     */
    @PreAuthorize("@ss.hasPermi('management:agent:export')")
    @Log(title = "代理人", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Agent agent)
    {
        List<Agent> list = agentService.selectAgentList(agent);
        ExcelUtil<Agent> util = new ExcelUtil<Agent>(Agent.class);
        util.exportExcel(response, list, "代理人数据");
    }

    /**
     * 获取代理人详细信息
     */
    @PreAuthorize("@ss.hasPermi('management:agent:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(agentService.selectAgentById(id));
    }

    /**
     * 新增代理人
     */
    @PreAuthorize("@ss.hasPermi('management:agent:add')")
    @Log(title = "代理人", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Agent agent)
    {
        return toAjax(agentService.insertAgent(agent));
    }

    /**
     * 修改代理人
     */
    @PreAuthorize("@ss.hasPermi('management:agent:edit')")
    @Log(title = "代理人", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Agent agent)
    {
        return toAjax(agentService.updateAgent(agent));
    }

    /**
     * 删除代理人
     */
    @PreAuthorize("@ss.hasPermi('management:agent:remove')")
    @Log(title = "代理人", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(agentService.deleteAgentByIds(ids));
    }
}
