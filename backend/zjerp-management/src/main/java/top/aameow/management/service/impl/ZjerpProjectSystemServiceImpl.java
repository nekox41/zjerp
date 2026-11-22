package top.aameow.management.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.management.mapper.ZjerpProjectSystemMapper;
import top.aameow.management.domain.ZjerpProjectSystem;
import top.aameow.management.service.IZjerpProjectSystemService;

/**
 * 项目系统Service业务层处理
 * 
 * @author ruoyi
 * @date 2025-06-06
 */
@Service
public class ZjerpProjectSystemServiceImpl implements IZjerpProjectSystemService 
{
    @Autowired
    private ZjerpProjectSystemMapper zjerpProjectSystemMapper;

    /**
     * 查询项目系统
     * 
     * @param id 项目系统主键
     * @return 项目系统
     */
    @Override
    public ZjerpProjectSystem selectZjerpProjectSystemById(Long id)
    {
        return zjerpProjectSystemMapper.selectZjerpProjectSystemById(id);
    }

    /**
     * 查询项目系统列表
     * 
     * @param zjerpProjectSystem 项目系统
     * @return 项目系统
     */
    @Override
    public List<ZjerpProjectSystem> selectZjerpProjectSystemList(ZjerpProjectSystem zjerpProjectSystem)
    {
        return zjerpProjectSystemMapper.selectZjerpProjectSystemList(zjerpProjectSystem);
    }

    /**
     * 新增项目系统
     * 
     * @param zjerpProjectSystem 项目系统
     * @return 结果
     */
    @Override
    public int insertZjerpProjectSystem(ZjerpProjectSystem zjerpProjectSystem)
    {
        return zjerpProjectSystemMapper.insertZjerpProjectSystem(zjerpProjectSystem);
    }

    /**
     * 修改项目系统
     * 
     * @param zjerpProjectSystem 项目系统
     * @return 结果
     */
    @Override
    public int updateZjerpProjectSystem(ZjerpProjectSystem zjerpProjectSystem)
    {
        return zjerpProjectSystemMapper.updateZjerpProjectSystem(zjerpProjectSystem);
    }

    /**
     * 批量删除项目系统
     * 
     * @param ids 需要删除的项目系统主键
     * @return 结果
     */
    @Override
    public int deleteZjerpProjectSystemByIds(Long[] ids)
    {
        return zjerpProjectSystemMapper.deleteZjerpProjectSystemByIds(ids);
    }

    /**
     * 删除项目系统信息
     * 
     * @param id 项目系统主键
     * @return 结果
     */
    @Override
    public int deleteZjerpProjectSystemById(Long id)
    {
        return zjerpProjectSystemMapper.deleteZjerpProjectSystemById(id);
    }
}
