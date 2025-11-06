package top.aameow.management.service;

import java.util.List;
import top.aameow.management.domain.zjWBCard;

/**
 * 维保卡管理Service接口
 * 
 * @author aa
 * @date 2025-11-05
 */
public interface IzjWBCardService 
{
    /**
     * 查询维保卡管理
     * 
     * @param id 维保卡管理主键
     * @return 维保卡管理
     */
    public zjWBCard selectzjWBCardById(Long id);

    /**
     * 查询维保卡管理列表
     * 
     * @param zjWBCard 维保卡管理
     * @return 维保卡管理集合
     */
    public List<zjWBCard> selectzjWBCardList(zjWBCard zjWBCard);

    /**
     * 新增维保卡管理
     * 
     * @param zjWBCard 维保卡管理
     * @return 结果
     */
    public int insertzjWBCard(zjWBCard zjWBCard);

    /**
     * 修改维保卡管理
     * 
     * @param zjWBCard 维保卡管理
     * @return 结果
     */
    public int updatezjWBCard(zjWBCard zjWBCard);

    /**
     * 批量删除维保卡管理
     * 
     * @param ids 需要删除的维保卡管理主键集合
     * @return 结果
     */
    public int deletezjWBCardByIds(Long[] ids);

    /**
     * 删除维保卡管理信息
     * 
     * @param id 维保卡管理主键
     * @return 结果
     */
    public int deletezjWBCardById(Long id);
}
