package top.aameow.management.domain;

import java.util.List;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import top.aameow.common.annotation.Excel;
import top.aameow.common.core.domain.BaseEntity;

/**
 * 员工资料对象 zj_user
 * 
 * @author aa
 * @date 2025-11-07
 */
public class User extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    private Long id;

    /** 姓名 */
    @Excel(name = "姓名")
    private String userName;

    /** 人员类别 */
    @Excel(name = "人员类别")
    private String userType;

    /** 手机号 */
    @Excel(name = "手机号")
    private String userPhone;

    /** 出生日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "出生日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date userBrithday;

    /** 年龄 */
    @Excel(name = "年龄")
    private Long userAge;

    /** 身份证号 */
    @Excel(name = "身份证号")
    private String userChinaid;

    /** 证书编号 */
    @Excel(name = "证书编号")
    private String userCert;

    /** 用户资料信息 */
    private List<UserFile> userFileList;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setUserName(String userName) 
    {
        this.userName = userName;
    }

    public String getUserName() 
    {
        return userName;
    }

    public void setUserType(String userType) 
    {
        this.userType = userType;
    }

    public String getUserType() 
    {
        return userType;
    }

    public void setUserPhone(String userPhone) 
    {
        this.userPhone = userPhone;
    }

    public String getUserPhone() 
    {
        return userPhone;
    }

    public void setUserBrithday(Date userBrithday) 
    {
        this.userBrithday = userBrithday;
    }

    public Date getUserBrithday() 
    {
        return userBrithday;
    }

    public void setUserAge(Long userAge) 
    {
        this.userAge = userAge;
    }

    public Long getUserAge() 
    {
        return userAge;
    }

    public void setUserChinaid(String userChinaid) 
    {
        this.userChinaid = userChinaid;
    }

    public String getUserChinaid() 
    {
        return userChinaid;
    }

    public void setUserCert(String userCert) 
    {
        this.userCert = userCert;
    }

    public String getUserCert() 
    {
        return userCert;
    }

    public List<UserFile> getUserFileList()
    {
        return userFileList;
    }

    public void setUserFileList(List<UserFile> userFileList)
    {
        this.userFileList = userFileList;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userName", getUserName())
            .append("userType", getUserType())
            .append("userPhone", getUserPhone())
            .append("userBrithday", getUserBrithday())
            .append("userAge", getUserAge())
            .append("userChinaid", getUserChinaid())
            .append("userCert", getUserCert())
            .append("userFileList", getUserFileList())
            .toString();
    }
}
