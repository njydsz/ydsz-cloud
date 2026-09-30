package com.njydsz.workflow.server.cache;

import org.springframework.stereotype.Component;

/**
 * Workflow 模块缓存键构造器（P2-2 整改：集中管理 Redis key 前缀常量）。
 *
 * <p>统一封装 workflow 模块各 Service 散落的 Redis key 前缀（{@code flow:assignee:todo_count:}、
 * {@code flow:join:}、{@code flow:urge:} 等），消除硬编码字符串常量，提升可维护性。
 *
 * <p><b>命名约定：</b>workflow 模块历史使用 {@code flow:} 前缀，为保持与线上已有 key 兼容，
 * 本构造器统一沿用 {@code flow:} 前缀格式。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * @Service
 * public class FlowAssigneeAvailabilityService {
 *   private final CacheKeyBuilder cacheKeyBuilder;
 *
 *   public void incTodoCount(String userId) {
 *     String key = cacheKeyBuilder.assigneeTodoCount(userId);
 *   }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.30
 */
@Component("workflowCacheKeyBuilder")
public class CacheKeyBuilder {

  // ============================== 审批人负载 key ==============================

  private static final String ASSIGNEE_TODO_COUNT_PREFIX = "flow:assignee:todo_count:";
  private static final String ASSIGNEE_LAST_ACTIVE_PREFIX = "flow:assignee:last_active:";

  // ============================== 流程合并 key ==============================

  private static final String MERGE_GROUP_KEY_PREFIX = "ydsz:flow:merge:group:";
  private static final String MERGE_GROUP_DETAIL_KEY_PREFIX = "ydsz:flow:merge:detail:";

  // ============================== 会签令牌 key ==============================

  private static final String JOIN_TOKEN_PREFIX = "flow:join:";
  private static final String JOIN_TOTAL_SUFFIX = ":total";
  private static final String JOIN_REQUIRED_SUFFIX = ":required";

  // ============================== 全局计数 ==============================

  /** 工作流待办计数全局 key（无租户维度，全局统计） */
  private static final String WORKFLOW_TASK_COUNT_TOTAL = "ydsz:workflow:task:count:total";

  // ============================== 审批催促限流 key ==============================

  private static final String URGE_LIMIT_PREFIX = "flow:urge:";

  // ============================== 消息 Pub/Sub channel ==============================

  private static final String MESSAGE_CHANNEL_PREFIX = "flow:message:";

  // ============================== 审批人负载 key ==============================

  /**
   * 生成审批人待办计数 key。
   *
   * <p>格式：{@code flow:assignee:todo_count:{userId}}
   *
   * @param userId 审批人用户 ID
   * @return Redis key
   */
  public String assigneeTodoCount(String userId) {
    return ASSIGNEE_TODO_COUNT_PREFIX + nullSafe(userId);
  }

  /**
   * 生成审批人最后活跃时间 key。
   *
   * <p>格式：{@code flow:assignee:last_active:{userId}}
   *
   * @param userId 审批人用户 ID
   * @return Redis key
   */
  public String assigneeLastActive(String userId) {
    return ASSIGNEE_LAST_ACTIVE_PREFIX + nullSafe(userId);
  }

  // ============================== 流程合并 key ==============================

  /**
   * 生成流程合并组 key。
   *
   * <p>格式：{@code ydsz:flow:merge:group:{mergeGroupId}}
   *
   * @param mergeGroupId 合并组 ID
   * @return Redis key
   */
  public String mergeGroup(String mergeGroupId) {
    return MERGE_GROUP_KEY_PREFIX + nullSafe(mergeGroupId);
  }

  /**
   * 生成流程合并组详情 key。
   *
   * <p>格式：{@code ydsz:flow:merge:detail:{mergeGroupId}}
   *
   * @param mergeGroupId 合并组 ID
   * @return Redis key
   */
  public String mergeGroupDetail(String mergeGroupId) {
    return MERGE_GROUP_DETAIL_KEY_PREFIX + nullSafe(mergeGroupId);
  }

  // ============================== 会签令牌 key ==============================

  /**
   * 生成会签基础 key（到达计数 key 或 total/required 前缀）。
   *
   * <p>格式：{@code flow:join:{instanceId}:{joinNodeCode}}
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return Redis 会签基础 key
   */
  public String joinToken(String instanceId, String joinNodeCode) {
    return JOIN_TOKEN_PREFIX + nullSafe(instanceId) + ":" + nullSafe(joinNodeCode);
  }

  /**
   * 生成会签 total 令牌 key。
   *
   * <p>格式：{@code flow:join:{joinKey}:total}
   *
   * @param joinKey 会签标识
   * @return Redis key
   */
  public String joinTokenTotal(String joinKey) {
    return JOIN_TOKEN_PREFIX + nullSafe(joinKey) + JOIN_TOTAL_SUFFIX;
  }

  /**
   * 生成会签 required 令牌 key。
   *
   * <p>格式：{@code flow:join:{joinKey}:required}
   *
   * @param joinKey 会签标识
   * @return Redis key
   */
  public String joinTokenRequired(String joinKey) {
    return JOIN_TOKEN_PREFIX + nullSafe(joinKey) + JOIN_REQUIRED_SUFFIX;
  }

  // ============================== 消息 Pub/Sub channel ==============================

  /**
   * 生成流程消息广播 channel 名。
   *
   * <p>格式：{@code flow:message:{channelSuffix}}
   *
   * @param channelSuffix 频道后缀（如 "event"、"definition:invalidate" 等）
   * @return channel 名
   */
  public String messageChannel(String channelSuffix) {
    return MESSAGE_CHANNEL_PREFIX + nullSafe(channelSuffix);
  }

  // ============================== 流程全局计数 ==============================

  /**
   * 获取工作流待办计数全局 key（无租户维度）。
   *
   * <p>格式：{@code ydzs:workflow:task:count:total}
   *
   * @return 全局 key 常量
   */
  public String workflowTaskCountTotal() {
    return WORKFLOW_TASK_COUNT_TOTAL;
  }

  // ============================== 审批催促限流 key ==============================

  /**
   * 生成催促限流 key。
   *
   * <p>格式：{@code flow:urge:{targetType}:{targetId}:by:{userId}}
   *
   * @param targetType 目标类型
   * @param targetId 目标 ID
   * @param userId 催促发起人
   * @return 限流 key
   */
  public String urgeLimit(String targetType, String targetId, String userId) {
    return URGE_LIMIT_PREFIX + nullSafe(targetType) + ":" + nullSafe(targetId) + ":by:" + nullSafe(userId);
  }

  // ============================== 内部工具 ==============================

  /** 防空处理：{@code null} 转为空字符串 */
  private static String nullSafe(String value) {
    return value != null ? value : "";
  }
}
