package top.aameow.management.mapper;

import java.util.List;
import top.aameow.management.domain.ZjerpProjectSystem;

/**
 * 项目系统Mapper接口
 * 
 * @author ruoyi
 * @date 2025-06-06
 */
public interface ZjerpProjectSystemMapper 
{
    /**
     * 查询项目系统
     * 
     * @param id 项目系统主键
     * @return 项目系统
     */
    public ZjerpProjectSystem selectZjerpProjectSystemById(Long id);

    /**
     * 查询项目系统列表
     * 
     * @param zjerpProjectSystem 项目系统
     * @return 项目系统集合
     */
    public List<ZjerpProjectSystem> selectZjerpProjectSystemList(ZjerpProjectSystem zjerpProjectSystem);

    /**
     * 新增项目系统
     * 
     * @param zjerpProjectSystem 项目系统
     * @return 结果
     */
    public int insertZjerpProjectSystem(ZjerpProjectSystem zjerpProjectSystem);

    /**
     * 修改项目系统
     * 
     * @param zjerpProjectSystem 项目系统
     * @return 结果
     */
    public int updateZjerpProjectSystem(ZjerpProjectSystem zjerpProjectSystem);

    /**
     * 删除项目系统
     * 
     * @param id 项目系统主键
     * @return 结果
     */
    public int deleteZjerpProjectSystemById(Long id);

    /**
     * 批量删除项目系统
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteZjerpProjectSystemByIds(Long[] ids);
}
