package top.aameow.management.service;

import java.util.List;
import top.aameow.management.domain.ZjReport;

/**
 * 报告管理Service接口
 * 
 * @author aa
 * @date 2025-05-13
 */
public interface IZjReportService 
{
    /**
     * 查询报告管理
     * 
     * @param id 报告管理主键
     * @return 报告管理
     */
    public ZjReport selectZjReportById(Long id);

    /**
     * 查询报告管理列表
     * 
     * @param zjReport 报告管理
     * @return 报告管理集合
     */
    public List<ZjReport> selectZjReportList(ZjReport zjReport);

    /**
     * 新增报告管理
     * 
     * @param zjReport 报告管理
     * @return 结果
     */
    public int insertZjReport(ZjReport zjReport);

    /**
     * 修改报告管理
     * 
     * @param zjReport 报告管理
     * @return 结果
     */
    public int updateZjReport(ZjReport zjReport);

    /**
     * 批量删除报告管理
     * 
     * @param ids 需要删除的报告管理主键集合
     * @return 结果
     */
    public int deleteZjReportByIds(Long[] ids);

    /**
     * 删除报告管理信息
     * 
     * @param id 报告管理主键
     * @return 结果
     */
    public int deleteZjReportById(Long id);
}
