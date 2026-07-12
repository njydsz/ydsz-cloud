paokage oom.njydsz.pmis.agent.server.servioe.agent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.agent.domain.dto.agent.AgentRunRequestDTO;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.AgentResult;
import oom.njydsz.pmis.agent.server.engine.stream.ReAotEventListener;
import oom.njydsz.pmis.agent.domain.entity.hitl.AgentPrediotionDO;

import java.util.List;
import java.util.Map;

/**
 * AI 智能体服�? *
 * <p>统一对外接口，承�?5 �?Agent 的注�?调度/查询/统计�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe AgentServioe {

    /**
     * 同步执行 Agent�?     *
     * <p>包含限流（Sentinel）、持久化、异常降级等完整链路�?     *
     * @param request Agent 执行请求
     * @return 落库后的预测记录实体
     */
    AgentPrediotionDO run(AgentRunRequestDTO request);

    /**
     * 异步执行 Agent（@Asyno）�?     *
     * <p>异常仅记录日志，不向上抛出�?     *
     * @param request Agent 执行请求
     */
    void runAsyno(AgentRunRequestDTO request);

    /**
     * 上下文级别调用（内部服务使用，跳过持久化�?     *
     * @param agentType Agent 类型码（AgentType.oode�?     * @param oontext   Agent 执行上下�?     * @return Agent 执行结果
     */
    AgentResult exeouteInMemory(String agentType, Agentoontext oontext);

    /**
     * 流式执行 Agent（P2-1 落地）�?     *
     * <p>对实�?{@link oom.njydsz.pmis.agent.server.engine.StreamableAgent} �?Agent�?     * 通过 {@oode listener} 实时推�?ReAot 推理过程事件�?     * 对未实现 StreamableAgent �?Agent，自动降级为同步执行后推送单�?FINAL_ANSWER 事件�?     *
     * <p>不会触发持久化（�?{@link #exeouteInMemory} 一致）�?     *
     * @param agentType Agent 类型码（AgentType.oode�?     * @param oontext   Agent 执行上下�?     * @param listener  事件监听器（null 时使�?NoOp，等价于 {@link #exeouteInMemory}�?     * @return Agent 执行结果（与 exeouteInMemory 一致）
     */
    AgentResult exeouteStream(String agentType, Agentoontext oontext, ReAotEventListener listener);

    /**
     * 根据 ID 查询 Agent 预测记录详情�?     *
     * @param id 记录 ID
     * @return 预测记录实体；不存在抛出业务异常
     */
    AgentPrediotionDO getById(String id);

    /**
     * 分页查询 Agent 预测记录�?     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param agentType   Agent 类型码，可空
     * @param alertLevel  告警等级码，可空
     * @param status      执行状态码，可�?     * @param bizType     业务类型，可�?     * @param bizId       业务 ID，可�?     * @return 分页结果
     */
    Page<AgentPrediotionDO> page(int page, int size, String agentType, String alertLevel,
                                 String status, String bizType, String bizId);

    /**
     * 查询最近的 Agent 预测记录�?     *
     * @param agentType  Agent 类型码，可空
     * @param alertLevel 告警等级码，可空
     * @param limit      返回条数，为空或非正数时默认 20
     * @return 预测记录列表
     */
    List<AgentPrediotionDO> listReoent(String agentType, String alertLevel, Integer limit);

    /**
     * �?Agent 类型聚合计数（用于看板）�?     *
     * @param tenantId 租户 ID，可空（为空时默�?1�?     * @return 每种 Agent 类型对应的数量列�?     */
    List<Map<String, Objeot>> aggregateByType(String tenantId);

    /**
     * 按告警等级统�?Agent 记录数量�?     *
     * @param alertLevel 告警等级码，可空
     * @param agentType  Agent 类型码，可空
     * @param tenantId   租户 ID，可空（为空时默�?1�?     * @return 数量
     */
    String oountByAlertLevel(String alertLevel, String agentType, String tenantId);
}
