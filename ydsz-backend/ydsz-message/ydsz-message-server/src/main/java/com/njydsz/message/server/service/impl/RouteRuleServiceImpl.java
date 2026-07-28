package com.njydsz.message.server.service.impl.config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import com.njydsz.common.redis.service.RedisService;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.security.TenantContext;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.config.RouteRuleUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgRouteRule;
import com.njydsz.message.infra.mapper.config.MsgRouteRuleMapper;
import com.njydsz.message.server.service.config.RouteRuleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息路由规则服务实现。
 *
 * <p>维护消息路由规则 ({@code ydsz_msg_route_rule})：根据租户/业务/优先级/模板类型选择渠道、
 *
 * <p>降级链、回执回调。规则支持 Groovy/Aviator 表达式动态求值。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteRuleServiceImpl implements RouteRuleService {

    /** 路由规则缓存 TTL */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** 路由规则 Mapper */
    private final MsgRouteRuleMapper msgRouteRuleMapper;
    /** SpEL 表达式解析器（条件求值） */
    private final ExpressionParser expressionParser;
    /** Redis 模板（路由规则缓存） */
    private final RedisService redisService;

    /**
     * {@inheritDoc}
     * <p>执行 ruleCode 唯一性校验后插入，并清除路由规则缓存。
     *
     * @throws SysException 当 ruleCode 为空或已存在时抛出
     */
    @Override
    public MsgRouteRule create(RouteRuleUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRuleCode())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "规则编码不能为空");
        }
        MsgRouteRule existing = msgRouteRuleMapper.selectOne(new LambdaQueryWrapper<MsgRouteRule>()
                .eq(MsgRouteRule::getRuleCode, dto.getRuleCode())
                .eq(MsgRouteRule::getTenantId, TenantContext.getTenantId())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new SysException(BaseResultCode.DUPLICATE_KEY, "规则编码已存在: " + dto.getRuleCode());
        }
        MsgRouteRule entity = toEntity(dto);
        msgRouteRuleMapper.insert(entity);
        evictCache();
        log.info("[RouteRule] 创建规则: code={}", dto.getRuleCode());
        return entity;
    }

    /**
     * {@inheritDoc}
     * <p>仅更新非 null 字段（动态更新），更新后清除路由规则缓存。
     *
     * @throws SysException 当 id 或 dto 为空时抛出
     */
    @Override
    public MsgRouteRule update(String id, RouteRuleUpsertDTO dto) {
        if (!StringUtils.hasText(id) || dto == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "规则 ID 与参数不能为空");
        }
        MsgRouteRule entity = getById(id);
        if (StringUtils.hasText(dto.getRuleName())) {
            entity.setRuleName(dto.getRuleName());
        }
        if (dto.getBizType() != null) {
            entity.setBizType(dto.getBizType());
        }
        if (dto.getChannel() != null) {
            entity.setChannel(dto.getChannel());
        }
        if (dto.getPriority() != null) {
            entity.setPriority(dto.getPriority());
        }
        if (dto.getConditionExpr() != null) {
            entity.setConditionExpr(dto.getConditionExpr());
        }
        if (dto.getTargetChannel() != null) {
            entity.setTargetChannel(dto.getTargetChannel());
        }
        if (dto.getFallbackChannel() != null) {
            entity.setFallbackChannel(dto.getFallbackChannel());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getSortOrder() != null) {
            entity.setSortOrder(dto.getSortOrder());
        }
        msgRouteRuleMapper.updateById(entity);
        evictCache();
        return entity;
    }

    /**
     * {@inheritDoc}
     * <p>删除后清除路由规则缓存。
     *
     * @throws SysException 当 id 为空时抛出
     */
    @Override
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "规则 ID 不能为空");
        }
        msgRouteRuleMapper.deleteById(id);
        evictCache();
    }

    /**
     * {@inheritDoc}
     *
     * @throws SysException 当 id 为空时抛出
     */
    @Override
    public MsgRouteRule getById(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "规则 ID 不能为空");
        }
        MsgRouteRule entity = msgRouteRuleMapper.selectById(id);
        if (entity == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "路由规则不存在: " + id);
        }
        return entity;
    }

    @Override
    public Page<MsgRouteRule> page(PageQuery query) {
        Page<MsgRouteRule> page = new Page<>(
                query == null ? 1 : query.getPageNum(),
                Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
        return msgRouteRuleMapper.selectPage(page, new LambdaQueryWrapper<MsgRouteRule>()
                .orderByAsc(MsgRouteRule::getSortOrder)
                .orderByDesc(MsgRouteRule::getCreatedAt));
    }

    @Override
    public List<MsgRouteRule> listEnabled() {
        return loadEnabledRulesFromCache();
    }

    @Override
    public MsgRouteRule match(MessageRequest request) {
        if (request == null) {
            return null;
        }
        List<MsgRouteRule> rules = loadEnabledRulesFromCache();
        EvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("request", request);
        for (MsgRouteRule rule : rules) {
            if (!StringUtils.hasText(rule.getConditionExpr())) {
                // 无条件表达式视为恒真命中
                return rule;
            }
            try {
                Expression exp = expressionParser.parseExpression(rule.getConditionExpr());
                Boolean matched = exp.getValue(ctx, Boolean.class);
                if (Boolean.TRUE.equals(matched)) {
                    return rule;
                }
            } catch (Exception e) {
                // SpEL 求值失败跳过该规则
                log.warn("[RouteRule] SpEL 求值失败,跳过规则: ruleId={} expr={} err={}",
                        rule.getId(), rule.getConditionExpr(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * 从 Redis 加载启用规则列表(未命中则查 DB 并回填)。
     */
    private List<MsgRouteRule> loadEnabledRulesFromCache() {
        try {
            String json = redisService.get(MessageConstants.ROUTE_RULE_CACHE_KEY, String.class);
            if (StringUtils.hasText(json)) {
                List<MsgRouteRule> cached = YdszJson.parseArray(json, MsgRouteRule.class);
                if (cached != null) {
                    return cached;
                }
            }
        } catch (Exception e) {
            log.warn("[RouteRule] 缓存读取失败,回退 DB: {}", e.getMessage(), e);
        }
        List<MsgRouteRule> rules = msgRouteRuleMapper.selectList(new LambdaQueryWrapper<MsgRouteRule>()
                .eq(MsgRouteRule::getStatus, "ENABLED")
                .orderByAsc(MsgRouteRule::getPriority));
        try {
            redisService.set(
                    MessageConstants.ROUTE_RULE_CACHE_KEY, YdszJson.toJson(rules),
                    CACHE_TTL);
        } catch (Exception e) {
            log.warn("[RouteRule] 缓存回填失败: {}", e.getMessage(), e);
        }
        return rules == null ? Collections.emptyList() : rules;
    }

    /**
     * 主动失效路由规则缓存。
     */
    private void evictCache() {
        try {
            redisService.delete(MessageConstants.ROUTE_RULE_CACHE_KEY);
        } catch (Exception e) {
            log.warn("[RouteRule] 缓存失效失败: {}", e.getMessage(), e);
        }
    }

    private MsgRouteRule toEntity(RouteRuleUpsertDTO dto) {
        MsgRouteRule entity = new MsgRouteRule();
        entity.setRuleCode(dto.getRuleCode());
        entity.setRuleName(dto.getRuleName());
        entity.setBizType(dto.getBizType());
        entity.setChannel(dto.getChannel());
        entity.setPriority(dto.getPriority() == null ? 100 : dto.getPriority());
        entity.setConditionExpr(dto.getConditionExpr());
        entity.setTargetChannel(dto.getTargetChannel());
        entity.setFallbackChannel(dto.getFallbackChannel());
        entity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ENABLED");
        entity.setDescription(dto.getDescription());
        entity.setSortOrder(dto.getSortOrder() == null ? 100 : dto.getSortOrder());
        entity.setTenantId(TenantContext.getTenantId());
        return entity;
    }
}
