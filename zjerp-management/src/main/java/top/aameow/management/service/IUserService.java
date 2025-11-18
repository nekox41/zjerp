package top.aameow.management.service;

import java.util.List;
import top.aameow.management.domain.User;

/**
 * 员工资料Service接口
 * 
 * @author aa
 * @date 2025-11-07
 */
public interface IUserService 
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
     * 批量删除员工资料
     * 
     * @param ids 需要删除的员工资料主键集合
     * @return 结果
     */
    public int deleteUserByIds(Long[] ids);

    /**
     * 删除员工资料信息
     * 
     * @param id 员工资料主键
     * @return 结果
     */
    public int deleteUserById(Long id);
}
