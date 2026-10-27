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
        int rows = zjContractMapper.deleteZjContractByIds(ids);
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
        int rows = zjContractMapper.deleteZjContractById(id);
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
    private void hydrateFiles(ZjContract record)
    {
        if (record == null) return;
        record.setContractFile(businessFiles.read("contract", record.getId(), record.getContractFile()));
    }
}
