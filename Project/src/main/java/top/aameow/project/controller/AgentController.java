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
import top.aameow.project.domain.Agent;
import top.aameow.project.service.IAgentService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 中间人Controller
 * 
 * @author aa
 * @date 2026-01-23
 */
@RestController
@RequestMapping("/project/agent")
public class AgentController extends BaseController
{
    @Autowired
    private IAgentService agentService;

    /**
     * 查询中间人列表
     */
    @PreAuthorize("@ss.hasPermi('project:agent:list')")
    @GetMapping("/list")
    public TableDataInfo list(Agent agent)
    {
        startPage();
        List<Agent> list = agentService.selectAgentList(agent);
        return getDataTable(list);
    }

    /**
     * 导出中间人列表
     */
    @PreAuthorize("@ss.hasPermi('project:agent:export')")
    @Log(title = "中间人", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Agent agent)
    {
        List<Agent> list = agentService.selectAgentList(agent);
        ExcelUtil<Agent> util = new ExcelUtil<Agent>(Agent.class);
        util.exportExcel(response, list, "中间人数据");
    }

    /**
     * 获取中间人详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:agent:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(agentService.selectAgentById(id));
    }

    /**
     * 新增中间人
     */
    @PreAuthorize("@ss.hasPermi('project:agent:add')")
    @Log(title = "中间人", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Agent agent)
    {
        return toAjax(agentService.insertAgent(agent));
    }

    /**
     * 修改中间人
     */
    @PreAuthorize("@ss.hasPermi('project:agent:edit')")
    @Log(title = "中间人", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Agent agent)
    {
        return toAjax(agentService.updateAgent(agent));
    }

    /**
     * 删除中间人
     */
    @PreAuthorize("@ss.hasPermi('project:agent:remove')")
    @Log(title = "中间人", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(agentService.deleteAgentByIds(ids));
    }
}
