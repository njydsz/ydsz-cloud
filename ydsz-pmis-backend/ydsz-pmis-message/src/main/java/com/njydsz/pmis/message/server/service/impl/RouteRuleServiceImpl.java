package com.njydsz.pmis.message.server.service.impl.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import com.njydsz.pmis.message.domain.dto.config.RouteRuleUpsertDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgRouteRuleDO;
import com.njydsz.pmis.message.infra.mapper.config.MsgRouteRuleMapper;
import com.njydsz.pmis.message.server.service.config.RouteRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 消息路由规则服务实现。
 *
 * <p>使用 SpEL 求值 {@code conditionExpr}，上下文变量 {@code #request} 绑定 {@link MessageRequest}。
 * match 按 priority 升序遍历 enabled 规则，命中即返回；SpEL 求值失败跳过该规则。
 *
 * @author ydsz-pmis-team
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
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public MsgRouteRuleDO create(RouteRuleUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRuleCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "规则编码不能为空");
        }
        MsgRouteRuleDO existing = msgRouteRuleMapper.selectOne(new LambdaQueryWrapper<MsgRouteRuleDO>()
                .eq(MsgRouteRuleDO::getRuleCode, dto.getRuleCode())
                .eq(MsgRouteRuleDO::getTenantId, TenantContext.getTenantId())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "规则编码已存在: " + dto.getRuleCode());
        }
        MsgRouteRuleDO entity = toEntity(dto);
        msgRouteRuleMapper.insert(entity);
        evictCache();
        log.info("[RouteRule] 创建规则: code={}", dto.getRuleCode());
        return entity;
    }

    @Override
    public MsgRouteRuleDO update(String id, RouteRuleUpsertDTO dto) {
        if (!StringUtils.hasText(id) || dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "规则 ID 与参数不能为空");
        }
        MsgRouteRuleDO entity = getById(id);
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

    @Override
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "规则 ID 不能为空");
        }
        msgRouteRuleMapper.deleteById(id);
        evictCache();
    }

    @Override
    public MsgRouteRuleDO getById(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "规则 ID 不能为空");
        }
        MsgRouteRuleDO entity = msgRouteRuleMapper.selectById(id);
        if (entity == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "路由规则不存在: " + id);
        }
        return entity;
    }

    @Override
    public Page<MsgRouteRuleDO> page(PageQuery query) {
        Page<MsgRouteRuleDO> page = new Page<>(
                query == null ? 1 : query.getPage(),
                Math.min(query == null ? 10 : query.getSize(), PageQuery.MAX_SIZE));
        return msgRouteRuleMapper.selectPage(page, new LambdaQueryWrapper<MsgRouteRuleDO>()
                .orderByAsc(MsgRouteRuleDO::getSortOrder)
                .orderByDesc(MsgRouteRuleDO::getCreatedAt));
    }

    @Override
    public List<MsgRouteRuleDO> listEnabled() {
        return loadEnabledRulesFromCache();
    }

    @Override
    public MsgRouteRuleDO match(MessageRequest request) {
        if (request == null) {
            return null;
        }
        List<MsgRouteRuleDO> rules = loadEnabledRulesFromCache();
        EvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("request", request);
        for (MsgRouteRuleDO rule : rules) {
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
    private List<MsgRouteRuleDO> loadEnabledRulesFromCache() {
        try {
            String json = stringRedisTemplate.opsForValue().get(MessageConstants.ROUTE_RULE_CACHE_KEY);
            if (StringUtils.hasText(json)) {
                List<MsgRouteRuleDO> cached = com.alibaba.fastjson2.JSON.parseArray(json, MsgRouteRuleDO.class);
                if (cached != null) {
                    return cached;
                }
            }
        } catch (Exception e) {
            log.warn("[RouteRule] 缓存读取失败,回退 DB: {}", e.getMessage());
        }
        List<MsgRouteRuleDO> rules = msgRouteRuleMapper.selectList(new LambdaQueryWrapper<MsgRouteRuleDO>()
                .eq(MsgRouteRuleDO::getStatus, "ENABLED")
                .orderByAsc(MsgRouteRuleDO::getPriority));
        try {
            stringRedisTemplate.opsForValue().set(
                    MessageConstants.ROUTE_RULE_CACHE_KEY,
                    JsonUtils.toJson(rules),
                    CACHE_TTL);
        } catch (Exception e) {
            log.warn("[RouteRule] 缓存回填失败: {}", e.getMessage());
        }
        return rules == null ? Collections.emptyList() : rules;
    }

    /**
     * 主动失效路由规则缓存。
     */
    private void evictCache() {
        try {
            stringRedisTemplate.delete(MessageConstants.ROUTE_RULE_CACHE_KEY);
        } catch (Exception e) {
            log.warn("[RouteRule] 缓存失效失败: {}", e.getMessage());
        }
    }

    private MsgRouteRuleDO toEntity(RouteRuleUpsertDTO dto) {
        MsgRouteRuleDO entity = new MsgRouteRuleDO();
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
