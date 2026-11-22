package top.aameow.management.service.impl;

import java.util.List;
import top.aameow.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.management.domain.WBCardPhone;
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
    private top.aameow.management.service.BusinessFiles businessFiles;

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
        zjWBCard record = zjWBCardMapper.selectzjWBCardById(id);
        hydrateFiles(record);
        return record;
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
        List<zjWBCard> records = zjWBCardMapper.selectzjWBCardList(zjWBCard);
        records.forEach(this::hydrateFiles);
        return records;
    }

    /**
     * 新增维保卡管理
     * 
     * @param zjWBCard 维保卡管理
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int insertzjWBCard(zjWBCard zjWBCard)
    {
        zjWBCard.setCreateTime(DateUtils.getNowDate());
        int rows = zjWBCardMapper.insertzjWBCard(zjWBCard);
        if (rows > 0)
        {
            businessFiles.save("wbcard", zjWBCard.getId(), zjWBCard.getFile());
        }
        return rows;
    }

    /**
     * 修改维保卡管理
     * 
     * @param zjWBCard 维保卡管理
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int updatezjWBCard(zjWBCard zjWBCard)
    {
        zjWBCard.setUpdateTime(DateUtils.getNowDate());
        int rows = zjWBCardMapper.updatezjWBCard(zjWBCard);
        if (rows > 0)
        {
            businessFiles.save("wbcard", zjWBCard.getId(), zjWBCard.getFile());
        }
        return rows;
    }

    /**
     * 批量删除维保卡管理
     * 
     * @param ids 需要删除的维保卡管理主键
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int deletezjWBCardByIds(Long[] ids)
    {
        int rows = zjWBCardMapper.deletezjWBCardByIds(ids);
        for (Long fileBizId : ids)
        {
            businessFiles.save("wbcard", fileBizId, "");
        }
        return rows;
    }

    /**
     * 删除维保卡管理信息
     * 
     * @param id 维保卡管理主键
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public int deletezjWBCardById(Long id)
    {
        int rows = zjWBCardMapper.deletezjWBCardById(id);
        for (Long fileBizId : new Long[] { id })
        {
            businessFiles.save("wbcard", fileBizId, "");
        }
        return rows;
    }

    /**
     * 查询生成维保卡时可用的手机号列表
     * @return 手机号列表
     */
    @Override
    public List<WBCardPhone> getPhoneList(WBCardPhone phone) {
        return zjWBCardMapper.getPhoneList(phone);
    }

    /**
     * 添加新的手机号
     * @param phone 手机号
     * @return
     */
    @Override
    public int insertWBCardPhone(WBCardPhone phone) {
        return zjWBCardMapper.insertWBCardPhone(phone);
    }

    /**
     * 删除手机号
     * @param id 要删除的主键列表
     * @return
     */
    @Override
    public int deleteWBCardPhoneById(Long id) {
        return zjWBCardMapper.deleteWBCardPhoneById(id);
    }
    private void hydrateFiles(zjWBCard record)
    {
        if (record == null) return;
        record.setFile(businessFiles.read("wbcard", record.getId(), record.getFile()));
    }
}
