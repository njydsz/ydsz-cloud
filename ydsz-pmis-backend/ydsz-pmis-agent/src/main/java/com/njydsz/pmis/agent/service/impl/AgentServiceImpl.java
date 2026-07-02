package com.njydsz.pmis.agent.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.AgentRunRequestDTO;
import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentRunStatus;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.mapper.AgentPredictionMapper;
import com.njydsz.pmis.agent.service.AgentService;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 智能体服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    /** 已注册的 Agent 列表（Spring 自动注入） */
    private final List<Agent> agents;
    /** Agent 预测记录 Mapper */
    private final AgentPredictionMapper predictionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @SentinelResource(value = "agent:run", blockHandler = "runBlockHandler", fallback = "runFallback")
    public AgentPredictionDO run(AgentRunRequestDTO req) {
        AgentType type = validate(req);
        Agent agent = findAgent(type);

        AgentContext ctx = new AgentContext(req.getBizType(), req.getBizId(), req.getBizRef(),
                req.getCallerId(), req.getCallerName(), req.getSource(), req.getParams());

        AgentPredictionDO record = new AgentPredictionDO();
        record.setTaskCode(buildTaskCode(type, req.getBizId()));
        record.setAgentType(type.getCode());
        record.setBizType(req.getBizType());
        record.setBizId(req.getBizId());
        record.setBizRef(req.getBizRef());
        record.setInputSnapshot(safeJson(req.getParams()));
        record.setModelVersion("v1.0.0");
        record.setStatus(AgentRunStatus.RUNNING.getCode());
        record.setCallerId(req.getCallerId());
        record.setCallerName(req.getCallerName());
        record.setSource(StringUtils.hasText(req.getSource()) ? req.getSource() : "MANUAL");
        record.setTenantId(1L);
        record.setProviderTraceId(TraceIdUtil.get() == null ? "" : TraceIdUtil.get());
        predictionMapper.insert(record);

        long t0 = System.currentTimeMillis();
        AgentResult result;
        try {
            result = agent.execute(ctx);
        } catch (Exception e) {
            log.error("[Agent] 执行失败: type={} biz={}", type, req.getBizRef(), e);
            record.setStatus(AgentRunStatus.FAILED.getCode());
            record.setErrorMsg(e.getMessage());
            record.setCostMs(System.currentTimeMillis() - t0);
            predictionMapper.updateById(record);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "error.agent.msg_eaf40df5" + e.getMessage());
        }
        long cost = System.currentTimeMillis() - t0;
        record.setAlertLevel(result.getAlertLevel() == null ? AgentAlertLevel.NORMAL.getCode()
                : result.getAlertLevel().getCode());
        record.setScore(result.getScore());
        record.setConfidence(result.getConfidence());
        record.setSuggestion(result.getSuggestion());
        record.setMatchedRules(result.getMatchedRules() == null ? null
                : safeJson(result.getMatchedRules()));
        record.setOutputResult(safeJson(result.getPayload()));
        record.setCostMs(cost);
        record.setStatus(AgentRunStatus.SUCCESS.getCode());
        predictionMapper.updateById(record);

        log.info("[Agent] 执行成功: type={} biz={} score={} level={} cost={}ms",
                type, req.getBizRef(), result.getScore(), result.getAlertLevel(), cost);
        return record;
    }

    /**
     * Sentinel 限流 BlockException 处理
     *
     * @param req 原始请求
     * @param ex  限流异常
     * @return 不返回（抛出业务异常）
     */
    public AgentPredictionDO runBlockHandler(AgentRunRequestDTO req, BlockException ex) {
        log.warn("[Agent] Sentinel 限流: {}", ex.getClass().getSimpleName());
        throw new BizException(BizErrorCode.RATE_LIMIT, "error.agent.msg_e12dc2f2");
    }

    /**
     * Sentinel 降级 fallback 处理
     *
     * @param req 原始请求
     * @param e   业务异常
     * @return 不返回（抛出业务异常）
     */
    public AgentPredictionDO runFallback(AgentRunRequestDTO req, Throwable e) {
        log.error("[Agent] Sentinel 降级: {}", e.getMessage());
        throw new BizException(BizErrorCode.SERVICE_UNAVAILABLE, "error.agent.msg_8536a322");
    }

    @Override
    @Async
    public void runAsync(AgentRunRequestDTO req) {
        try {
            run(req);
        } catch (Exception e) {
            log.error("[Agent] 异步执行失败: type={} biz={}", req.getAgentType(), req.getBizRef(), e);
        }
    }

    @Override
    public AgentResult executeInMemory(String agentType, AgentContext context) {
        AgentType type = AgentType.fromCode(agentType);
        if (type == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_3e4d9788" + agentType);
        }
        Agent agent = findAgent(type);
        return agent.execute(context);
    }

    @Override
    public AgentPredictionDO getById(Long id) {
        AgentPredictionDO r = predictionMapper.selectById(id);
        if (r == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.agent.msg_99e3df42");
        }
        return r;
    }

    @Override
    public Page<AgentPredictionDO> page(int page, int size, String agentType, String alertLevel,
                                        String status, String bizType, Long bizId) {
        Page<AgentPredictionDO> p = new Page<>(page, size);
        LambdaQueryWrapper<AgentPredictionDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(agentType)) w.eq(AgentPredictionDO::getAgentType, agentType);
        if (StringUtils.hasText(alertLevel)) w.eq(AgentPredictionDO::getAlertLevel, alertLevel);
        if (StringUtils.hasText(status)) w.eq(AgentPredictionDO::getStatus, status);
        if (StringUtils.hasText(bizType)) w.eq(AgentPredictionDO::getBizType, bizType);
        if (bizId != null) w.eq(AgentPredictionDO::getBizId, bizId);
        w.orderByDesc(AgentPredictionDO::getCreatedAt);
        return predictionMapper.selectPage(p, w);
    }

    @Override
    public List<AgentPredictionDO> listRecent(String agentType, String alertLevel, Integer limit) {
        if (limit == null || limit <= 0) limit = 20;
        return predictionMapper.selectByAgentType(agentType, alertLevel, limit);
    }

    @Override
    public List<Map<String, Object>> aggregateByType(Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return predictionMapper.aggregateByType(tenantId);
    }

    @Override
    public long countByAlertLevel(String alertLevel, String agentType, Long tenantId) {
        if (tenantId == null) tenantId = 1L;
        return predictionMapper.countByAlertLevel(alertLevel, agentType, tenantId);
    }

    // ========== 私有方法 ==========

    private Agent findAgent(AgentType type) {
        return agents.stream()
                .filter(a -> a.type() == type)
                .findFirst()
                .orElseThrow(() -> new BizException(BizErrorCode.BAD_REQUEST,
                        "未注册 Agent: " + type.getCode()));
    }

    private AgentType validate(AgentRunRequestDTO req) {
        if (req == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_d9712a58");
        }
        AgentType type = AgentType.fromCode(req.getAgentType());
        if (type == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_3e4d9788" + req.getAgentType());
        }
        return type;
    }

    private String buildTaskCode(AgentType type, Long bizId) {
        return LocalDate.now().toString().replace("-", "")
                + "-" + type.getCode() + "-"
                + (bizId == null ? "0" : bizId)
                + "-" + System.currentTimeMillis() % 100000;
    }

    private String safeJson(Object o) {
        if (o == null) return null;
        try {
            return JSON.toJSONString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
