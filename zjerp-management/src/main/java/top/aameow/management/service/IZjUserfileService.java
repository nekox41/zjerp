package top.aameow.management.service;

import java.util.List;

import top.aameow.management.domain.UserFileAndUser;
import top.aameow.management.domain.ZjUserfile;

/**
 * 资料附件Service接口
 * 
 * @author aa
 * @date 2025-11-11
 */
public interface IZjUserfileService 
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
     * 批量删除资料附件
     * 
     * @param ids 需要删除的资料附件主键集合
     * @return 结果
     */
    public int deleteZjUserfileByIds(Long[] ids);

    /**
     * 删除资料附件信息
     * 
     * @param id 资料附件主键
     * @return 结果
     */
    public int deleteZjUserfileById(Long id);

    public List<UserFileAndUser> selectUserFileAndUserList(ZjUserfile zjUserfile);
}
