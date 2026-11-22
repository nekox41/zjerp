package top.aameow.management.domain;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import top.aameow.common.annotation.Excel;
import top.aameow.common.core.domain.BaseEntity;

/**
 * 项目系统对象 zjerp_project_system
 * 
 * @author ruoyi
 * @date 2025-06-06
 */
@Setter
@Getter
public class ZjerpProjectSystem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 项目名称 */
    @Excel(name = "项目名称")
    private String projectName;

    /** 二维码 */
    @Excel(name = "二维码")
    private String qrcode;

    /** 系统 */
    @Excel(name = "系统")
    private String projectSystem;

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("projectName", getProjectName())
            .append("qrcode", getQrcode())
            .append("projectSystem", getProjectSystem())
            .toString();
    }
}
