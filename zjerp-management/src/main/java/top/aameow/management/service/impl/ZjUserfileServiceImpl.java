package top.aameow.management.service.impl;

import java.util.List;
import top.aameow.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.management.domain.UserFileAndUser;
import top.aameow.management.mapper.ZjUserfileMapper;
import top.aameow.management.domain.ZjUserfile;
import top.aameow.management.service.IZjUserfileService;

/**
 * 资料附件Service业务层处理
 * 
 * @author aa
 * @date 2025-11-11
 */
@Service
public class ZjUserfileServiceImpl implements IZjUserfileService 
{
    @Autowired
    private ZjUserfileMapper zjUserfileMapper;

    /**
     * 查询资料附件
     * 
     * @param id 资料附件主键
     * @return 资料附件
     */
    @Override
    public ZjUserfile selectZjUserfileById(Long id)
    {
        return zjUserfileMapper.selectZjUserfileById(id);
    }

    /**
     * 查询资料附件列表
     * 
     * @param zjUserfile 资料附件
     * @return 资料附件
     */
    @Override
    public List<ZjUserfile> selectZjUserfileList(ZjUserfile zjUserfile)
    {
        return zjUserfileMapper.selectZjUserfileList(zjUserfile);
    }

    /**
     * 新增资料附件
     * 
     * @param zjUserfile 资料附件
     * @return 结果
     */
    @Override
    public int insertZjUserfile(ZjUserfile zjUserfile)
    {
        zjUserfile.setCreateTime(DateUtils.getNowDate());
        return zjUserfileMapper.insertZjUserfile(zjUserfile);
    }

    /**
     * 修改资料附件
     * 
     * @param zjUserfile 资料附件
     * @return 结果
     */
    @Override
    public int updateZjUserfile(ZjUserfile zjUserfile)
    {
        zjUserfile.setUpdateTime(DateUtils.getNowDate());
        return zjUserfileMapper.updateZjUserfile(zjUserfile);
    }

    /**
     * 批量删除资料附件
     * 
     * @param ids 需要删除的资料附件主键
     * @return 结果
     */
    @Override
    public int deleteZjUserfileByIds(Long[] ids)
    {
        return zjUserfileMapper.deleteZjUserfileByIds(ids);
    }

    /**
     * 删除资料附件信息
     * 
     * @param id 资料附件主键
     * @return 结果
     */
    @Override
    public int deleteZjUserfileById(Long id)
    {
        return zjUserfileMapper.deleteZjUserfileById(id);
    }

    @Override
    public List<UserFileAndUser> selectUserFileAndUserList(ZjUserfile zjUserfile) {
        return zjUserfileMapper.selectUserFileAndUserList(zjUserfile);
    }
}
