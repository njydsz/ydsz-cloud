package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.workflow.server.service.FlowJoinTokenService;

/**
 * 流程加签 Token 服务实现。
 *
 * <p>管理加签/减签/转签的短期 Token ({@code ydsz_flow_join_token})：
 *
 * <p>加签发起人生成 Token → 受邀人通过 Token 链接加入审批 → Token 一次性使用后失效。
 *
 * <p>支持过期时间、租户隔离、操作审计。
 *
 * @author ydsz-team
 * @since 1.0.0
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
  private static final Duration TTL = Duration.ofDays(7);

  /** 默认 TTL 秒数（Lua 脚本用） */
  private static final long TTL_SECONDS = TTL.getSeconds();

  /** Redis 模板，操作 join 令牌计数 key（原子 Lua 脚本保证并发安全） */
  private final RedisTemplate<String, Object> redisTemplate;

  /** Redis String 操作组件（get/hasKey/delete） */
  private final RedisStringOps redisStringOps;

  /**
   * P1-7: 初始化脚本 —— 原子写入 total + arrived 并带 TTL。 KEYS[1]=arrivedKey, KEYS[2]=totalKey,
   * ARGV[1]=total, ARGV[2]=ttlSeconds
   *
   * <pre>
   *   redis.call('SET', KEYS[1], '0', 'EX', ARGV[2])
   *   redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
   *   return 1
   * </pre>
   */
  private static final RedisScript<Long> INIT_SCRIPT =
      new DefaultRedisScript<>(
          "redis.call('SET', KEYS[1], '0', 'EX', ARGV[2])\n"
              + "redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])\n"
              + "return 1",
          Long.class);

  /**
   * P1-7: 到达脚本 —— 原子 INCR arrived + 比较 total + 补设 TTL，返回是否全部到达。 KEYS[1]=arrivedKey,
   * KEYS[2]=totalKey, ARGV[1]=ttlSeconds
   *
   * <pre>
   *   local arrived = redis.call('INCR', KEYS[1])
   *   redis.call('EXPIRE', KEYS[1], ARGV[1])
   *   local total = tonumber(redis.call('GET', KEYS[2]))
   *   if total and arrived >= total then
   *     return 1
   *   end
   *   return 0
   * </pre>
   */
  private static final RedisScript<Long> ARRIVE_SCRIPT =
      new DefaultRedisScript<>(
          "local arrived = redis.call('INCR', KEYS[1])\n"
              + "redis.call('EXPIRE', KEYS[1], ARGV[1])\n"
              + "local total = tonumber(redis.call('GET', KEYS[2]))\n"
              + "if total and arrived >= total then\n"
              + "  return 1\n"
              + "end\n"
              + "return 0",
          Long.class);

  /**
   * P0-3: N/M join 初始化脚本 —— 原子写入 total + required + arrived 并带 TTL。 KEYS[1]=arrivedKey,
   * KEYS[2]=totalKey, KEYS[3]=requiredKey, ARGV[1]=total, ARGV[2]=required, ARGV[3]=ttlSeconds
   */
  private static final RedisScript<Long> INIT_REQUIRED_SCRIPT =
      new DefaultRedisScript<>(
          "redis.call('SET', KEYS[1], '0', 'EX', ARGV[3])\n"
              + "redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3])\n"
              + "redis.call('SET', KEYS[3], ARGV[2], 'EX', ARGV[3])\n"
              + "return 1",
          Long.class);

  /**
   * P0-3: N/M join 到达脚本 —— INCR arrived + 比较 required + 补设 TTL。 KEYS[1]=arrivedKey,
   * KEYS[2]=requiredKey, ARGV[1]=ttlSeconds
   */
  private static final RedisScript<Long> ARRIVE_REQUIRED_SCRIPT =
      new DefaultRedisScript<>(
          "local arrived = redis.call('INCR', KEYS[1])\n"
              + "redis.call('EXPIRE', KEYS[1], ARGV[1])\n"
              + "local required = tonumber(redis.call('GET', KEYS[2]))\n"
              + "if required and arrived >= required then\n"
              + "  return 1\n"
              + "end\n"
              + "return 0",
          Long.class);

  // ============================== 接口实现 ==============================

  /**
   * 初始化 join 令牌：写入分支总数并重置到达计数
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
      // P1-7: 原子写入 total + arrived 并带 TTL（单条 Lua 脚本）
      redisTemplate.execute(
          INIT_SCRIPT,
          List.of(arrivedKey, totalKey),
          String.valueOf(total),
          String.valueOf(TTL_SECONDS));
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
   * 标记一个分支已到达：INCR 到达计数并判断是否全部到达
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
      // P1-7: 原子 INCR + 比较 total + 补设 TTL（单条 Lua 脚本，消除并发竞态）
      Long result =
          redisTemplate.execute(
              ARRIVE_SCRIPT, List.of(arrivedKey, totalKey), String.valueOf(TTL_SECONDS));
      boolean allArrived = result != null && result == 1L;
      log.debug(
          "[FlowJoinToken] 分支到达 instanceId={} node={} allArrived={}",
          instanceId,
          joinNodeCode,
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
   * 检查是否所有分支都已到达
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
   * P0-3: 初始化 N/M join 令牌
   *
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @param branchCount 参数说明
   * @param requiredCount 参数说明
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
      redisTemplate.execute(
          INIT_REQUIRED_SCRIPT,
          List.of(arrivedKey, totalKey, requiredKey),
          String.valueOf(total),
          String.valueOf(required),
          String.valueOf(TTL_SECONDS));
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
   * P0-3: 标记分支到达并检查 N/M 聚合条件
   *
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @return 返回值说明
   */
  @Override
  public boolean arriveTokenWithRequired(String instanceId, String joinNodeCode) {
    if (!isValidParam(instanceId, joinNodeCode)) {
      return false;
    }
    String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
    String requiredKey = buildRequiredKey(instanceId, joinNodeCode);
    try {
      // 先尝试 N/M 评估
      Long result =
          redisTemplate.execute(
              ARRIVE_REQUIRED_SCRIPT,
              List.of(arrivedKey, requiredKey),
              String.valueOf(TTL_SECONDS));
      if (result != null && result == 1L) {
        log.debug(
            "[FlowJoinToken] P0-3 N/M 聚合条件满足 instanceId={} node={}", instanceId, joinNodeCode);
        return true;
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
   * P0-3: 检查是否满足 N/M 聚合条件
   *
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @return 返回值说明
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
   * 清除 join 令牌：删除到达计数与分支总数 key
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
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @return 返回值说明
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
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @return 返回值说明
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
   * 参数合法性校验
   *
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @return 返回值说明
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
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @return 返回值说明
   */
  private String buildArrivedKey(String instanceId, String joinNodeCode) {
    return KEY_PREFIX + instanceId + ":" + joinNodeCode;
  }

  /**
   * 构建分支总数 key：flow:join:{instanceId}:{joinNodeCode}:total
   *
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @return 返回值说明
   */
  private String buildTotalKey(String instanceId, String joinNodeCode) {
    return buildArrivedKey(instanceId, joinNodeCode) + TOTAL_SUFFIX;
  }

  /**
   * P0-3: 构建 N/M join required key：flow:join:{instanceId}:{joinNodeCode}:required
   *
   * @param instanceId 参数说明
   * @param joinNodeCode 参数说明
   * @return 返回值说明
   */
  private String buildRequiredKey(String instanceId, String joinNodeCode) {
    return buildArrivedKey(instanceId, joinNodeCode) + REQUIRED_SUFFIX;
  }
}
