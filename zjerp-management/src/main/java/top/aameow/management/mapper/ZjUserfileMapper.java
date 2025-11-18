package top.aameow.management.mapper;

import java.util.List;

import top.aameow.management.domain.UserFileAndUser;
import top.aameow.management.domain.ZjUserfile;

/**
 * 资料附件Mapper接口
 * 
 * @author aa
 * @date 2025-11-11
 */
public interface ZjUserfileMapper 
{
    /**
     * 查询资料附件
     * 
     * @param id 资料附件主键
     * @return 资料附件
     */
    public ZjUserfile selectZjUserfileById(Long id);

    /**
     * 查询资料附件列表
     * 
     * @param zjUserfile 资料附件
     * @return 资料附件集合
     */
    public List<ZjUserfile> selectZjUserfileList(ZjUserfile zjUserfile);

    /**
     * 新增资料附件
     * 
     * @param zjUserfile 资料附件
     * @return 结果
     */
    public int insertZjUserfile(ZjUserfile zjUserfile);

    /**
     * 修改资料附件
     * 
     * @param zjUserfile 资料附件
     * @return 结果
     */
    public int updateZjUserfile(ZjUserfile zjUserfile);

    /**
     * 删除资料附件
     * 
     * @param id 资料附件主键
     * @return 结果
     */
    public int deleteZjUserfileById(Long id);

    /**
     * 批量删除资料附件
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteZjUserfileByIds(Long[] ids);

    public List<UserFileAndUser> selectUserFileAndUserList(ZjUserfile zjUserfile);
}
