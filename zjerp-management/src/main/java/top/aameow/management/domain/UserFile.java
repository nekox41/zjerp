package top.aameow.management.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import top.aameow.common.annotation.Excel;
import top.aameow.common.core.domain.BaseEntity;

/**
 * 用户资料对象 zj_userfile
 * 
 * @author aa
 * @date 2025-11-07
 */
public class UserFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 标签 */
    @Excel(name = "名称")
    private String name;

    /** 文件 */
    @Excel(name = "文件")
    private String file;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }
    public void setTag(String name)
    {
        this.name = name;
    }

    public String getTag() 
    {
        return name;
    }
    public void setFile(String file) 
    {
        this.file = file;
    }

    public String getFile() 
    {
        return file;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("tag", getTag())
            .append("file", getFile())
            .toString();
    }
}
