package top.aameow.management.mapper;

import java.util.List;
import top.aameow.management.domain.Agent;

/**
 * 代理人Mapper接口
 * 
 * @author aameow
 * @date 2026-10-16
 */
public interface AgentMapper 
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
     * 删除代理人
     * 
     * @param id 代理人主键
     * @return 结果
     */
    public int deleteAgentById(Long id);

    /**
     * 批量删除代理人
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAgentByIds(Long[] ids);
}
