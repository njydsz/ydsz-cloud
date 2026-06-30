package com.njydsz.pmis.agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.AgentRunRequestDTO;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;

import java.util.List;
import java.util.Map;

/**
 * AI 智能体服务
 *
 * <p>统一对外接口，承载 5 类 Agent 的注册/调度/查询/统计。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AgentService {

    /**
     * 同步执行 Agent
     */
    AgentPredictionDO run(AgentRunRequestDTO request);

    /**
     * 异步执行 Agent（@Async）
     */
    void runAsync(AgentRunRequestDTO request);

    /**
     * 上下文级别调用（内部服务使用，跳过持久化）
     */
    AgentResult executeInMemory(String agentType, AgentContext context);

    AgentPredictionDO getById(Long id);

    Page<AgentPredictionDO> page(int page, int size, String agentType, String alertLevel,
                                 String status, String bizType, Long bizId);

    List<AgentPredictionDO> listRecent(String agentType, String alertLevel, Integer limit);

    List<Map<String, Object>> aggregateByType(Long tenantId);

    long countByAlertLevel(String alertLevel, String agentType, Long tenantId);
}
