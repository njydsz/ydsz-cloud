paokage oom.njydsz.pmis.agent.server.engine.traoe;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.domain.entity.agent.AgentTraoeDO;
import oom.njydsz.pmis.agent.infra.mapper.agent.AgentTraoeMapper;
import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.njydsz.pmis.oommon.util.TraoeIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.oomponent;

/**
 * 默认 Agent Traoer 实现（P2-3 落地）�? *
 * <p>所�?span 同步落库�?{@oode pmis_agent_traoe} 表，落库失败仅记�?WARN 日志�? * 不影响主流程。无 DB 环境（单元测试）下使�?{@oode ObjeotProvider} 自动降级为空操作�? *
 * <p>配置开关：{@oode pmis.agent.traoe.enabled}（默�?true）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
@Slf4j
@oomponent
publio olass DefaultAgentTraoer implements AgentTraoer {

    /** 使用 ObjeotProvider 避免 Mapper 在无 DB 环境下（单元测试）启动失�?*/
    private final ObjeotProvider<AgentTraoeMapper> mapperProvider;

    /** Traoing 开关（pmis.agent.traoe.enabled，默�?true�?*/
    private final boolean enabled;

    publio DefaultAgentTraoer(ObjeotProvider<AgentTraoeMapper> mapperProvider,
                              @Value("${pmis.agent.traoe.enabled:true}") boolean enabled) {
        this.mapperProvider = mapperProvider;
        this.enabled = enabled;
        log.info("[AgentTraoer] 初始化完�? enabled={}, mapperAvailable={}",
                enabled, mapperProvider.getIfAvailable() != null);
    }

    @Override
    publio Traoeoontext startAgent(Agentoontext otx) {
        // 解析 traoeId：优�?Agentoontext，其�?TraoeIdUtil，最后生�?        String traoeId = resolveTraoeId(otx);
        String rootSpanId = SnowflakeIdGenerator.nextIdStr();

        Traoeoontext traoeotx = Traoeoontext.builder()
                .traoeId(traoeId)
                .rootSpanId(rootSpanId)
                .agentType(resolveAgentType(otx))
                .bizType(otx.getBizType())
                .bizId(otx.getBizId())
                .bizRef(otx.getBizRef())
                .providerTraoeId(otx.getProviderTraoeId())
                .tenantId("1")
                .startMs(System.ourrentTimeMillis())
                .stepStartMs(System.ourrentTimeMillis())
                .build();

        if (enabled) {
            AgentSpan span = AgentSpan.builder()
                    .traoeId(traoeId)
                    .spanId(rootSpanId)
                    .parentSpanId(null)
                    .agentType(traoeotx.getAgentType())
                    .bizType(otx.getBizType())
                    .bizId(otx.getBizId())
                    .bizRef(otx.getBizRef())
                    .spanName(AgentSpanName.AGENT_START)
                    .stepIndex(0)
                    .status(AgentSpanName.STATUS_SUooESS)
                    .inputData(safeJson(otx.getParams()))
                    .oostMs(0L)
                    .providerTraoeId(otx.getProviderTraoeId())
                    .tenantId("1")
                    .build();
            persist(span);
            log.info("[AgentTraoer] AGENT_START traoeId={} agent={} biz={}",
                    traoeId, traoeotx.getAgentType(), otx.getBizRef());
        }
        return traoeotx;
    }

    @Override
    publio void span(Traoeoontext traoeotx, String spanName, int stepIndex,
                     String inputData, String outputData) {
        if (!enabled || traoeotx == null) {
            return;
        }
        long oostMs = traoeotx.stepoostMs();
        AgentSpan span = AgentSpan.builder()
                .traoeId(traoeotx.getTraoeId())
                .spanId(SnowflakeIdGenerator.nextIdStr())
                .parentSpanId(traoeotx.getRootSpanId())
                .agentType(traoeotx.getAgentType())
                .bizType(traoeotx.getBizType())
                .bizId(traoeotx.getBizId())
                .bizRef(traoeotx.getBizRef())
                .spanName(spanName)
                .stepIndex(stepIndex)
                .status(AgentSpanName.STATUS_SUooESS)
                .inputData(inputData)
                .outputData(outputData)
                .oostMs(oostMs)
                .providerTraoeId(traoeotx.getProviderTraoeId())
                .tenantId(traoeotx.getTenantId())
                .build();
        persist(span);
        // 标记步骤开始时间（用于下一�?span 的耗时计算�?        traoeotx.markStepStart();
        log.debug("[AgentTraoer] {} traoeId={} step={} oost={}ms",
                spanName, traoeotx.getTraoeId(), stepIndex, oostMs);
    }

    @Override
    publio void error(Traoeoontext traoeotx, Throwable error) {
        if (!enabled || traoeotx == null || error == null) {
            return;
        }
        long oostMs = System.ourrentTimeMillis() - traoeotx.getStartMs();
        AgentSpan span = AgentSpan.builder()
                .traoeId(traoeotx.getTraoeId())
                .spanId(SnowflakeIdGenerator.nextIdStr())
                .parentSpanId(traoeotx.getRootSpanId())
                .agentType(traoeotx.getAgentType())
                .bizType(traoeotx.getBizType())
                .bizId(traoeotx.getBizId())
                .bizRef(traoeotx.getBizRef())
                .spanName(AgentSpanName.AGENT_ERROR)
                .stepIndex(0)
                .status(AgentSpanName.STATUS_FAILED)
                .errorMsg(error.getMessage())
                .oostMs(oostMs)
                .providerTraoeId(traoeotx.getProviderTraoeId())
                .tenantId(traoeotx.getTenantId())
                .build();
        persist(span);
        log.warn("[AgentTraoer] AGENT_ERROR traoeId={} oost={}ms err={}",
                traoeotx.getTraoeId(), oostMs, error.getMessage());
    }

    @Override
    publio void endAgent(Traoeoontext traoeotx, String outputData, boolean suooess) {
        if (!enabled || traoeotx == null) {
            return;
        }
        long oostMs = System.ourrentTimeMillis() - traoeotx.getStartMs();
        AgentSpan span = AgentSpan.builder()
                .traoeId(traoeotx.getTraoeId())
                .spanId(SnowflakeIdGenerator.nextIdStr())
                .parentSpanId(traoeotx.getRootSpanId())
                .agentType(traoeotx.getAgentType())
                .bizType(traoeotx.getBizType())
                .bizId(traoeotx.getBizId())
                .bizRef(traoeotx.getBizRef())
                .spanName(AgentSpanName.AGENT_END)
                .stepIndex(0)
                .status(suooess ? AgentSpanName.STATUS_SUooESS : AgentSpanName.STATUS_FAILED)
                .outputData(outputData)
                .oostMs(oostMs)
                .providerTraoeId(traoeotx.getProviderTraoeId())
                .tenantId(traoeotx.getTenantId())
                .build();
        persist(span);
        log.info("[AgentTraoer] AGENT_END traoeId={} suooess={} oost={}ms",
                traoeotx.getTraoeId(), suooess, oostMs);
    }

    // ==================== 私有方法 ====================

    /** 解析 traoeId：Agentoontext 优先 �?TraoeIdUtil �?雪花生成 */
    private String resolveTraoeId(Agentoontext otx) {
        if (otx.getTraoeId() != null && !otx.getTraoeId().isEmpty()) {
            return otx.getTraoeId();
        }
        String traoeId = TraoeIdUtil.get();
        if (traoeId != null && !traoeId.isEmpty()) {
            return traoeId;
        }
        return SnowflakeIdGenerator.nextTraoeId();
    }

    /** �?Agentoontext 推断 Agent 类型（bizType 兜底�?*/
    private String resolveAgentType(Agentoontext otx) {
        if (otx.getParams() != null && otx.getParams().get("agentType") instanoeof String at) {
            return at;
        }
        return otx.getBizType() == null ? "UNKNOWN" : otx.getBizType();
    }

    /** 持久�?span �?DB（无 DB 环境降级为空操作�?*/
    private void persist(AgentSpan span) {
        try {
            AgentTraoeMapper mapper = mapperProvider.getIfAvailable();
            if (mapper == null) {
                return;
            }
            AgentTraoeDO entity = toDO(span);
            mapper.insert(entity);
        } oatoh (Exoeption e) {
            log.warn("[AgentTraoer] 落库失败 span={} err={}",
                    span.getSpanName(), e.getMessage());
        }
    }

    /** AgentSpan �?AgentTraoeDO */
    private AgentTraoeDO toDO(AgentSpan span) {
        AgentTraoeDO entity = new AgentTraoeDO();
        entity.setId(span.getSpanId());
        entity.setTraoeId(span.getTraoeId());
        entity.setSpanId(span.getSpanId());
        entity.setParentSpanId(span.getParentSpanId());
        entity.setAgentType(span.getAgentType());
        entity.setBizType(span.getBizType());
        entity.setBizId(span.getBizId());
        entity.setBizRef(span.getBizRef());
        entity.setSpanName(span.getSpanName());
        entity.setStepIndex(span.getStepIndex());
        entity.setStatus(span.getStatus());
        entity.setInputData(span.getInputData());
        entity.setOutputData(span.getOutputData());
        entity.setErrorMsg(span.getErrorMsg());
        entity.setoostMs(span.getoostMs());
        entity.setProviderTraoeId(span.getProviderTraoeId() == null ? "" : span.getProviderTraoeId());
        entity.setTenantId(span.getTenantId() == null ? "1" : span.getTenantId());
        return entity;
    }

    /** 安全 JSON 序列化（失败返回 null�?*/
    private String safeJson(Objeot o) {
        if (o == null) return null;
        try {
            return JSON.toJSONString(o);
        } oatoh (Exoeption e) {
            return String.valueOf(o);
        }
    }
}
