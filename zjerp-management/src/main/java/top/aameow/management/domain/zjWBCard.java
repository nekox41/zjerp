package top.aameow.management.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import top.aameow.common.annotation.Excel;
import top.aameow.common.core.domain.BaseEntity;

/**
 * 维保卡管理对象 zj_wbcard
 * 
 * @author aa
 * @date 2025-11-05
 */
public class zjWBCard extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 编号 */
    @Excel(name = "编号")
    private String code;

    /** 标题 */
    @Excel(name = "标题")
    private String title;

    /** 文件 */
    @Excel(name = "文件")
    private String file;

    /** 是否打印 */
    @Excel(name = "是否打印")
    private String printCheck;

    /** 是否邮寄 */
    @Excel(name = "是否邮寄")
    private String sendCheck;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setCode(String code) 
    {
        this.code = code;
    }

    public String getCode() 
    {
        return code;
    }

    public void setTitle(String title) 
    {
        this.title = title;
    }

    public String getTitle() 
    {
        return title;
    }

    public void setFile(String file) 
    {
        this.file = file;
    }

    public String getFile() 
    {
        return file;
    }

    public void setPrintCheck(String printCheck) 
    {
        this.printCheck = printCheck;
    }

    public String getPrintCheck() 
    {
        return printCheck;
    }

    public void setSendCheck(String sendCheck) 
    {
        this.sendCheck = sendCheck;
    }

    public String getSendCheck() 
    {
        return sendCheck;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("code", getCode())
            .append("title", getTitle())
            .append("file", getFile())
            .append("printCheck", getPrintCheck())
            .append("sendCheck", getSendCheck())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
