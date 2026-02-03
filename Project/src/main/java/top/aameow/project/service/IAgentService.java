package top.aameow.project.service;

import java.util.List;
import top.aameow.project.domain.Agent;

/**
 * 中间人Service接口
 * 
 * @author aa
 * @date 2026-01-23
 */
public interface IAgentService 
{
    /**
     * 查询中间人
     * 
     * @param id 中间人主键
     * @return 中间人
     */
    public Agent selectAgentById(Long id);

    /**
     * 查询中间人列表
     * 
     * @param agent 中间人
     * @return 中间人集合
     */
    public List<Agent> selectAgentList(Agent agent);

    /**
     * 新增中间人
     * 
     * @param agent 中间人
     * @return 结果
     */
    public int insertAgent(Agent agent);

    /**
     * 修改中间人
     * 
     * @param agent 中间人
     * @return 结果
     */
    public int updateAgent(Agent agent);

    /**
     * 批量删除中间人
     * 
     * @param ids 需要删除的中间人主键集合
     * @return 结果
     */
    public int deleteAgentByIds(Long[] ids);

    /**
     * 删除中间人信息
     * 
     * @param id 中间人主键
     * @return 结果
     */
    public int deleteAgentById(Long id);
}
