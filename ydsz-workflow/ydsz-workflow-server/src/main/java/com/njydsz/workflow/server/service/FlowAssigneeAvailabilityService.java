package com.njydsz.workflow.server.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.date.DateUtils;
import com.njydsz.workflow.server.cache.CacheKeyBuilder;

/**
 * 审批人可用性服务 — 基于待办计数和活跃时间的智能负载感知
 *
 * <p>审批人忙碌状态能力。通过 Redis 统计每个审批人的当前待办数量和最后活跃时间， 为会签/或签场景提供「最空闲优先」的审批人推荐策略，避免将任务分配给已过载的用户。
 *
 * <p><b>状态分级：</b>
 *
 * <ul>
 *   <li>{@code IDLE}（待办数 = 0）— 空闲，推荐优先分配
 *   <li>{@code NORMAL}（待办数 < 10）— 正常
 *   <li>{@code BUSY}（待办数 10~19）— 繁忙，建议降级分配
 *   <li>{@code OVERLOADED}（待办数 ≥ 20）— 过载，不建议分配
 * </ul>
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <ul>
 *   <li>{@code flow:assignee:todo_count:{userId}} — 待办计数（INCR/DECR 原子操作，TTL 7 天）
 *   <li>{@code flow:assignee:last_active:{userId}} — 最后活跃时间（ISO LocalDateTime 格式）
 * </ul>
 *
 * <p><b>P2-2 整改（26.09.30）</b>：使用 {@link CacheKeyBuilder} 集中管理 key 构造，消除硬编码字符串常量。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAssigneeAvailabilityService {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** P2-2 整改：缓存键构造器（集中管理 flow:assignee:* 前缀） */
  private final CacheKeyBuilder cacheKeyBuilder;

  private final RedisStringOps redisStringOps;

  /** 审批人状态缓存 TTL（天），默认 7 天 */
  @Value("${ydsz.workflow.assignee.cache-ttl-days:7}")
  private long assigneeCacheTtlDays;

  /** 待办数阈值 */
  private static final int BUSY_THRESHOLD = 10;

  private static final int OVERLOADED_THRESHOLD = 20;

  /**
   * 增加审批人待办计数
   *
   * @param userId 审批人用户 ID
   */
  public void incTodoCount(String userId) {
    if (!StringUtils.hasText(userId)) {
      return;
    }
    try {
      String key = cacheKeyBuilder.assigneeTodoCount(userId);
      Long count = redisStringOps.incr(key, 1);
      if (count != null && count == 1) {
        redisStringOps.expire(key, Duration.ofDays(assigneeCacheTtlDays));
      }
      updateLastActive(userId);
    } catch (Exception e) {
      log.warn("[Availability] 增加待办计数失败 userId={} err={}", userId, e.getMessage());
    }
  }

  /**
   * 减少审批人待办计数
   *
   * @param userId 审批人用户 ID
   */
  public void decTodoCount(String userId) {
    if (!StringUtils.hasText(userId)) {
      return;
    }
    try {
      String key = cacheKeyBuilder.assigneeTodoCount(userId);
      long count = redisStringOps.decr(key, 1);
      if (count <= 0) {
        redisStringOps.del(key);
      }
      updateLastActive(userId);
    } catch (Exception e) {
      log.warn("[Availability] 减少待办计数失败 userId={} err={}", userId, e.getMessage());
    }
  }

  /**
   * 查询审批人忙碌状态
   *
   * @param userId 用户 ID
   * @return Map 包含：status (IDLE/NORMAL/BUSY/OVERLOADED), todoCount, lastActive
   */
  public Map<String, Object> getAvailability(String userId) {
    Map<String, Object> result = new HashMap<>(COLLECTION_CAPACITY);
    result.put("userId", userId);
    result.put("date", LocalDate.now().toString());

    int todoCount = getTodoCount(userId);
    result.put("todoCount", todoCount);

    String status;
    if (todoCount == 0) {
      status = "IDLE";
    } else if (todoCount < BUSY_THRESHOLD) {
      status = "NORMAL";
    } else if (todoCount < OVERLOADED_THRESHOLD) {
      status = "BUSY";
    } else {
      status = "OVERLOADED";
    }
    result.put("status", status);

    String lastActive = getLastActive(userId);
    result.put("lastActive", lastActive);

    return result;
  }

  /**
   * 批量查询审批人忙碌状态（Pipeline MGET 优化，2N 次 RTT → 2 次 RTT）
   *
   * @param userIds 用户 ID 列表
   * @return userId → availability Map
   */
  public Map<String, Map<String, Object>> batchGetAvailability(Set<String> userIds) {
    Map<String, Map<String, Object>> result = new HashMap<>(COLLECTION_CAPACITY);
    if (userIds == null || userIds.isEmpty()) {
      return result;
    }

    List<String> userIdList = new ArrayList<>(userIds);
    int size = userIdList.size();
    List<String> todoCountKeys = new ArrayList<>(size);
    List<String> lastActiveKeys = new ArrayList<>(size);
    for (String userId : userIdList) {
      todoCountKeys.add(cacheKeyBuilder.assigneeTodoCount(userId));
      lastActiveKeys.add(cacheKeyBuilder.assigneeLastActive(userId));
    }

    try {
      List<String> todoCountValues = redisStringOps.multiGetPipelined(todoCountKeys);
      List<String> lastActiveValues = redisStringOps.multiGetPipelined(lastActiveKeys);

      LocalDate today = LocalDate.now();
      for (int i = 0; i < size; i++) {
        String userId = userIdList.get(i);
        Map<String, Object> availability = new HashMap<>(COLLECTION_CAPACITY);
        availability.put("userId", userId);
        availability.put("date", today.toString());

        int todoCount = parseTodoCount(todoCountValues, i);
        availability.put("todoCount", todoCount);
        availability.put("status", calculateStatus(todoCount));

        if (i < lastActiveValues.size()) {
          availability.put("lastActive", lastActiveValues.get(i));
        }

        result.put(userId, availability);
      }
    } catch (Exception e) {
      log.warn("[Availability] 批量查询失败 userIdCount={} err={}", size, e.getMessage());
      for (String userId : userIdList) {
        result.put(userId, getAvailability(userId));
      }
    }

    return result;
  }

  /**
   * 推荐最空闲的审批人（从候选人中选择，Pipeline 批量获取待办计数）
   *
   * @param candidateUserIds 候选人列表
   * @return 最空闲的候选人 userId，列表为空时返回 null
   */
  public String recommendLeastBusy(List<String> candidateUserIds) {
    if (candidateUserIds == null || candidateUserIds.isEmpty()) {
      return null;
    }

    List<String> keys = new ArrayList<>(candidateUserIds.size());
    for (String userId : candidateUserIds) {
      keys.add(cacheKeyBuilder.assigneeTodoCount(userId));
    }

    try {
      List<String> values = redisStringOps.multiGetPipelined(keys);
      String bestUser = null;
      int minCount = Integer.MAX_VALUE;
      for (int i = 0; i < candidateUserIds.size(); i++) {
        int count = parseTodoCount(values, i);
        if (count < minCount) {
          minCount = count;
          bestUser = candidateUserIds.get(i);
        }
      }
      return bestUser;
    } catch (Exception e) {
      log.warn("[Availability] 批量获取待办计数失败 candidateCount={} err={}", candidateUserIds.size(), e.getMessage());
      return null;
    }
  }

  // ============================== 私有方法 ==============================

  private int getTodoCount(String userId) {
    try {
      String val = redisStringOps.get(cacheKeyBuilder.assigneeTodoCount(userId), String.class);
      if (val == null) {
        return 0;
      }
      return Integer.parseInt(val);
    } catch (Exception e) {
      log.warn("[Availability] 查询待办计数失败 userId={}, err={}", userId, e.getMessage());
      return 0;
    }
  }

  private String getLastActive(String userId) {
    try {
      return redisStringOps.get(cacheKeyBuilder.assigneeLastActive(userId), String.class);
    } catch (Exception e) {
      log.warn("[Availability] 查询活跃时间失败 userId={}, err={}", userId, e.getMessage());
      return null;
    }
  }

  /**
   * 解析 Pipeline 批量查询结果中指定索引的待办计数
   *
   * @param values Pipeline 查询结果
   * @param index 索引
   * @return 待办计数（解析失败或 null 返回 0）
   */
  private int parseTodoCount(List<String> values, int index) {
    if (values == null || index >= values.size() || values.get(index) == null) {
      return 0;
    }
    try {
      return Integer.parseInt(values.get(index));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * 根据待办计数计算忙碌状态
   *
   * @param todoCount 待办计数
   * @return 状态标识（IDLE/NORMAL/BUSY/OVERLOADED）
   */
  private String calculateStatus(int todoCount) {
    if (todoCount == 0) {
      return "IDLE";
    }
    if (todoCount < BUSY_THRESHOLD) {
      return "NORMAL";
    }
    if (todoCount < OVERLOADED_THRESHOLD) {
      return "BUSY";
    }
    return "OVERLOADED";
  }

  private void updateLastActive(String userId) {
    try {
      String key = cacheKeyBuilder.assigneeLastActive(userId);
      String now = DateUtils.now();
      redisStringOps.set(key, now, Duration.ofDays(assigneeCacheTtlDays));
    } catch (Exception e) {
      log.debug("[Availability] 更新活跃时间失败 userId={} err={}", userId, e.getMessage());
    }
  }
}
