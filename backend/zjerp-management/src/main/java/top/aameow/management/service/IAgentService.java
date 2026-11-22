package top.aameow.management.service;

import java.util.List;
import top.aameow.management.domain.Agent;

/**
 * 代理人Service接口
 * 
 * @author aameow
 * @date 2026-10-16
 */
public interface IAgentService 
{
    /**
     * 查询代理人
     * 
     * @param id 代理人主键
     * @return 代理人
     */
    public Agent selectAgentById(Long id);

    /**
     * 查询代理人列表
     * 
     * @param agent 代理人
     * @return 代理人集合
     */
    public List<Agent> selectAgentList(Agent agent);

    /**
     * 新增代理人
     * 
     * @param agent 代理人
     * @return 结果
     */
    public int insertAgent(Agent agent);

    /**
     * 修改代理人
     * 
     * @param agent 代理人
     * @return 结果
     */
    public int updateAgent(Agent agent);

    /**
     * 批量删除代理人
     * 
     * @param ids 需要删除的代理人主键集合
     * @return 结果
     */
    public int deleteAgentByIds(Long[] ids);

    /**
     * 删除代理人信息
     * 
     * @param id 代理人主键
     * @return 结果
     */
    public int deleteAgentById(Long id);
}
