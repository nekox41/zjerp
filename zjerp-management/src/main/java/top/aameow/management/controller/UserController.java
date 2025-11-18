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
import top.aameow.management.domain.User;
import top.aameow.management.service.IUserService;
import top.aameow.common.utils.poi.ExcelUtil;
import top.aameow.common.core.page.TableDataInfo;

/**
 * 员工资料Controller
 * 
 * @author aa
 * @date 2025-11-07
 */
@RestController
@RequestMapping("/management/user")
public class UserController extends BaseController
{
    @Autowired
    private IUserService userService;

    /**
     * 查询员工资料列表
     */
    @PreAuthorize("@ss.hasPermi('management:user:list')")
    @GetMapping("/list")
    public TableDataInfo list(User user)
    {
        startPage();
        List<User> list = userService.selectUserList(user);
        return getDataTable(list);
    }

    /**
     * 导出员工资料列表
     */
    @PreAuthorize("@ss.hasPermi('management:user:export')")
    @Log(title = "员工资料", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, User user)
    {
        List<User> list = userService.selectUserList(user);
        ExcelUtil<User> util = new ExcelUtil<User>(User.class);
        util.exportExcel(response, list, "员工资料数据");
    }

    /**
     * 获取员工资料详细信息
     */
    @PreAuthorize("@ss.hasPermi('management:user:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(userService.selectUserById(id));
    }

    /**
     * 新增员工资料
     */
    @PreAuthorize("@ss.hasPermi('management:user:add')")
    @Log(title = "员工资料", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody User user)
    {
        return toAjax(userService.insertUser(user));
    }

    /**
     * 修改员工资料
     */
    @PreAuthorize("@ss.hasPermi('management:user:edit')")
    @Log(title = "员工资料", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody User user)
    {
        return toAjax(userService.updateUser(user));
    }

    /**
     * 删除员工资料
     */
    @PreAuthorize("@ss.hasPermi('management:user:remove')")
    @Log(title = "员工资料", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(userService.deleteUserByIds(ids));
    }
}
