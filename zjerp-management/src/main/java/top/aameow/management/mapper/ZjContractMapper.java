package top.aameow.management.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import top.aameow.management.domain.ZjContract;

/**
 * 合同管理Mapper接口
 * 
 * @author ruoyi
 * @date 2025-05-09
 */
public interface ZjContractMapper 
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
    public List<ZjContract> selectZjContractList(@Param("sortBy")String sortBy, @Param("orderBy")String orderBy, @Param("zjContract")ZjContract zjContract);

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
     * 删除合同管理
     * 
     * @param id 合同管理主键
     * @return 结果
     */
    public int deleteZjContractById(Long id);

    /**
     * 批量删除合同管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteZjContractByIds(Long[] ids);

    int countByContractType(int contractType);
}
