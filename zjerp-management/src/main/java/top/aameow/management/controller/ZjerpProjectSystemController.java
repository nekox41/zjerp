package top.aameow.management.controller;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
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
import top.aameow.common.annotation.Anonymous;
import top.aameow.common.annotation.Log;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.enums.BusinessType;
import top.aameow.management.domain.ZjerpProjectSystem;
import top.aameow.management.service.IZjerpProjectSystemService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 项目系统Controller
 * 
 * @author ruoyi
 * @date 2025-06-06
 */
@RestController
@RequestMapping("/management/projectSystem")
public class ZjerpProjectSystemController extends BaseController
{
    @Autowired
    private IZjerpProjectSystemService zjerpProjectSystemService;

    @Autowired
    private ResourceLoader resourceLoader;

    /**
     * 查询项目系统列表
     */
    @PreAuthorize("@ss.hasPermi('management:projectSystem:list')")
    @GetMapping("/list")
    public TableDataInfo list(ZjerpProjectSystem zjerpProjectSystem)
    {
        startPage();
        List<ZjerpProjectSystem> list = zjerpProjectSystemService.selectZjerpProjectSystemList(zjerpProjectSystem);
        return getDataTable(list);
    }

    /**
     * 导出项目系统列表
     */
    @PreAuthorize("@ss.hasPermi('management:projectSystem:export')")
    @Log(title = "项目系统", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ZjerpProjectSystem zjerpProjectSystem)
    {
        List<ZjerpProjectSystem> list = zjerpProjectSystemService.selectZjerpProjectSystemList(zjerpProjectSystem);
        ExcelUtil<ZjerpProjectSystem> util = new ExcelUtil<ZjerpProjectSystem>(ZjerpProjectSystem.class);
        util.exportExcel(response, list, "项目系统数据");
    }

    /**
     * 获取项目系统详细信息
     */
    @Anonymous
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(zjerpProjectSystemService.selectZjerpProjectSystemById(id));
    }

    /**
     * 新增项目系统
     */
    @PreAuthorize("@ss.hasPermi('management:projectSystem:add')")
    @Log(title = "项目系统", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ZjerpProjectSystem zjerpProjectSystem)
    {
        // TODO 生成二维码
        // set system template
        Resource resource = resourceLoader.getResource("classpath:template.json");
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        zjerpProjectSystem.setProjectSystem(content.toString());
        return toAjax(zjerpProjectSystemService.insertZjerpProjectSystem(zjerpProjectSystem));
    }

    /**
     * 修改项目系统
     */
    @Anonymous
    @Log(title = "项目系统", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ZjerpProjectSystem zjerpProjectSystem)
    {
        return toAjax(zjerpProjectSystemService.updateZjerpProjectSystem(zjerpProjectSystem));
    }

    /**
     * 删除项目系统
     */
    @PreAuthorize("@ss.hasPermi('management:projectSystem:remove')")
    @Log(title = "项目系统", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(zjerpProjectSystemService.deleteZjerpProjectSystemByIds(ids));
    }
}
