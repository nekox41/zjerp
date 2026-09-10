package top.aameow.web.controller.system;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.system.mapper.SysUserMapper;

@RestController
@RequestMapping("/system/dashboard")
public class SysDashboardController extends BaseController {

    @Autowired
    private SysUserMapper userMapper;

    /** 首页员工统计 */
    @GetMapping("/userStat")
    public AjaxResult userStat() {
        Map<String, Object> data = new HashMap<>();
        data.put("total", userMapper.countActiveUsers());
        data.put("posts", userMapper.countUsersByPost());
        return AjaxResult.success(data);
    }
}