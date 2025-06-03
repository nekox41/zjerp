package top.aameow.management.service;

import java.util.List;
import top.aameow.management.domain.ZjContract;

/**
 * 合同管理Service接口
 * 
 * @author ruoyi
 * @date 2025-05-09
 */
public interface IZjContractService 
{
    /**
     * 查询合同管理
     * 
     * @param id 合同管理主键
     * @return 合同管理
     */
    public ZjContract selectZjContractById(Long id);

    /**
     * 查询合同管理列表
     * 
     * @param zjContract 合同管理
     * @return 合同管理集合
     */
    public List<ZjContract> selectZjContractList(ZjContract zjContract);

    /**
     * 新增合同管理
     * 
     * @param zjContract 合同管理
     * @return 结果
     */
    public int insertZjContract(ZjContract zjContract);

    /**
     * 修改合同管理
     * 
     * @param zjContract 合同管理
     * @return 结果
     */
    public int updateZjContract(ZjContract zjContract);

    /**
     * 批量删除合同管理
     * 
     * @param ids 需要删除的合同管理主键集合
     * @return 结果
     */
    public int deleteZjContractByIds(Long[] ids);

    /**
     * 删除合同管理信息
     * 
     * @param id 合同管理主键
     * @return 结果
     */
    public int deleteZjContractById(Long id);

    int countByContractType(int contractType);
}
