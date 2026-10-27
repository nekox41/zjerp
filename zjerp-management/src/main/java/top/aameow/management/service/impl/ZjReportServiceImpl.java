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
    private top.aameow.management.service.BusinessFiles businessFiles;

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
        ZjReport record = zjReportMapper.selectZjReportById(id);
        hydrateFiles(record);
        return record;
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
        List<ZjReport> records = zjReportMapper.selectZjReportList(zjReport);
        records.forEach(this::hydrateFiles);
        return records;
    }

    /**
     * 新增报告管理
     * 
     * @param zjReport 报告管理
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int insertZjReport(ZjReport zjReport)
    {
        int rows = zjReportMapper.insertZjReport(zjReport);
        if (rows > 0)
        {
            businessFiles.save("report", zjReport.getId(), zjReport.getReportFile());
            businessFiles.save("report_attach", zjReport.getId(), zjReport.getReportAttach());
        }
        return rows;
    }

    /**
     * 修改报告管理
     * 
     * @param zjReport 报告管理
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int updateZjReport(ZjReport zjReport)
    {
        int rows = zjReportMapper.updateZjReport(zjReport);
        if (rows > 0)
        {
            businessFiles.save("report", zjReport.getId(), zjReport.getReportFile());
            businessFiles.save("report_attach", zjReport.getId(), zjReport.getReportAttach());
        }
        return rows;
    }

    /**
     * 批量删除报告管理
     * 
     * @param ids 需要删除的报告管理主键
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int deleteZjReportByIds(Long[] ids)
    {
        int rows = zjReportMapper.deleteZjReportByIds(ids);
        for (Long fileBizId : ids)
        {
            businessFiles.save("report", fileBizId, "");
            businessFiles.save("report_attach", fileBizId, "");
        }
        return rows;
    }

    /**
     * 删除报告管理信息
     * 
     * @param id 报告管理主键
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int deleteZjReportById(Long id)
    {
        int rows = zjReportMapper.deleteZjReportById(id);
        for (Long fileBizId : new Long[] { id })
        {
            businessFiles.save("report", fileBizId, "");
            businessFiles.save("report_attach", fileBizId, "");
        }
        return rows;
    }
    private void hydrateFiles(ZjReport record)
    {
        if (record == null) return;
        record.setReportFile(businessFiles.read("report", record.getId(), record.getReportFile()));
        record.setReportAttach(businessFiles.read("report_attach", record.getId(), record.getReportAttach()));
    }
}
