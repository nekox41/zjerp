package top.aameow.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.project.mapper.MaintainProjectMapper;
import top.aameow.project.domain.MaintainProject;
import top.aameow.project.service.IMaintainProjectService;

/**
 * 维保项目Service业务层处理
 * 
 * @author aa
 * @date 2026-01-24
 */
@Service
public class MaintainProjectServiceImpl implements IMaintainProjectService 
{
    @Autowired
    private MaintainProjectMapper maintainProjectMapper;

    /**
     * 查询维保项目
     * 
     * @param id 维保项目主键
     * @return 维保项目
     */
    @Override
    public MaintainProject selectMaintainProjectById(Long id)
    {
        return maintainProjectMapper.selectMaintainProjectById(id);
    }

    /**
     * 查询维保项目列表
     * 
     * @param maintainProject 维保项目
     * @return 维保项目
     */
    @Override
    public List<MaintainProject> selectMaintainProjectList(MaintainProject maintainProject)
    {
        return maintainProjectMapper.selectMaintainProjectList(maintainProject);
    }

    /**
     * 新增维保项目
     * 
     * @param maintainProject 维保项目
     * @return 结果
     */
    @Override
    public int insertMaintainProject(MaintainProject maintainProject)
    {
        return maintainProjectMapper.insertMaintainProject(maintainProject);
    }

    /**
     * 修改维保项目
     * 
     * @param maintainProject 维保项目
     * @return 结果
     */
    @Override
    public int updateMaintainProject(MaintainProject maintainProject)
    {
        return maintainProjectMapper.updateMaintainProject(maintainProject);
    }

    /**
     * 批量删除维保项目
     * 
     * @param ids 需要删除的维保项目主键
     * @return 结果
     */
    @Override
    public int deleteMaintainProjectByIds(Long[] ids)
    {
        return maintainProjectMapper.deleteMaintainProjectByIds(ids);
    }

    /**
     * 删除维保项目信息
     * 
     * @param id 维保项目主键
     * @return 结果
     */
    @Override
    public int deleteMaintainProjectById(Long id)
    {
        return maintainProjectMapper.deleteMaintainProjectById(id);
    }
}
