package com.njydsz.workflow.server.service.impl.instance;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.workflow.server.service.FlowJoinTokenService;

/**
 * 流程加签 Token 服务实现。
 *
 * <p>管理并行网关 join 的到达计数与分支总数 ({@code flow:join:*})：
 *
 * <p>分支到达时原子计数 → 达到阈值时触发 join 聚合 → 全部完成后清除。
 *
 * <p>支持全部分支到达和 N/M 到达两种模式， TTL 兜底防止数据永久残留。
 *
 * <p>Redis 操作使用 {@link RedisStringOps} 高级 API（INCR + EXPIRE、SET + EXPIRE），
 * 替代手写 Lua 脚本，保证原子性同时提升可维护性。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowJoinTokenServiceImpl implements FlowJoinTokenService {

  /** 到达计数 key 前缀：flow:join:{instanceId}:{joinNodeCode} */
  private static final String KEY_PREFIX = "flow:join:";

  /** 分支总数 key 后缀 */
  private static final String TOTAL_SUFFIX = ":total";

  /** P0-3: N/M join 所需到达数 key 后缀 */
  private static final String REQUIRED_SUFFIX = ":required";

  /** 默认 TTL：7 天 */
  private static final long TTL_SECONDS = 7 * 24 * 60 * 60;

  /** Redis String 操作组件（SET + EXPIRE、INCR、GET 等高级 API，支持租户前缀） */
  private final RedisStringOps redisStringOps;

  // ============================== 接口实现 ==============================

  /**
   * 初始化 join 令牌：写入分支总数并重置到达计数。
   *
   * <p>使用 SET + EX 原子写入替代 Lua 脚本（{@code SET key value EX ttl}），
   * Spring Data Redis 在单次请求中完成 SET + EXPIRE，无并发竞态。
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
    String totalKey = buildTotalKey(instanceId, joinNodeCode);
    String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
    try {
      // SET arrived 0 EX ttl + SET total N EX ttl（两条原子 SET 命令，Spring Data Redis SET 带 Duration）
      redisStringOps.set(arrivedKey, "0", TTL_SECONDS);
      redisStringOps.set(totalKey, String.valueOf(total), TTL_SECONDS);
      log.info(
          "[FlowJoinToken] 初始化 join 令牌 instanceId={} node={} branchCount={}",
          instanceId,
          joinNodeCode,
          total);
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] 初始化令牌失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
    }
  }

  /**
   * 标记一个分支已到达：INCR 到达计数并判断是否全部到达。
   *
   * <p>使用 INCR + EXPIRE + GET 组合替代 Lua 脚本。INCR 保证原子计数，
   * EXPIRE 刷新 TTL，GET total 仅读取不变值（initTokens 后不再修改），无竞态。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=本次到达后全部分支已到达（可聚合）；false=仍有分支未到达或 Redis 异常
   */
  @Override
  public boolean arriveToken(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    String totalKey = buildTotalKey(instanceId, joinNodeCode);
    String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
    try {
      // INCR arrived（原子） + EXPIRE 刷新 TTL
      Long arrived = redisStringOps.incr(arrivedKey, 1L);
      redisStringOps.expire(arrivedKey, TTL_SECONDS);
      // GET total（total 在 initTokens 后不再修改，无需原子组合）
      String totalStr = redisStringOps.get(totalKey, String.class);
      if (totalStr == null || arrived == null) {
        return false;
      }
      int total = Integer.parseInt(totalStr);
      boolean allArrived = arrived >= total;
      log.debug(
          "[FlowJoinToken] 分支到达 instanceId={} node={} arrived={} total={} allArrived={}",
          instanceId,
          joinNodeCode,
          arrived,
          total,
          allArrived);
      return allArrived;
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] 标记到达失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
      return false;
    }
  }

  /**
   * 检查是否所有分支都已到达。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=全部到达可聚合；false=未全部到达 / 未初始化 / Redis 异常
   */
  @Override
  public boolean allArrived(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    try {
      int total = readTotal(instanceId, joinNodeCode);
      String arrivedStr =
          redisStringOps.get(buildArrivedKey(instanceId, joinNodeCode), String.class);
      if (arrivedStr == null) {
        return false;
      }
      long arrived;
      try {
        arrived = Long.parseLong(arrivedStr);
      } catch (NumberFormatException e) {
        log.warn(
            "[FlowJoinToken] 到达计数非数字 instanceId={} node={} raw={}",
            instanceId,
            joinNodeCode,
            arrivedStr);
        return false;
      }
      return arrived >= total;
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] 检查全部到达失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
      return false;
    }
  }

  /**
   * P0-3: 初始化 N/M join 令牌。
   *
   * <p>使用三条 SET + EX 原子写入，替代 Lua 脚本。
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
    String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
    String totalKey = buildTotalKey(instanceId, joinNodeCode);
    String requiredKey = buildRequiredKey(instanceId, joinNodeCode);
    try {
      // 三条 SET + EX，每条独立原子
      redisStringOps.set(arrivedKey, "0", TTL_SECONDS);
      redisStringOps.set(totalKey, String.valueOf(total), TTL_SECONDS);
      redisStringOps.set(requiredKey, String.valueOf(required), TTL_SECONDS);
      log.info(
          "[FlowJoinToken] P0-3 初始化 N/M join 令牌 instanceId={} node={} total={} required={}",
          instanceId,
          joinNodeCode,
          total,
          required);
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] P0-3 初始化 N/M 令牌失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
    }
  }

  /**
   * P0-3: 标记分支到达并检查 N/M 聚合条件。
   *
   * <p>使用 INCR + EXPIRE + GET 组合替代 Lua 脚本，获取 required 值而非 total。
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
    String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
    String requiredKey = buildRequiredKey(instanceId, joinNodeCode);
    try {
      // INCR arrived（原子） + EXPIRE 刷新 TTL
      Long arrived = redisStringOps.incr(arrivedKey, 1L);
      redisStringOps.expire(arrivedKey, TTL_SECONDS);
      // 先尝试 N/M 评估
      String requiredStr = redisStringOps.get(requiredKey, String.class);
      if (requiredStr != null && arrived != null) {
        int required = Integer.parseInt(requiredStr);
        if (arrived >= required) {
          log.debug(
              "[FlowJoinToken] P0-3 N/M 聚合条件满足 instanceId={} node={} arrived={} required={}",
              instanceId,
              joinNodeCode,
              arrived,
              required);
          return true;
        }
      }
      // required key 不存在时回退到全部分支语义
      Boolean hasRequired = redisStringOps.hasKey(requiredKey);
      if (Boolean.FALSE.equals(hasRequired)) {
        return arriveToken(instanceId, joinNodeCode);
      }
      return false;
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] P0-3 N/M 到达标记失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
      return arriveToken(instanceId, joinNodeCode);
    }
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
    try {
      String requiredStr =
          redisStringOps.get(buildRequiredKey(instanceId, joinNodeCode), String.class);
      if (requiredStr == null) {
        // 未设置 required，回退到全部分支到达语义
        return allArrived(instanceId, joinNodeCode);
      }
      int required = Integer.parseInt(requiredStr);
      String arrivedStr =
          redisStringOps.get(buildArrivedKey(instanceId, joinNodeCode), String.class);
      if (arrivedStr == null) {
        return false;
      }
      return Long.parseLong(arrivedStr) >= required;
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] P0-3 检查 N/M 条件失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
      return allArrived(instanceId, joinNodeCode);
    }
  }

  /**
   * 清除 join 令牌：删除到达计数与分支总数 key。
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
      redisStringOps.del(buildArrivedKey(instanceId, joinNodeCode));
      redisStringOps.del(buildTotalKey(instanceId, joinNodeCode));
      redisStringOps.del(buildRequiredKey(instanceId, joinNodeCode));
      log.info("[FlowJoinToken] 清除 join 令牌 instanceId={} node={}", instanceId, joinNodeCode);
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] 清除令牌失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
    }
  }

  /**
   * 检查 join 令牌是否已初始化（total key 是否存在）
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=令牌已初始化
   */
  @Override
  public boolean isInitialized(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    try {
      Boolean exists = redisStringOps.hasKey(buildTotalKey(instanceId, joinNodeCode));
      return Boolean.TRUE.equals(exists);
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] 检查初始化状态失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
      return false;
    }
  }

  // ============================== 私有辅助 ==============================

  /**
   * 读取分支总数，未初始化时返回 Integer.MAX_VALUE（避免误判为已全部到达）
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return 分支总数；未初始化返回 Integer.MAX_VALUE
   */
  private int readTotal(String instanceId, String joinNodeCode) {
    try {
      String totalStr = redisStringOps.get(buildTotalKey(instanceId, joinNodeCode), String.class);
      if (totalStr == null) {
        // 未初始化：返回最大值，确保 allArrived 返回 false（fail-safe）
        log.warn("[FlowJoinToken] 分支总数未初始化 instanceId={} node={}", instanceId, joinNodeCode);
        return Integer.MAX_VALUE;
      }
      return Integer.parseInt(totalStr);
    } catch (NumberFormatException e) {
      log.warn(
          "[FlowJoinToken] 分支总数非数字 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
      return Integer.MAX_VALUE;
    } catch (Exception e) {
      log.warn(
          "[FlowJoinToken] 读取分支总数失败 instanceId={} node={} err={}",
          instanceId,
          joinNodeCode,
          e.getMessage());
      return Integer.MAX_VALUE;
    }
  }

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
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return Redis 到达计数 key
   */
  private String buildArrivedKey(String instanceId, String joinNodeCode) {
    return KEY_PREFIX + instanceId + ":" + joinNodeCode;
  }

  /**
   * 构建分支总数 key：flow:join:{instanceId}:{joinNodeCode}:total
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return Redis 分支总数 key
   */
  private String buildTotalKey(String instanceId, String joinNodeCode) {
    return buildArrivedKey(instanceId, joinNodeCode) + TOTAL_SUFFIX;
  }

  /**
   * P0-3: 构建 N/M join required key：flow:join:{instanceId}:{joinNodeCode}:required
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return Redis N/M join required key
   */
  private String buildRequiredKey(String instanceId, String joinNodeCode) {
    return buildArrivedKey(instanceId, joinNodeCode) + REQUIRED_SUFFIX;
  }
}
