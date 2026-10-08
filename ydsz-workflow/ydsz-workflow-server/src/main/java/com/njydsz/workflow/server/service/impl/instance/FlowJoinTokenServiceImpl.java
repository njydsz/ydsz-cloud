package com.njydsz.workflow.server.service.impl.instance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.workflow.domain.repository.FlowJoinTokenRepository;
import com.njydsz.workflow.server.cache.CacheKeyBuilder;
import com.njydsz.workflow.server.service.FlowJoinTokenService;

/**
 * 流程加签 Token 服务实现。
 *
 * <p>管理并行网关 join 的到达计数与分支总数：分支到达时原子计数 → 达到阈值时触发 join 聚合 → 全部完成后清除。
 *
 * <p><b>持久化策略（V26.10.08 新增）：</b>
 *
 * <ul>
 *   <li>DB（{@code ydsz_flow_join_token}）为主存储，确保服务重启后 join 状态不丢失</li>
 *   <li>Redis 为缓存层，加速读操作；DB 写入成功后再更新 Redis</li>
 *   <li>读操作优先 Redis，未命中或异常时回退到 DB</li>
 * </ul>
 *
 * <p>支持全部分支到达和 N/M 到达两种模式，DB 层 SQL 原子递增保证并发安全。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowJoinTokenServiceImpl implements FlowJoinTokenService {

  /** 默认 TTL：7 天 */
  private static final long TTL_SECONDS = 7 * 24 * 60 * 60;

  /** P2-2 整改：缓存键构造器（集中管理 flow:join:* key 前缀） */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** Redis String 操作组件（SET + EXPIRE、INCR、GET 等高级 API，支持租户前缀） */
  private final RedisStringOps redisStringOps;

  /** DB 持久化层（主存储，domain 接口，infra 实现由 Spring 注入） */
  private final FlowJoinTokenRepository joinTokenRepository;

  // ============================== 接口实现 ==============================

  /**
   * 初始化 join 令牌：写入分支总数并重置到达计数。
   *
   * <p>DB 优先 → 成功后更新 Redis 缓存。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @param branchCount 并行分支数（&lt;=0 时按 1 处理）
   */
  @Override
  public void initTokens(String instanceId, String joinNodeCode, int branchCount) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return;
    }
    int total = Math.max(1, branchCount);
    // DB 优先（主存储）
    String tokenId = joinTokenRepository.initTokens(instanceId, joinNodeCode, total, 0);
    if (tokenId == null) {
      log.warn(
          "[FlowJoinToken] DB 初始化令牌失败，回退到 Redis instanceId={} node={}",
          instanceId,
          joinNodeCode);
      // DB 失败时回退到 Redis-only
      initTokensInRedis(instanceId, joinNodeCode, total, null);
      return;
    }
    // DB 成功后更新 Redis 缓存
    initTokensInRedis(instanceId, joinNodeCode, total, null);
    log.info(
        "[FlowJoinToken] 初始化 join 令牌 instanceId={} node={} branchCount={}",
        instanceId,
        joinNodeCode,
        total);
  }

  /**
   * P0-3: 初始化 N/M join 令牌。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @param branchCount 总分支数
   * @param requiredCount 需到达的分支数
   */
  @Override
  public void initTokensWithRequired(
      String instanceId, String joinNodeCode, int branchCount, int requiredCount) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return;
    }
    int total = Math.max(1, branchCount);
    int required = Math.min(Math.max(1, requiredCount), total);
    // DB 优先（主存储）
    String tokenId = joinTokenRepository.initTokens(instanceId, joinNodeCode, total, required);
    if (tokenId == null) {
      log.warn(
          "[FlowJoinToken] DB 初始化 N/M 令牌失败，回退到 Redis instanceId={} node={}",
          instanceId,
          joinNodeCode);
      initTokensInRedis(instanceId, joinNodeCode, total, required);
      return;
    }
    initTokensInRedis(instanceId, joinNodeCode, total, required);
    log.info(
        "[FlowJoinToken] P0-3 初始化 N/M join 令牌 instanceId={} node={} total={} required={}",
        instanceId,
        joinNodeCode,
        total,
        required);
  }

  /**
   * 标记一个分支已到达 join 节点。
   *
   * <p>DB 原子递增为主 → 成功后同步 Redis 缓存。以 DB 判断聚合是否完成。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=本次到达后全部分支已到达（可聚合）；false=仍有分支未到达或异常
   */
  @Override
  public boolean arriveToken(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    // DB 原子递增（主存储）
    int dbArrived = joinTokenRepository.arriveToken(instanceId, joinNodeCode);
    if (dbArrived < 0) {
      // DB 异常或 token 不存在：回退到 Redis
      log.warn(
          "[FlowJoinToken] DB arrive 失败，回退到 Redis instanceId={} node={}",
          instanceId,
          joinNodeCode);
      return arriveTokenInRedis(instanceId, joinNodeCode, false);
    }
    // DB 判断聚合是否完成
    boolean complete = joinTokenRepository.isComplete(instanceId, joinNodeCode);
    // 异步更新 Redis 缓存（不影响主流程）
    syncRedisArrive(instanceId, joinNodeCode);
    log.debug(
        "[FlowJoinToken] 分支到达 instanceId={} node={} arrived={} allArrived={}",
        instanceId,
        joinNodeCode,
        dbArrived,
        complete);
    return complete;
  }

  /**
   * P0-3: 标记分支到达并检查 N/M 聚合条件。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=N/M 聚合条件满足
   */
  @Override
  public boolean arriveTokenWithRequired(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    // DB 原子递增（主存储）
    int dbArrived = joinTokenRepository.arriveToken(instanceId, joinNodeCode);
    if (dbArrived < 0) {
      log.warn(
          "[FlowJoinToken] DB N/M arrive 失败，回退到 Redis instanceId={} node={}",
          instanceId,
          joinNodeCode);
      return arriveTokenInRedis(instanceId, joinNodeCode, true);
    }
    // DB 判断聚合是否完成
    boolean complete = joinTokenRepository.isComplete(instanceId, joinNodeCode);
    // 异步更新 Redis 缓存
    syncRedisArrive(instanceId, joinNodeCode);
    if (complete) {
      log.debug(
          "[FlowJoinToken] P0-3 N/M 聚合条件满足 instanceId={} node={} arrived={}",
          instanceId,
          joinNodeCode,
          dbArrived);
    }
    return complete;
  }

  /**
   * 检查是否所有分支都已到达（可以聚合通过）。
   *
   * <p>优先查 Redis，未命中时回退到 DB。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=全部到达可聚合；false=未全部到达 / 未初始化 / 异常
   */
  @Override
  public boolean allArrived(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    // 优先查 DB（DB 为主存储）
    return joinTokenRepository.isComplete(instanceId, joinNodeCode);
  }

  /**
   * P0-3: 检查是否满足 N/M 聚合条件。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=已达到 N/M 聚合条件
   */
  @Override
  public boolean requirementMet(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    return joinTokenRepository.isComplete(instanceId, joinNodeCode);
  }

  /**
   * 清除 join 令牌：标记 DB 状态为 COMPLETED 并删除 Redis key。
   *
   * <p>join 聚合通过后或流程终止时调用，释放计数资源。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   */
  @Override
  public void clearTokens(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return;
    }
    try {
      // 标记 DB 状态
      joinTokenRepository.markCompleted(instanceId, joinNodeCode);
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] DB 标记完成失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
    }
    try {
      // 删除 Redis 缓存
      redisStringOps.del(buildArrivedKey(instanceId, joinNodeCode));
      redisStringOps.del(buildTotalKey(instanceId, joinNodeCode));
      redisStringOps.del(buildRequiredKey(instanceId, joinNodeCode));
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] Redis 清除失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
    }
    log.info("[FlowJoinToken] 清除 join 令牌 instanceId={} node={}", instanceId, joinNodeCode);
  }

  /**
   * 检查 join 令牌是否已初始化。
   *
   * <p>同时检查 DB 和 Redis：任一来源报告已初始化即视为已初始化。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=已初始化
   */
  @Override
  public boolean isInitialized(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    // 优先查 DB
    return joinTokenRepository.isInitialized(instanceId, joinNodeCode);
  }

  // ============================== Redis 私有辅助 ==============================

  /**
   * 在 Redis 中初始化 token（缓存层）。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @param total 总分支数
   * @param required 所需到达数（null 表示全部分支）
   */
  private void initTokensInRedis(
      String instanceId, String joinNodeCode, int total, Integer required) {
    String totalKey = buildTotalKey(instanceId, joinNodeCode);
    String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
    String requiredKey = buildRequiredKey(instanceId, joinNodeCode);
    try {
      redisStringOps.set(arrivedKey, "0", TTL_SECONDS);
      redisStringOps.set(totalKey, String.valueOf(total), TTL_SECONDS);
      if (required != null) {
        redisStringOps.set(requiredKey, String.valueOf(required), TTL_SECONDS);
      }
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] Redis 初始化令牌缓存失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
    }
  }

  /**
   * 在 Redis 中执行 arriveToken 操作（降级路径）。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @param withRequired 是否使用 N/M 语义
   * @return true=聚合完成
   */
  private boolean arriveTokenInRedis(
      String instanceId, String joinNodeCode, boolean withRequired) {
    String totalKey = buildTotalKey(instanceId, joinNodeCode);
    String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
    try {
      Long arrived = redisStringOps.incr(arrivedKey, 1L);
      redisStringOps.expire(arrivedKey, TTL_SECONDS);
      if (arrived == null) {
        return false;
      }
      if (withRequired) {
        String requiredStr = redisStringOps.get(buildRequiredKey(instanceId, joinNodeCode), String.class);
        if (requiredStr != null) {
          return arrived >= Integer.parseInt(requiredStr);
        }
      }
      String totalStr = redisStringOps.get(totalKey, String.class);
      if (totalStr == null) {
        return false;
      }
      return arrived >= Integer.parseInt(totalStr);
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] Redis arrive 降级操作失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
      return false;
    }
  }

  /**
   * 同步 Redis 到达计数（异步更新缓存）。
   *
   * <p>以 DB 当前计数设置 Redis，不依赖 INCR（避免缓存层与 DB 产生计数偏差）。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   */
  private void syncRedisArrive(String instanceId, String joinNodeCode) {
    try {
      int dbCount = joinTokenRepository.getArrivedCount(instanceId, joinNodeCode);
      if (dbCount >= 0) {
        String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
        redisStringOps.set(arrivedKey, String.valueOf(dbCount), TTL_SECONDS);
      }
    } catch (Exception e) {
      // Redis 同步失败不影响主流程
      log.debug(
          "[FlowJoinToken] Redis 同步计数失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
    }
  }

  // ============================== 私有辅助 ==============================

  /**
   * 参数合法性校验。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=参数合法
   */
  private boolean isValidParam(String instanceId, String joinNodeCode) {
    if (instanceId == null) {
      log.warn("[FlowJoinToken] instanceId 为空，跳过");
      return false;
    }
    if (joinNodeCode == null || joinNodeCode.isBlank()) {
      log.warn("[FlowJoinToken] joinNodeCode 为空，跳过 instanceId={}", instanceId);
      return false;
    }
    return true;
  }

  /**
   * 构建到达计数 key：flow:join:{instanceId}:{joinNodeCode}
   */
  private String buildArrivedKey(String instanceId, String joinNodeCode) {
    return cacheKeyBuilder.joinToken(instanceId, joinNodeCode);
  }

  /**
   * 构建分支总数 key：flow:join:{instanceId}:{joinNodeCode}:total
   */
  private String buildTotalKey(String instanceId, String joinNodeCode) {
    return cacheKeyBuilder.joinTokenTotal(instanceId + ":" + joinNodeCode);
  }

  /**
   * 构建 N/M join required key：flow:join:{instanceId}:{joinNodeCode}:required
   */
  private String buildRequiredKey(String instanceId, String joinNodeCode) {
    return cacheKeyBuilder.joinTokenRequired(instanceId + ":" + joinNodeCode);
  }
}
