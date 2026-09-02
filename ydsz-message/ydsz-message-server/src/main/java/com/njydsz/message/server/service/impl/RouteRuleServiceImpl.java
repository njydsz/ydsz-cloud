package com.njydsz.message.server.service.impl.config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.RouteRuleUpsertDTO;
import com.njydsz.message.domain.query.MsgRouteRuleQuery;
import com.njydsz.message.domain.repository.MsgRouteRuleRepository;
import com.njydsz.message.domain.vo.MsgRouteRuleVO;
import com.njydsz.message.server.service.config.RouteRuleService;

/**
 * 消息路由规则服务实现。
 *
 * <p>维护消息路由规则 ({@code ydsz_msg_route_rule})：根据租户/业务/优先级/模板类型选择渠道、
 *
 * <p>降级链、回执回调。规则支持 Groovy/Aviator 表达式动态求值。
 *
 * @author ydsz-team
 * @since 26.09.01
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

  /** 缓存名称（用于健康检查和监控） */
  private static final String CACHE_NAME = "message:route-rule";

  /** YdszCache 本地一级缓存（规则列表，key 为 ROUTE_RULE_CACHE_KEY） */
  private final Cache<String, List<MsgRouteRuleVO>> localCache =
      YdszCache.<String, List<MsgRouteRuleVO>>newBuilder()
          .name(CACHE_NAME)
          .maximumSize(LOCAL_CACHE_MAX_SIZE)
          .expireAfterWrite(LOCAL_CACHE_TTL_MS, TimeUnit.MILLISECONDS)
          .recordStats()
          .build();

  /** 路由规则 Repository */
  private final MsgRouteRuleRepository msgRouteRuleRepository;

  /** SpEL 表达式解析器（条件求值） */
  private final ExpressionParser expressionParser;

  /** Redis 模板（路由规则缓存） */
  private final RedisStringOps redisStringOps;

  /**
   * {@inheritDoc}
   *
   * <p>执行 ruleCode 唯一性校验后插入，并清除路由规则缓存。
   */
  @Override
  public MsgRouteRuleVO create(RouteRuleUpsertDTO dto) {
    if (dto == null || !StringUtils.hasText(dto.getRuleCode())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("规则编码不能为空")
          .build();
    }
    MsgRouteRuleQuery query = new MsgRouteRuleQuery();
    query.setRuleCode(dto.getRuleCode());
    Optional<MsgRouteRuleVO> existing = msgRouteRuleRepository.findOne(query);
    if (existing.isPresent()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("规则编码已存在: " + dto.getRuleCode())
          .build();
    }
    MsgRouteRuleVO vo = toVO(dto);
    msgRouteRuleRepository.save(vo);
    evictCache();
    log.info("[RouteRule] 创建规则: code={}", dto.getRuleCode());
    return vo;
  }

  /**
   * {@inheritDoc}
   *
   * <p>仅更新非 null 字段（动态更新），更新后清除路由规则缓存。
   */
  @Override
  public MsgRouteRuleVO update(String id, RouteRuleUpsertDTO dto) {
    if (!StringUtils.hasText(id) || dto == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("规则 ID 与参数不能为空")
          .build();
    }
    MsgRouteRuleVO vo = getById(id);
    if (StringUtils.hasText(dto.getRuleName())) {
      vo.setRuleName(dto.getRuleName());
    }
    if (dto.getBizType() != null) {
      vo.setBizType(dto.getBizType());
    }
    if (dto.getChannel() != null) {
      vo.setChannel(dto.getChannel());
    }
    if (dto.getPriority() != null) {
      vo.setPriority(dto.getPriority());
    }
    if (dto.getConditionExpr() != null) {
      vo.setConditionExpr(dto.getConditionExpr());
    }
    if (dto.getTargetChannel() != null) {
      vo.setTargetChannel(dto.getTargetChannel());
    }
    if (dto.getFallbackChannel() != null) {
      vo.setFallbackChannel(dto.getFallbackChannel());
    }
    if (StringUtils.hasText(dto.getStatus())) {
      vo.setStatus(dto.getStatus());
    }
    if (dto.getDescription() != null) {
      vo.setDescription(dto.getDescription());
    }
    if (dto.getSortOrder() != null) {
      vo.setSortOrder(dto.getSortOrder());
    }
    msgRouteRuleRepository.update(vo);
    evictCache();
    return vo;
  }

  /**
   * {@inheritDoc}
   *
   * <p>删除后清除路由规则缓存。
   */
  @Override
  public void delete(String id) {
    if (!StringUtils.hasText(id)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("规则 ID 不能为空")
          .build();
    }
    msgRouteRuleRepository.deleteById(id);
    evictCache();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MsgRouteRuleVO getById(String id) {
    if (!StringUtils.hasText(id)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("规则 ID 不能为空")
          .build();
    }
    return msgRouteRuleRepository
        .findById(id)
        .orElseThrow(
            () ->
                SysException.builder()
                    .resultCode(YdszResultCode.NOT_FOUND)
                    .message("路由规则不存在: " + id)
                    .build());
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
  public PageResponse<List<MsgRouteRuleVO>> page(PageQuery query) {
    MsgRouteRuleQuery routeQuery = new MsgRouteRuleQuery();
    routeQuery.setPageNum(query == null ? 1 : query.getPageNum());
    routeQuery.setPageSize(
        Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
    return msgRouteRuleRepository.findPage(routeQuery);
  }

  /**
   * 查询所有启用的路由规则（按 priority/sortOrder 缓存）。
   *
   * <p>直接读取内存缓存的已启用规则列表，供 {@link #match} 匹配使用，避免每次匹配都查库。
   *
   * @return 启用规则列表（按优先级升序）
   */
  @Override
  public List<MsgRouteRuleVO> listEnabled() {
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
  public MsgRouteRuleVO match(MessageRequest request) {
    if (request == null) {
      return null;
    }
    List<MsgRouteRuleVO> rules = loadEnabledRulesFromCache();
    EvaluationContext ctx = new StandardEvaluationContext();
    ctx.setVariable("request", request);
    for (MsgRouteRuleVO rule : rules) {
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
   * <p>查询顺序：L1 本地缓存（YdszCache）→ L2 Redis → DB，每一层回填上层。 L1 仅 30 秒 TTL， 一致性由 CUD 操作主动失效 L1 + L2 保证。
   *
   * @return 启用规则列表（按优先级升序）
   */
  private List<MsgRouteRuleVO> loadEnabledRulesFromCache() {
    String cacheKey = MessageConstants.ROUTE_RULE_CACHE_KEY;
    // L1: 本地缓存
    List<MsgRouteRuleVO> l1Result = localCache.getIfPresent(cacheKey);
    if (l1Result != null) {
      return l1Result;
    }
    // L2: Redis
    try {
      String json = redisStringOps.get(cacheKey, String.class);
      if (StringUtils.hasText(json)) {
        List<MsgRouteRuleVO> cached = YdszJson.parseArray(json, MsgRouteRuleVO.class);
        if (cached != null) {
          localCache.put(cacheKey, cached);
          return cached;
        }
      }
    } catch (Exception e) {
      log.warn("[RouteRule] L2 缓存读取失败,回退 DB: {}", e.getMessage(), e);
    }
    // DB 回源
    MsgRouteRuleQuery query = new MsgRouteRuleQuery();
    query.setStatus("ENABLED");
    List<MsgRouteRuleVO> rules = msgRouteRuleRepository.findList(query);
    List<MsgRouteRuleVO> result = rules == null ? Collections.emptyList() : rules;
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

  private MsgRouteRuleVO toVO(RouteRuleUpsertDTO dto) {
    MsgRouteRuleVO vo = new MsgRouteRuleVO();
    vo.setRuleCode(dto.getRuleCode());
    vo.setRuleName(dto.getRuleName());
    vo.setBizType(dto.getBizType());
    vo.setChannel(dto.getChannel());
    vo.setPriority(dto.getPriority() == null ? 100 : dto.getPriority());
    vo.setConditionExpr(dto.getConditionExpr());
    vo.setTargetChannel(dto.getTargetChannel());
    vo.setFallbackChannel(dto.getFallbackChannel());
    vo.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ENABLED");
    vo.setDescription(dto.getDescription());
    vo.setSortOrder(dto.getSortOrder() == null ? 100 : dto.getSortOrder());
    return vo;
  }
}
