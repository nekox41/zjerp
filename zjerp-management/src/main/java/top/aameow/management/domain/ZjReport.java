package top.aameow.management.domain;

import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import top.aameow.common.annotation.Excel;
import top.aameow.common.core.domain.BaseEntity;

/**
 * 报告管理对象 zj_report
 *
 * @author aa
 * @date 2025-05-16
 */
@Data
public class ZjReport extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 报告编号 */
    @Excel(name = "报告编号")
    private String reportId;

    /** 名称 */
    @Excel(name = "名称")
    private String reportName;

    /** 报告文件 */
    @Excel(name = "报告文件")
    private String reportFile;

    /** 报告类型 */
    @Excel(name = "报告类型")
    private String reportType;

    /** 最终版本 */
    @Excel(name = "最终版本")
    private String reportIsfinal;

    /** 报告归属 */
    @Excel(name = "报告归属")
    private String reportManager;

    @Excel(name = "项目面积")
    private String reportArea;

    @Excel(name = "附件")
    private String reportAttach;

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("reportId", getReportId())
                .append("reportName", getReportName())
                .append("reportFile", getReportFile())
                .append("reportAttach", getReportAttach())
                .append("reportType", getReportType())
                .append("reportIsfinal", getReportIsfinal())
                .append("reportManager", getReportManager())
                .append("reportArea", getReportArea())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .toString();
    }
}
