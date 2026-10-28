package top.aameow.management.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
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
@Getter
@Setter
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
    private String contractType;

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
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private java.math.BigDecimal amount;
    private String ownership;
    private Long intermediaryId;
    private String intermediaryName;
    private String paymentPlan;
    private String legacyType;
    private Boolean classificationPending;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getStorageTable() {
        if (contractType == null) return "";
        return switch(contractType) {
            case "WB" -> "zj_contract_maintenance";
            case "JC" -> "zj_contract_inspection";
            case "PD" -> "zj_contract_rating";
            case "PG" -> "zj_contract_assessment";
            case "QT" -> "zj_contract_other";
            default -> "";
        };
    }

    /** 合同附件 */
    @Excel(name = "合同附件")
    private String contractFile;

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
