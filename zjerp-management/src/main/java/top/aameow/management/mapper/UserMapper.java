package top.aameow.management.mapper;

import java.util.List;
import top.aameow.management.domain.User;
import top.aameow.management.domain.UserFile;

/**
 * 员工资料Mapper接口
 * 
 * @author aa
 * @date 2025-11-07
 */
public interface UserMapper 
{
    /**
     * 查询员工资料
     * 
     * @param id 员工资料主键
     * @return 员工资料
     */
    public User selectUserById(Long id);

    /**
     * 查询员工资料列表
     * 
     * @param user 员工资料
     * @return 员工资料集合
     */
    public List<User> selectUserList(User user);

    /**
     * 新增员工资料
     * 
     * @param user 员工资料
     * @return 结果
     */
    public int insertUser(User user);

    /**
     * 修改员工资料
     * 
     * @param user 员工资料
     * @return 结果
     */
    public int updateUser(User user);

    /**
     * 删除员工资料
     * 
     * @param id 员工资料主键
     * @return 结果
     */
    public int deleteUserById(Long id);

    /**
     * 批量删除员工资料
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteUserByIds(Long[] ids);

    /**
     * 批量删除用户资料
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteUserFileByUserIds(Long[] ids);
    
    /**
     * 批量新增用户资料
     * 
     * @param userFileList 用户资料列表
     * @return 结果
     */
    public int batchUserFile(List<UserFile> userFileList);
    

    /**
     * 通过员工资料主键删除用户资料信息
     * 
     * @param id 员工资料ID
     * @return 结果
     */
    public int deleteUserFileByUserId(Long id);
}
