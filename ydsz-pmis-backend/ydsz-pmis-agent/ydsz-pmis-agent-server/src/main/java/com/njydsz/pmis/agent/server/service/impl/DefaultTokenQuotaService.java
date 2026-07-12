package com.njydsz.pmis.agent.server.service.impl.tool;

import com.njydsz.pmis.agent.server.config.TokenQuotaProperties;
import com.njydsz.pmis.agent.domain.dto.tool.QuotaSummary;
import com.njydsz.pmis.agent.domain.dto.tool.TokenUsage;
import com.njydsz.pmis.agent.domain.entity.tool.TokenQuotaDO;
import com.njydsz.pmis.agent.domain.entity.tool.TokenUsageLogDO;
import com.njydsz.pmis.agent.infra.mapper.tool.TokenQuotaMapper;
import com.njydsz.pmis.agent.infra.mapper.tool.TokenUsageLogMapper;
import com.njydsz.pmis.agent.server.service.tool.TokenQuotaService;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 默认 Token 配额服务实现（P2-4 落地）。
 *
 * <p>核心设计：
 * <ul>
 *   <li>使用 {@link TokenQuotaMapper#incrementUsedTokens} 的 SQL 原子性保证并发安全</li>
 *   <li>使用 {@code ObjectProvider} 避免 Mapper 在无 DB 环境（单元测试）下启动失败</li>
 *   <li>配置开关 {@code pmis.agent.token-quota.enabled=false} 时所有方法降级为空操作</li>
 *   <li>首次访问时自动初始化当月配额（{@code autoInit=true} 时）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Slf4j
@Service
public class DefaultTokenQuotaService implements TokenQuotaService {

    /** 月份格式化器（YYYYMM） */
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    /** Token 配额 Mapper 提供者（延迟注入，无 DB 环境降级为空操作） */
    private final ObjectProvider<TokenQuotaMapper> quotaMapperProvider;
    /** Token 使用明细 Mapper 提供者（延迟注入） */
    private final ObjectProvider<TokenUsageLogMapper> usageLogMapperProvider;
    /** Token 配额配置属性 */
    private final TokenQuotaProperties properties;

    /**
     * 构造函数。
     *
     * @param quotaMapperProvider  Token 配额 Mapper 提供者
     * @param usageLogMapperProvider Token 使用明细 Mapper 提供者
     * @param properties           Token 配额配置属性
     */
    public DefaultTokenQuotaService(
            ObjectProvider<TokenQuotaMapper> quotaMapperProvider,
            ObjectProvider<TokenUsageLogMapper> usageLogMapperProvider,
            TokenQuotaProperties properties) {
        this.quotaMapperProvider = quotaMapperProvider;
        this.usageLogMapperProvider = usageLogMapperProvider;
        this.properties = properties;
        log.info("[TokenQuota] 初始化完成, enabled={}, defaultMonthlyQuota={}, autoInit={}",
                properties.isEnabled(), properties.getDefaultMonthlyQuota(), properties.isAutoInit());
    }

    /**
     * 检查租户当月 Token 配额是否充足
     *
     * <p>配额开关关闭时直接放行；首次访问时自动初始化当月配额（autoInit=true 时）。
     *
     * @param tenantId       租户 ID
     * @param estimatedTokens 预估 Token 消耗量
     * @throws SysException 配额不足时抛出（QUOTA_EXCEEDED）
     */
    @Override
    public void checkQuota(String tenantId, long estimatedTokens) {
        if (!properties.isEnabled()) {
            return;
        }
        TokenQuotaMapper mapper = quotaMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        String month = currentMonth();
        TokenQuotaDO quota = getOrCreateQuota(mapper, tenantId, month);
        if (quota == null) {
            return;
        }
        long remaining = quota.getTotalQuota() - quota.getUsedTokens();
        if (estimatedTokens > remaining) {
            log.warn("[TokenQuota] 配额不足: tenant={} month={} needed={} remaining={}",
                    tenantId, month, estimatedTokens, remaining);
            throw new SysException(StandardResultCode.QUOTA_EXCEEDED,
                    "error.agent.token_quota_exceeded",
                    tenantId, month, estimatedTokens, remaining);
        }
    }

    /**
     * 记录 Token 使用量并递增配额
     *
     * <p>无论配额开关是否启用，都会写入使用明细日志（供审计/账单核对）。
     * 仅当配额开关启用时，才递增租户当月已用配额（SQL 原子递增）。
     *
     * @param usage Token 使用量信息（含租户、模型、Token 数、耗时等）
     */
    @Override
    public void recordUsage(TokenUsage usage) {
        if (usage == null) {
            return;
        }
        // 1. 写入使用明细（无论是否启用配额限制，都记录明细）
        TokenUsageLogMapper logMapper = usageLogMapperProvider.getIfAvailable();
        if (logMapper != null) {
            try {
                TokenUsageLogDO logDO = toLogDO(usage);
                logMapper.insert(logDO);
            } catch (Exception e) {
                log.warn("[TokenQuota] 写入使用明细失败: tenant={} err={}",
                        usage.getTenantId(), e.getMessage());
            }
        }
        // 2. 递增配额（仅启用时）
        if (!properties.isEnabled()) {
            return;
        }
        TokenQuotaMapper mapper = quotaMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            int total = usage.computeTotal();
            if (total <= 0) {
                return;
            }
            String month = currentMonth();
            TokenQuotaDO quota = getOrCreateQuota(mapper, usage.getTenantId(), month);
            if (quota == null) {
                return;
            }
            int updated = mapper.incrementUsedTokens(quota.getId(), total);
            if (updated == 0) {
                log.warn("[TokenQuota] 递增配额失败（可能已超限）: tenant={} delta={} used={}/{}",
                        usage.getTenantId(), total, quota.getUsedTokens(), quota.getTotalQuota());
            } else {
                log.debug("[TokenQuota] 递增配额成功: tenant={} delta={} total={}",
                        usage.getTenantId(), total, total);
            }
        } catch (Exception e) {
            log.warn("[TokenQuota] 记录使用量异常: tenant={} err={}",
                    usage.getTenantId(), e.getMessage());
        }
    }

    /**
     * 查询租户当月配额概览
     *
     * @param tenantId 租户 ID
     * @return 配额概览（总量、已用、状态、重置时间）；无记录时返回默认概览
     */
    @Override
    public QuotaSummary getQuotaSummary(String tenantId) {
        TokenQuotaMapper mapper = quotaMapperProvider.getIfAvailable();
        if (mapper == null) {
            return defaultSummary(tenantId);
        }
        String month = currentMonth();
        TokenQuotaDO quota = mapper.selectByTenantAndMonth(tenantId, month);
        if (quota == null) {
            return defaultSummary(tenantId);
        }
        return QuotaSummary.of(tenantId, month, quota.getTotalQuota(),
                quota.getUsedTokens(), quota.getStatus(), quota.getResetAt());
    }

    /**
     * 重置租户当月配额（运维操作）
     *
     * <p>将已用 Token 清零，状态重置为 ACTIVE，记录重置时间。
     *
     * @param tenantId 租户 ID
     */
    @Override
    public void resetQuota(String tenantId) {
        TokenQuotaMapper mapper = quotaMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        String month = currentMonth();
        TokenQuotaDO quota = mapper.selectByTenantAndMonth(tenantId, month);
        if (quota == null) {
            return;
        }
        quota.setUsedTokens(0L);
        quota.setStatus("ACTIVE");
        quota.setResetAt(LocalDateTime.now());
        mapper.updateById(quota);
        log.info("[TokenQuota] 配额已重置: tenant={} month={}", tenantId, month);
    }

    // ==================== 私有方法 ====================

    /** 获取或自动初始化当月配额 */
    private TokenQuotaDO getOrCreateQuota(TokenQuotaMapper mapper, String tenantId, String month) {
        TokenQuotaDO quota = mapper.selectByTenantAndMonth(tenantId, month);
        if (quota != null) {
            return quota;
        }
        if (!properties.isAutoInit()) {
            return null;
        }
        // 自动初始化
        try {
            quota = new TokenQuotaDO();
            quota.setId(SnowflakeIdGenerator.nextIdStr());
            quota.setTenantId(tenantId);
            quota.setQuotaMonth(month);
            quota.setTotalQuota(properties.getDefaultMonthlyQuota());
            quota.setUsedTokens(0L);
            quota.setStatus("ACTIVE");
            quota.setResetAt(LocalDateTime.now());
            mapper.insert(quota);
            log.info("[TokenQuota] 自动初始化配额: tenant={} month={} quota={}",
                    tenantId, month, properties.getDefaultMonthlyQuota());
            return quota;
        } catch (Exception e) {
            // 并发场景可能已被其他线程创建，重新查询
            log.debug("[TokenQuota] 初始化配额时发生异常（可能并发）: {} err={}", tenantId, e.getMessage());
            return mapper.selectByTenantAndMonth(tenantId, month);
        }
    }

    /** TokenUsage → TokenUsageLogDO */
    private TokenUsageLogDO toLogDO(TokenUsage usage) {
        TokenUsageLogDO logDO = new TokenUsageLogDO();
        logDO.setId(SnowflakeIdGenerator.nextIdStr());
        logDO.setTenantId(usage.getTenantId());
        logDO.setTraceId(usage.getTraceId() == null ? "" : usage.getTraceId());
        logDO.setAgentType(usage.getAgentType());
        logDO.setProvider(usage.getProvider() == null ? "unknown" : usage.getProvider());
        logDO.setModel(usage.getModel());
        logDO.setBizRef(usage.getBizRef());
        logDO.setPromptTokens(usage.getPromptTokens());
        logDO.setCompletionTokens(usage.getCompletionTokens());
        logDO.setTotalTokens(usage.computeTotal());
        logDO.setCostMs(usage.getCostMs());
        logDO.setCallerId(usage.getCallerId());
        logDO.setCallerName(usage.getCallerName());
        return logDO;
    }

    /** 当前月份 YYYYMM */
    private String currentMonth() {
        return LocalDateTime.now().format(MONTH_FMT);
    }

    /** 默认概览（无配额记录时） */
    private QuotaSummary defaultSummary(String tenantId) {
        return QuotaSummary.of(tenantId, currentMonth(),
                properties.getDefaultMonthlyQuota(), 0L, "ACTIVE", null);
    }
}
