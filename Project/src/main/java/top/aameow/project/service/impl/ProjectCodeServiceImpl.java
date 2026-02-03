package top.aameow.project.service.impl;

import java.util.Date;
import java.util.List;
import top.aameow.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.common.utils.SecurityUtils;
import top.aameow.project.mapper.ProjectCodeMapper;
import top.aameow.project.domain.ProjectCode;
import top.aameow.project.service.IProjectCodeService;

/**
 * 编号申请Service业务层处理
 * 
 * @author aa
 * @date 2026-02-03
 */
@Service
public class ProjectCodeServiceImpl implements IProjectCodeService 
{
    @Autowired
    private ProjectCodeMapper projectCodeMapper;

    /**
     * 查询编号申请
     * 
     * @param id 编号申请主键
     * @return 编号申请
     */
    @Override
    public ProjectCode selectProjectCodeById(Long id)
    {
        return projectCodeMapper.selectProjectCodeById(id);
    }

    /**
     * 查询编号申请列表
     * 
     * @param projectCode 编号申请
     * @return 编号申请
     */
    @Override
    public List<ProjectCode> selectProjectCodeList(ProjectCode projectCode)
    {
        return projectCodeMapper.selectProjectCodeList(projectCode);
    }

    /**
     * 新增编号申请
     * 
     * @param projectCode 编号申请
     * @return 结果
     */
    @Override
    public int insertProjectCode(ProjectCode projectCode)
    {
        projectCode.setCreateBy(SecurityUtils.getLoginUser().getNickName());
        projectCode.setCreateTime(DateUtils.getNowDate());
        genProjectCode(projectCode);
        return projectCodeMapper.insertProjectCode(projectCode);
    }

    /**
     * 修改编号申请
     * 
     * @param projectCode 编号申请
     * @return 结果
     */
    @Override
    public int updateProjectCode(ProjectCode projectCode)
    {
        return projectCodeMapper.updateProjectCode(projectCode);
    }

    /**
     * 批量删除编号申请
     * 
     * @param ids 需要删除的编号申请主键
     * @return 结果
     */
    @Override
    public int deleteProjectCodeByIds(Long[] ids)
    {
        return projectCodeMapper.deleteProjectCodeByIds(ids);
    }

    /**
     * 删除编号申请信息
     * 
     * @param id 编号申请主键
     * @return 结果
     */
    @Override
    public int deleteProjectCodeById(Long id)
    {
        return projectCodeMapper.deleteProjectCodeById(id);
    }

    @Override
    public void genProjectCode(ProjectCode projectCode) {
        // PD JC YS PG
        String type = projectCode.getType();
        String prefix = "ZJXF";
        Date date = projectCode.getCreateTime();
        // 获取序号
        int index = getIndexByType(type);

        String dateString = DateUtils.parseDateToStr("yyyyMM", date);
        String indexString = String.format("%03d", index);
        String string = prefix + dateString + type + indexString;
        projectCode.setCode(string);
    }

    @Override
    public int getIndexByType(String type) {
        return projectCodeMapper.getIndexByType(type);
    }
}
