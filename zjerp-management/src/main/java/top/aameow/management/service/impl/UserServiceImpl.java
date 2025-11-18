package top.aameow.management.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import top.aameow.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import top.aameow.management.domain.UserFile;
import top.aameow.management.mapper.UserMapper;
import top.aameow.management.domain.User;
import top.aameow.management.service.IUserService;

/**
 * 员工资料Service业务层处理
 * 
 * @author aa
 * @date 2025-11-07
 */
@Service
public class UserServiceImpl implements IUserService 
{
    @Autowired
    private UserMapper userMapper;

    /**
     * 查询员工资料
     * 
     * @param id 员工资料主键
     * @return 员工资料
     */
    @Override
    public User selectUserById(Long id)
    {
        return userMapper.selectUserById(id);
    }

    /**
     * 查询员工资料列表
     * 
     * @param user 员工资料
     * @return 员工资料
     */
    @Override
    public List<User> selectUserList(User user)
    {
        return userMapper.selectUserList(user);
    }

    /**
     * 新增员工资料
     * 
     * @param user 员工资料
     * @return 结果
     */
    @Transactional
    @Override
    public int insertUser(User user)
    {
        int rows = userMapper.insertUser(user);
        insertUserFile(user);
        return rows;
    }

    /**
     * 修改员工资料
     * 
     * @param user 员工资料
     * @return 结果
     */
    @Transactional
    @Override
    public int updateUser(User user)
    {
        userMapper.deleteUserFileByUserId(user.getId());
        insertUserFile(user);
        return userMapper.updateUser(user);
    }

    /**
     * 批量删除员工资料
     * 
     * @param ids 需要删除的员工资料主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteUserByIds(Long[] ids)
    {
        userMapper.deleteUserFileByUserIds(ids);
        return userMapper.deleteUserByIds(ids);
    }

    /**
     * 删除员工资料信息
     * 
     * @param id 员工资料主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteUserById(Long id)
    {
        userMapper.deleteUserFileByUserId(id);
        return userMapper.deleteUserById(id);
    }

    /**
     * 新增用户资料信息
     * 
     * @param user 员工资料对象
     */
    public void insertUserFile(User user)
    {
        List<UserFile> userFileList = user.getUserFileList();
        Long id = user.getId();
        if (StringUtils.isNotNull(userFileList))
        {
            List<UserFile> list = new ArrayList<UserFile>();
            for (UserFile userFile : userFileList)
            {
                userFile.setUserId(id);
                list.add(userFile);
            }
            if (list.size() > 0)
            {
                userMapper.batchUserFile(list);
            }
        }
    }
}
