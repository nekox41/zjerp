package top.aameow.management.service.impl;

import java.util.List;
import top.aameow.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.management.mapper.AgentMapper;
import top.aameow.management.domain.Agent;
import top.aameow.management.service.IAgentService;

/**
 * 代理人Service业务层处理
 * 
 * @author aameow
 * @date 2026-10-16
 */
@Service
public class AgentServiceImpl implements IAgentService 
{
    @Autowired
    private AgentMapper agentMapper;

    /**
     * 查询代理人
     * 
     * @param id 代理人主键
     * @return 代理人
     */
    @Override
    public Agent selectAgentById(Long id)
    {
        return agentMapper.selectAgentById(id);
    }

    /**
     * 查询代理人列表
     * 
     * @param agent 代理人
     * @return 代理人
     */
    @Override
    public List<Agent> selectAgentList(Agent agent)
    {
        return agentMapper.selectAgentList(agent);
    }

    /**
     * 新增代理人
     * 
     * @param agent 代理人
     * @return 结果
     */
    @Override
    public int insertAgent(Agent agent)
    {
        agent.setCreateTime(DateUtils.getNowDate());
        return agentMapper.insertAgent(agent);
    }

    /**
     * 修改代理人
     * 
     * @param agent 代理人
     * @return 结果
     */
    @Override
    public int updateAgent(Agent agent)
    {
        agent.setUpdateTime(DateUtils.getNowDate());
        return agentMapper.updateAgent(agent);
    }

    /**
     * 批量删除代理人
     * 
     * @param ids 需要删除的代理人主键
     * @return 结果
     */
    @Override
    public int deleteAgentByIds(Long[] ids)
    {
        return agentMapper.deleteAgentByIds(ids);
    }

    /**
     * 删除代理人信息
     * 
     * @param id 代理人主键
     * @return 结果
     */
    @Override
    public int deleteAgentById(Long id)
    {
        return agentMapper.deleteAgentById(id);
    }
}
