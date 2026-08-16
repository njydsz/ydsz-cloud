package com.njydsz.message.server.service.impl.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.config.RouteRuleUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgRouteRule;
import com.njydsz.message.infra.mapper.config.MsgRouteRuleMapper;
import com.njydsz.message.server.service.config.RouteRuleService;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

  /** 路由规则 Redis 缓存 TTL（L2） */
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  /** 路由规则本地缓存 TTL（L1，毫秒），远短于 Redis TTL 以平衡一致性与性能 */
  private static final long LOCAL_CACHE_TTL_MS = 30_000L;

  /** 本地缓存最大条目数（路由规则单一 key，设为 1） */
  private static final int LOCAL_CACHE_MAX_SIZE = 10;

  /** Caffeine 本地一级缓存（规则列表，key 为 ROUTE_RULE_CACHE_KEY） */
  private final Cache<String, List<MsgRouteRule>> localCache =
      Caffeine.newBuilder()
          .maximumSize(LOCAL_CACHE_MAX_SIZE)
          .expireAfterWrite(LOCAL_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
          .recordStats()
          .build();

  /** 路由规则 Mapper */
  private final MsgRouteRuleMapper msgRouteRuleMapper;

  /** SpEL 表达式解析器（条件求值） */
  private final ExpressionParser expressionParser;

  /** Redis 模板（路由规则缓存） */
  private final RedisStringOps redisStringOps;

  /**
   * {@inheritDoc}
   *
   * <p>执行 ruleCode 唯一性校验后插入，并清除路由规则缓存。
   *
   * @throws SysException 当 ruleCode 为空或已存在时抛出
   */
  @Override
  public MsgRouteRule create(RouteRuleUpsertDTO dto) {
    if (dto == null || !StringUtils.hasText(dto.getRuleCode())) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("规则编码不能为空")
          .build();
    }
    MsgRouteRule existing =
        msgRouteRuleMapper.selectOne(
            new LambdaQueryWrapper<MsgRouteRule>()
                .eq(MsgRouteRule::getRuleCode, dto.getRuleCode())
                .eq(MsgRouteRule::getTenantId, TenantContextHolder.getTenantId())
                .last("LIMIT 1"));
    if (existing != null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("规则编码已存在: " + dto.getRuleCode())
          .build();
    }
    MsgRouteRule entity = toEntity(dto);
    msgRouteRuleMapper.insert(entity);
    evictCache();
    log.info("[RouteRule] 创建规则: code={}", dto.getRuleCode());
    return entity;
  }

  /**
   * {@inheritDoc}
   *
   * <p>仅更新非 null 字段（动态更新），更新后清除路由规则缓存。
   *
   * @throws SysException 当 id 或 dto 为空时抛出
   */
  @Override
  public MsgRouteRule update(String id, RouteRuleUpsertDTO dto) {
    if (!StringUtils.hasText(id) || dto == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("规则 ID 与参数不能为空")
          .build();
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
   *
   * <p>删除后清除路由规则缓存。
   *
   * @throws SysException 当 id 为空时抛出
   */
  @Override
  public void delete(String id) {
    if (!StringUtils.hasText(id)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("规则 ID 不能为空")
          .build();
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
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("规则 ID 不能为空")
          .build();
    }
    MsgRouteRule entity = msgRouteRuleMapper.selectById(id);
    if (entity == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("路由规则不存在: " + id)
          .build();
    }
    return entity;
  }

  /**
   * 分页查询路由规则。
   *
   * <p>按 {@code sortOrder} 升序、{@code createdAt} 降序分页；页码/页大小缺失时取默认值， 页大小受 {@code PageConstants}
   * 上限保护，防止一次拉取过多。
   *
   * @param query 分页参数（可为 null，使用默认值）
   * @return 路由规则分页结果
   */
  @Override
  public Page<MsgRouteRule> page(PageQuery query) {
    Page<MsgRouteRule> page =
        new Page<>(
            query == null ? 1 : query.getPageNum(),
            Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
    return msgRouteRuleMapper.selectPage(
        page,
        new LambdaQueryWrapper<MsgRouteRule>()
            .orderByAsc(MsgRouteRule::getSortOrder)
            .orderByDesc(MsgRouteRule::getCreatedAt));
  }

  /**
   * 查询所有启用的路由规则（按 priority/sortOrder 缓存）。
   *
   * <p>直接读取内存缓存的已启用规则列表，供 {@link #match} 匹配使用，避免每次匹配都查库。
   *
   * @return 启用规则列表（按优先级升序）
   */
  @Override
  public List<MsgRouteRule> listEnabled() {
    return loadEnabledRulesFromCache();
  }

  /**
   * 按 priority 升序遍历启用规则，SpEL 求值首个命中即返回。
   *
   * <p>将 {@code MessageRequest} 注入 SpEL 上下文（变量名 {@code request}），
   * 条件表达式为空视为恒真命中；单条规则求值异常仅告警并跳过，不影响整体匹配。 全不匹配返回 null，由调用方决定兜底通道。
   *
   * @param request 消息请求（含 bizType/bizId/receiver/channel 等）
   * @return 命中的路由规则；未命中返回 null
   */
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
        log.warn(
            "[RouteRule] SpEL 求值失败,跳过规则: ruleId={} expr={} err={}",
            rule.getId(),
            rule.getConditionExpr(),
            e.getMessage());
      }
    }
    return null;
  }

  /**
   * 两级缓存加载启用规则列表。
   *
   * <p>查询顺序：L1 本地缓存（Caffeine）→ L2 Redis → DB，每一层回填上层。 L1 仅 30 秒 TTL，
   * 一致性由 CUD 操作主动失效 L1 + L2 保证。
   *
   * @return 启用规则列表（按优先级升序）
   */
  private List<MsgRouteRule> loadEnabledRulesFromCache() {
    String cacheKey = MessageConstants.ROUTE_RULE_CACHE_KEY;
    // L1: 本地缓存
    List<MsgRouteRule> l1Result = localCache.getIfPresent(cacheKey);
    if (l1Result != null) {
      return l1Result;
    }
    // L2: Redis
    try {
      String json = redisStringOps.get(cacheKey, String.class);
      if (StringUtils.hasText(json)) {
        List<MsgRouteRule> cached = YdszJson.parseArray(json, MsgRouteRule.class);
        if (cached != null) {
          localCache.put(cacheKey, cached);
          return cached;
        }
      }
    } catch (Exception e) {
      log.warn("[RouteRule] L2 缓存读取失败,回退 DB: {}", e.getMessage(), e);
    }
    // DB 回源
    List<MsgRouteRule> rules =
        msgRouteRuleMapper.selectList(
            new LambdaQueryWrapper<MsgRouteRule>()
                .eq(MsgRouteRule::getStatus, "ENABLED")
                .orderByAsc(MsgRouteRule::getPriority));
    List<MsgRouteRule> result = rules == null ? Collections.emptyList() : rules;
    // 回填 L2 + L1
    try {
      redisStringOps.set(cacheKey, YdszJson.toJson(result), CACHE_TTL);
    } catch (Exception e) {
      log.warn("[RouteRule] L2 缓存回填失败: {}", e.getMessage(), e);
    }
    localCache.put(cacheKey, result);
    return result;
  }

  /** 主动失效路由规则缓存（L1 + L2 双失效）。 */
  private void evictCache() {
    localCache.invalidateAll();
    try {
      redisStringOps.del(MessageConstants.ROUTE_RULE_CACHE_KEY);
    } catch (Exception e) {
      log.warn("[RouteRule] L2 缓存失效失败: {}", e.getMessage(), e);
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
    entity.setTenantId(TenantContextHolder.getTenantId());
    return entity;
  }
}
