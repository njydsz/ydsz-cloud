package com.njydsz.workflow.server.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 审批人可用性服务 — 基于待办计数和活跃时间的智能负载感知
 *
 * <p>对标钉钉/飞书「审批人忙碌状态」能力。通过 Redis 统计每个审批人的当前待办数量和最后活跃时间， 为会签/或签场景提供「最空闲优先」的审批人推荐策略，避免将任务分配给已过载的用户。
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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAssigneeAvailabilityService {

  private final RedisStringOps redisStringOps;

  private static final String TODO_COUNT_PREFIX = "flow:assignee:todo_count:";
  private static final String LAST_ACTIVE_PREFIX = "flow:assignee:last_active:";
  private static final Duration TTL = Duration.ofDays(7);

  /** 待办数阈值 */
  private static final int BUSY_THRESHOLD = 10;

  private static final int OVERLOADED_THRESHOLD = 20;

  /**
   * 增加审批人待办计数
   *
   * @param userId 参数说明
   */
  public void incTodoCount(String userId) {
    if (!StringUtils.hasText(userId)) {
      return;
    }
    try {
      String key = TODO_COUNT_PREFIX + userId;
      Long count = redisStringOps.incr(key, 1);
      if (count != null && count == 1) {
        redisStringOps.expire(key, TTL);
      }
      updateLastActive(userId);
    } catch (Exception e) {
      log.warn("[Availability] 增加待办计数失败 userId={} err={}", userId, e.getMessage());
    }
  }

  /**
   * 减少审批人待办计数
   *
   * @param userId 参数说明
   */
  public void decTodoCount(String userId) {
    if (!StringUtils.hasText(userId)) {
      return;
    }
    try {
      String key = TODO_COUNT_PREFIX + userId;
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
    Map<String, Object> result = new HashMap<>();
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
   * 批量查询审批人忙碌状态
   *
   * @param userIds 用户 ID 列表
   * @return userId → availability Map
   */
  public Map<String, Map<String, Object>> batchGetAvailability(Set<String> userIds) {
    Map<String, Map<String, Object>> result = new HashMap<>();
    if (userIds == null || userIds.isEmpty()) {
      return result;
    }
    for (String userId : userIds) {
      result.put(userId, getAvailability(userId));
    }
    return result;
  }

  /**
   * 推荐最空闲的审批人（从候选人中选择）
   *
   * @param candidateUserIds 候选人列表
   * @return 最空闲的候选人 userId，列表为空时返回 null
   */
  public String recommendLeastBusy(List<String> candidateUserIds) {
    if (candidateUserIds == null || candidateUserIds.isEmpty()) {
      return null;
    }
    String bestUser = null;
    int minCount = Integer.MAX_VALUE;
    for (String userId : candidateUserIds) {
      int count = getTodoCount(userId);
      if (count < minCount) {
        minCount = count;
        bestUser = userId;
      }
    }
    return bestUser;
  }

  // ============================== 私有方法 ==============================

  private int getTodoCount(String userId) {
    try {
      String val = redisStringOps.get(TODO_COUNT_PREFIX + userId, String.class);
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
      return redisStringOps.get(LAST_ACTIVE_PREFIX + userId, String.class);
    } catch (Exception e) {
      log.warn("[Availability] 查询活跃时间失败 userId={}, err={}", userId, e.getMessage());
      return null;
    }
  }

  private void updateLastActive(String userId) {
    try {
      String key = LAST_ACTIVE_PREFIX + userId;
      String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      redisStringOps.set(key, now, TTL);
    } catch (Exception e) {
      log.debug("[Availability] 更新活跃时间失败 userId={} err={}", userId, e.getMessage());
    }
  }
}
