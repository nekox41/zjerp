package top.aameow.project.mapper;

import java.util.List;
import top.aameow.project.domain.MaintainProject;

/**
 * 维保项目Mapper接口
 * 
 * @author aa
 * @date 2026-01-24
 */
public interface MaintainProjectMapper 
{
    /**
     * 查询维保项目
     * 
     * @param id 维保项目主键
     * @return 维保项目
     */
    public MaintainProject selectMaintainProjectById(Long id);

    /**
     * 查询维保项目列表
     * 
     * @param maintainProject 维保项目
     * @return 维保项目集合
     */
    public List<MaintainProject> selectMaintainProjectList(MaintainProject maintainProject);

    /**
     * 新增维保项目
     * 
     * @param maintainProject 维保项目
     * @return 结果
     */
    public int insertMaintainProject(MaintainProject maintainProject);

    /**
     * 修改维保项目
     * 
     * @param maintainProject 维保项目
     * @return 结果
     */
    public int updateMaintainProject(MaintainProject maintainProject);

    /**
     * 删除维保项目
     * 
     * @param id 维保项目主键
     * @return 结果
     */
    public int deleteMaintainProjectById(Long id);

    /**
     * 批量删除维保项目
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteMaintainProjectByIds(Long[] ids);
}
