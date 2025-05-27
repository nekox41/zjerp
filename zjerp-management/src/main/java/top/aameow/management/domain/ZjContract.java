package top.aameow.management.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import top.aameow.common.annotation.Excel;
import top.aameow.common.core.domain.BaseEntity;

/**
 * 合同管理对象 zj_contract
 *
 * @author ruoyi
 * @date 2025-05-13
 */
public class ZjContract extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /**  */
    private Long id;

    /** 合同编号 */
    @Excel(name = "合同编号")
    private String contractId;

    /** 合同类型 */
    @Excel(name = "合同类型")
    private Integer contractType;

    /** 合同名称 */
    @Excel(name = "合同名称")
    private String contractName;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "开始时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "结束时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date endTime;

    /** 合同负责人 */
    @Excel(name = "合同负责人")
    private String manager;

    /** 合同金额 */
    @Excel(name = "合同金额")
    private Long amount;

    /** 合同附件 */
    @Excel(name = "合同附件")
    private String contractFile;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setContractId(String contractId)
    {
        this.contractId = contractId;
    }

    public String getContractId()
    {
        return contractId;
    }

    public void setContractType(Integer contractType)
    {
        this.contractType = contractType;
    }

    public Integer getContractType()
    {
        return contractType;
    }

    public void setContractName(String contractName)
    {
        this.contractName = contractName;
    }

    public String getContractName()
    {
        return contractName;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    public void setManager(String manager)
    {
        this.manager = manager;
    }

    public String getManager()
    {
        return manager;
    }

    public void setAmount(Long amount)
    {
        this.amount = amount;
    }

    public Long getAmount()
    {
        return amount;
    }

    public void setContractFile(String contractFile)
    {
        this.contractFile = contractFile;
    }

    public String getContractFile()
    {
        return contractFile;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("contractId", getContractId())
                .append("contractType", getContractType())
                .append("contractName", getContractName())
                .append("startTime", getStartTime())
                .append("endTime", getEndTime())
                .append("manager", getManager())
                .append("amount", getAmount())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .append("contractFile", getContractFile())
                .toString();
    }
}
