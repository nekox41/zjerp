package top.aameow.management.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.management.domain.WbSummary;
import top.aameow.management.mapper.ZjContractMapper;
import top.aameow.management.domain.ZjContract;
import top.aameow.management.service.IZjContractService;

/**
 * 合同管理Service业务层处理
 * 
 * @author ruoyi
 * @date 2025-05-09
 */
@Service
public class ZjContractServiceImpl implements IZjContractService 
{
    @Autowired
    private top.aameow.management.service.BusinessFiles businessFiles;

    @Autowired
    private ZjContractMapper zjContractMapper;

    /**
     * 查询合同管理
     * 
     * @param id 合同管理主键
     * @return 合同管理
     */
    @Override
    public ZjContract selectZjContractById(Long id)
    {
        ZjContract record = zjContractMapper.selectZjContractById(id);
        hydrateFiles(record);
        return record;
    }

    /**
     * 查询合同管理列表
     * 
     * @param zjContract 合同管理
     * @return 合同管理
     */
    @Override
    public List<ZjContract> selectZjContractList(ZjContract zjContract)
    {
        List<ZjContract> records = zjContractMapper.selectZjContractList(zjContract);
        records.forEach(this::hydrateFiles);
        return records;
    }

    /**
     * 新增合同管理
     * 
     * @param zjContract 合同管理
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int insertZjContract(ZjContract zjContract)
    {
        validate(zjContract, false);
        zjContractMapper.allocateId(zjContract);
        if(zjContract.getContractId()==null||zjContract.getContractId().isBlank()) zjContract.setContractId("ZJHT"+java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)+zjContract.getContractType()+String.format(java.util.Locale.ROOT,"%06d",zjContract.getId()));
        int rows = zjContractMapper.insertZjContract(zjContract);
        if (rows > 0)
        {
            businessFiles.save("contract", zjContract.getId(), zjContract.getContractFile());
        }
        return rows;
    }

    /**
     * 修改合同管理
     * 
     * @param zjContract 合同管理
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int updateZjContract(ZjContract zjContract)
    {
        validate(zjContract, true);
        int rows = zjContractMapper.updateZjContract(zjContract);
        if (rows > 0)
        {
            businessFiles.save("contract", zjContract.getId(), zjContract.getContractFile());
        }
        return rows;
    }

    /**
     * 批量删除合同管理
     * 
     * @param ids 需要删除的合同管理主键
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int deleteZjContractByIds(Long[] ids)
    {
        int rows;
        try { rows = zjContractMapper.deleteZjContractByIds(ids); } catch(org.springframework.dao.DataIntegrityViolationException e) { throw new top.aameow.common.exception.ServiceException("合同已关联项目，不能删除"); }
        for (Long fileBizId : ids)
        {
            businessFiles.save("contract", fileBizId, "");
        }
        return rows;
    }

    /**
     * 删除合同管理信息
     * 
     * @param id 合同管理主键
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int deleteZjContractById(Long id)
    {
        int rows;
        try { rows = zjContractMapper.deleteZjContractById(id); } catch(org.springframework.dao.DataIntegrityViolationException e) { throw new top.aameow.common.exception.ServiceException("合同已关联项目，不能删除"); }
        for (Long fileBizId : new Long[] { id })
        {
            businessFiles.save("contract", fileBizId, "");
        }
        return rows;
    }

    @Override
    public int countByContractType(String contractType) {
        return zjContractMapper.countByContractType(contractType);
    }

    @Override
    public WbSummary getWbSummary() {
        return zjContractMapper.getWbSummary();
    }
    @Autowired private top.aameow.management.mapper.IntermediaryMapper intermediaryMapper;
    private void validate(ZjContract c, boolean editing) {
        if(editing) {
            ZjContract old=zjContractMapper.selectZjContractById(c.getId());
            if(old==null) throw new top.aameow.common.exception.ServiceException("合同不存在");
            if(c.getContractType()!=null && !c.getContractType().equals(old.getContractType())) throw new top.aameow.common.exception.ServiceException("不能直接修改合同所属模块");
            c.setContractType(old.getContractType());
        }
        if(c.getStorageTable().isEmpty()) throw new top.aameow.common.exception.ServiceException("不支持的合同类型");
        if(c.getAmount()!=null && c.getAmount().stripTrailingZeros().scale()>2) {
            ZjContract previous=editing?zjContractMapper.selectZjContractById(c.getId()):null;
            if(previous==null || previous.getAmount()==null || previous.getAmount().compareTo(c.getAmount())!=0) throw new top.aameow.common.exception.ServiceException("金额最多两位小数");
        }
        if(c.getRemark()!=null && c.getRemark().length()>5000) throw new top.aameow.common.exception.ServiceException("文本超出长度限制");
        if(c.getContractName()!=null && c.getContractName().length()>255) throw new top.aameow.common.exception.ServiceException("合同名称最多255字符");
        if((editing&&(c.getContractId()==null||c.getContractId().isBlank()))||(c.getContractId()!=null&&c.getContractId().length()>255)) throw new top.aameow.common.exception.ServiceException("合同编号不能清空且最多255字符");
        if(c.getAmount()!=null && (c.getAmount().signum()<0 || c.getAmount().precision()-c.getAmount().scale()>13)) throw new top.aameow.common.exception.ServiceException("合同金额超出范围");
        if(c.getStartTime()!=null && c.getEndTime()!=null && c.getEndTime().before(c.getStartTime())) throw new top.aameow.common.exception.ServiceException("结束日期不能早于开始日期");
        if(c.getOwnership()==null) c.setOwnership("pending");
        if(!java.util.Arrays.asList("pending","company","intermediary").contains(c.getOwnership())) throw new top.aameow.common.exception.ServiceException("合同归属无效");
        if("intermediary".equals(c.getOwnership())) {
            if(c.getIntermediaryId()==null || intermediaryMapper.get(c.getIntermediaryId())==null) throw new top.aameow.common.exception.ServiceException("请选择中间人");
        } else c.setIntermediaryId(null);
        if(c.getPaymentPlan()!=null) {
            try {
                com.fasterxml.jackson.databind.JsonNode plan=new com.fasterxml.jackson.databind.ObjectMapper().readTree(c.getPaymentPlan());
                if(!plan.isArray() || plan.size()>100) throw new IllegalArgumentException();
                java.math.BigDecimal total=java.math.BigDecimal.ZERO;
                for(com.fasterxml.jackson.databind.JsonNode item:plan) {
                    if(!item.hasNonNull("name") || item.get("name").asText().isBlank() || item.get("name").asText().length()>100 || !item.hasNonNull("amount")) throw new IllegalArgumentException();
                    java.math.BigDecimal amount=new java.math.BigDecimal(item.get("amount").asText());
                    if(amount.signum()<0 || amount.scale()>2) throw new IllegalArgumentException();
                    total=total.add(amount);
                    if(item.hasNonNull("dueDate") && !item.get("dueDate").asText().isBlank()) java.time.LocalDate.parse(item.get("dueDate").asText());
                }
                if(c.getAmount()!=null && total.compareTo(c.getAmount())>0) throw new IllegalArgumentException();
            } catch(Exception e) { throw new top.aameow.common.exception.ServiceException("付款计划格式无效，金额须非负且合计不得超过合同金额"); }
        }
        if(!editing) c.setClassificationPending(false);
    }
    private void hydrateFiles(ZjContract record)
    {
        if (record == null) return;
        record.setContractFile(businessFiles.read("contract", record.getId(), record.getContractFile()));
    }
}
