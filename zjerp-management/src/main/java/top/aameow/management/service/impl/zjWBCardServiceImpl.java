package top.aameow.management.service.impl;

import java.util.List;
import top.aameow.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.management.mapper.zjWBCardMapper;
import top.aameow.management.domain.zjWBCard;
import top.aameow.management.service.IzjWBCardService;

/**
 * 维保卡管理Service业务层处理
 * 
 * @author aa
 * @date 2025-11-05
 */
@Service
public class zjWBCardServiceImpl implements IzjWBCardService 
{
    @Autowired
    private zjWBCardMapper zjWBCardMapper;

    /**
     * 查询维保卡管理
     * 
     * @param id 维保卡管理主键
     * @return 维保卡管理
     */
    @Override
    public zjWBCard selectzjWBCardById(Long id)
    {
        return zjWBCardMapper.selectzjWBCardById(id);
    }

    /**
     * 查询维保卡管理列表
     * 
     * @param zjWBCard 维保卡管理
     * @return 维保卡管理
     */
    @Override
    public List<zjWBCard> selectzjWBCardList(zjWBCard zjWBCard)
    {
        return zjWBCardMapper.selectzjWBCardList(zjWBCard);
    }

    /**
     * 新增维保卡管理
     * 
     * @param zjWBCard 维保卡管理
     * @return 结果
     */
    @Override
    public int insertzjWBCard(zjWBCard zjWBCard)
    {
        zjWBCard.setCreateTime(DateUtils.getNowDate());
        return zjWBCardMapper.insertzjWBCard(zjWBCard);
    }

    /**
     * 修改维保卡管理
     * 
     * @param zjWBCard 维保卡管理
     * @return 结果
     */
    @Override
    public int updatezjWBCard(zjWBCard zjWBCard)
    {
        zjWBCard.setUpdateTime(DateUtils.getNowDate());
        return zjWBCardMapper.updatezjWBCard(zjWBCard);
    }

    /**
     * 批量删除维保卡管理
     * 
     * @param ids 需要删除的维保卡管理主键
     * @return 结果
     */
    @Override
    public int deletezjWBCardByIds(Long[] ids)
    {
        return zjWBCardMapper.deletezjWBCardByIds(ids);
    }

    /**
     * 删除维保卡管理信息
     * 
     * @param id 维保卡管理主键
     * @return 结果
     */
    @Override
    public int deletezjWBCardById(Long id)
    {
        return zjWBCardMapper.deletezjWBCardById(id);
    }
}
