package top.aameow.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.project.mapper.AgentMapper;
import top.aameow.project.domain.Agent;
import top.aameow.project.service.IAgentService;

/**
 * 中间人Service业务层处理
 * 
 * @author aa
 * @date 2026-01-23
 */
@Service
public class AgentServiceImpl implements IAgentService 
{
    @Autowired
    private AgentMapper agentMapper;

    /**
     * 查询中间人
     * 
     * @param id 中间人主键
     * @return 中间人
     */
    @Override
    public Agent selectAgentById(Long id)
    {
        return agentMapper.selectAgentById(id);
    }

    /**
     * 查询中间人列表
     * 
     * @param agent 中间人
     * @return 中间人
     */
    @Override
    public List<Agent> selectAgentList(Agent agent)
    {
        return agentMapper.selectAgentList(agent);
    }

    /**
     * 新增中间人
     * 
     * @param agent 中间人
     * @return 结果
     */
    @Override
    public int insertAgent(Agent agent)
    {
        return agentMapper.insertAgent(agent);
    }

    /**
     * 修改中间人
     * 
     * @param agent 中间人
     * @return 结果
     */
    @Override
    public int updateAgent(Agent agent)
    {
        return agentMapper.updateAgent(agent);
    }

    /**
     * 批量删除中间人
     * 
     * @param ids 需要删除的中间人主键
     * @return 结果
     */
    @Override
    public int deleteAgentByIds(Long[] ids)
    {
        return agentMapper.deleteAgentByIds(ids);
    }

    /**
     * 删除中间人信息
     * 
     * @param id 中间人主键
     * @return 结果
     */
    @Override
    public int deleteAgentById(Long id)
    {
        return agentMapper.deleteAgentById(id);
    }
}
