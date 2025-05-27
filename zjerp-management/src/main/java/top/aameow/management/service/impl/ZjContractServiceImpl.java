package top.aameow.management.service.impl;

import java.util.List;
import top.aameow.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
        return zjContractMapper.selectZjContractById(id);
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
        return zjContractMapper.selectZjContractList(zjContract);
    }

    /**
     * 新增合同管理
     * 
     * @param zjContract 合同管理
     * @return 结果
     */
    @Override
    public int insertZjContract(ZjContract zjContract)
    {
        zjContract.setCreateTime(DateUtils.getNowDate());
        return zjContractMapper.insertZjContract(zjContract);
    }

    /**
     * 修改合同管理
     * 
     * @param zjContract 合同管理
     * @return 结果
     */
    @Override
    public int updateZjContract(ZjContract zjContract)
    {
        zjContract.setUpdateTime(DateUtils.getNowDate());
        return zjContractMapper.updateZjContract(zjContract);
    }

    /**
     * 批量删除合同管理
     * 
     * @param ids 需要删除的合同管理主键
     * @return 结果
     */
    @Override
    public int deleteZjContractByIds(Long[] ids)
    {
        return zjContractMapper.deleteZjContractByIds(ids);
    }

    /**
     * 删除合同管理信息
     * 
     * @param id 合同管理主键
     * @return 结果
     */
    @Override
    public int deleteZjContractById(Long id)
    {
        return zjContractMapper.deleteZjContractById(id);
    }

    @Override
    public int countByContractType(int contractType) {
        return zjContractMapper.countByContractType(contractType);
    }
}
