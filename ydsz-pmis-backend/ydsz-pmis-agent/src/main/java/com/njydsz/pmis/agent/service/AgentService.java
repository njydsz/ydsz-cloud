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
     * 同步执行 Agent。
     *
     * <p>包含限流（Sentinel）、持久化、异常降级等完整链路。
     *
     * @param request Agent 执行请求
     * @return 落库后的预测记录实体
     */
    AgentPredictionDO run(AgentRunRequestDTO request);

    /**
     * 异步执行 Agent（@Async）。
     *
     * <p>异常仅记录日志，不向上抛出。
     *
     * @param request Agent 执行请求
     */
    void runAsync(AgentRunRequestDTO request);

    /**
     * 上下文级别调用（内部服务使用，跳过持久化）
     *
     * @param agentType Agent 类型码（AgentType.code）
     * @param context   Agent 执行上下文
     * @return Agent 执行结果
     */
    AgentResult executeInMemory(String agentType, AgentContext context);

    /**
     * 根据 ID 查询 Agent 预测记录详情。
     *
     * @param id 记录 ID
     * @return 预测记录实体；不存在抛出业务异常
     */
    AgentPredictionDO getById(Long id);

    /**
     * 分页查询 Agent 预测记录。
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param agentType   Agent 类型码，可空
     * @param alertLevel  告警等级码，可空
     * @param status      执行状态码，可空
     * @param bizType     业务类型，可空
     * @param bizId       业务 ID，可空
     * @return 分页结果
     */
    Page<AgentPredictionDO> page(int page, int size, String agentType, String alertLevel,
                                 String status, String bizType, String bizId);

    /**
     * 查询最近的 Agent 预测记录。
     *
     * @param agentType  Agent 类型码，可空
     * @param alertLevel 告警等级码，可空
     * @param limit      返回条数，为空或非正数时默认 20
     * @return 预测记录列表
     */
    List<AgentPredictionDO> listRecent(String agentType, String alertLevel, Integer limit);

    /**
     * 按 Agent 类型聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID，可空（为空时默认 1）
     * @return 每种 Agent 类型对应的数量列表
     */
    List<Map<String, Object>> aggregateByType(Long tenantId);

    /**
     * 按告警等级统计 Agent 记录数量。
     *
     * @param alertLevel 告警等级码，可空
     * @param agentType  Agent 类型码，可空
     * @param tenantId   租户 ID，可空（为空时默认 1）
     * @return 数量
     */
    long countByAlertLevel(String alertLevel, String agentType, Long tenantId);
}
