package top.aameow.management.service.impl;

import java.util.List;
import top.aameow.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.management.mapper.ZjReportMapper;
import top.aameow.management.domain.ZjReport;
import top.aameow.management.service.IZjReportService;

/**
 * 报告管理Service业务层处理
 * 
 * @author aa
 * @date 2025-05-13
 */
@Service
public class ZjReportServiceImpl implements IZjReportService 
{
    @Autowired
    private ZjReportMapper zjReportMapper;

    /**
     * 查询报告管理
     * 
     * @param id 报告管理主键
     * @return 报告管理
     */
    @Override
    public ZjReport selectZjReportById(Long id)
    {
        return zjReportMapper.selectZjReportById(id);
    }

    /**
     * 查询报告管理列表
     * 
     * @param zjReport 报告管理
     * @return 报告管理
     */
    @Override
    public List<ZjReport> selectZjReportList(ZjReport zjReport)
    {
        return zjReportMapper.selectZjReportList(zjReport);
    }

    /**
     * 新增报告管理
     * 
     * @param zjReport 报告管理
     * @return 结果
     */
    @Override
    public int insertZjReport(ZjReport zjReport)
    {
        zjReport.setCreateTime(DateUtils.getNowDate());
        return zjReportMapper.insertZjReport(zjReport);
    }

    /**
     * 修改报告管理
     * 
     * @param zjReport 报告管理
     * @return 结果
     */
    @Override
    public int updateZjReport(ZjReport zjReport)
    {
        zjReport.setUpdateTime(DateUtils.getNowDate());
        return zjReportMapper.updateZjReport(zjReport);
    }

    /**
     * 批量删除报告管理
     * 
     * @param ids 需要删除的报告管理主键
     * @return 结果
     */
    @Override
    public int deleteZjReportByIds(Long[] ids)
    {
        return zjReportMapper.deleteZjReportByIds(ids);
    }

    /**
     * 删除报告管理信息
     * 
     * @param id 报告管理主键
     * @return 结果
     */
    @Override
    public int deleteZjReportById(Long id)
    {
        return zjReportMapper.deleteZjReportById(id);
    }
}
