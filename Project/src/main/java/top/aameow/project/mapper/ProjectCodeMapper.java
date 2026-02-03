package top.aameow.project.mapper;

import java.util.List;
import top.aameow.project.domain.ProjectCode;

/**
 * 编号申请Mapper接口
 * 
 * @author aa
 * @date 2026-02-03
 */
public interface ProjectCodeMapper 
{
    /**
     * 查询编号申请
     * 
     * @param id 编号申请主键
     * @return 编号申请
     */
    public ProjectCode selectProjectCodeById(Long id);

    /**
     * 查询编号申请列表
     * 
     * @param projectCode 编号申请
     * @return 编号申请集合
     */
    public List<ProjectCode> selectProjectCodeList(ProjectCode projectCode);

    /**
     * 新增编号申请
     * 
     * @param projectCode 编号申请
     * @return 结果
     */
    public int insertProjectCode(ProjectCode projectCode);

    /**
     * 修改编号申请
     * 
     * @param projectCode 编号申请
     * @return 结果
     */
    public int updateProjectCode(ProjectCode projectCode);

    /**
     * 删除编号申请
     * 
     * @param id 编号申请主键
     * @return 结果
     */
    public int deleteProjectCodeById(Long id);

    /**
     * 批量删除编号申请
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteProjectCodeByIds(Long[] ids);

    public int getIndexByType(String type);
}
