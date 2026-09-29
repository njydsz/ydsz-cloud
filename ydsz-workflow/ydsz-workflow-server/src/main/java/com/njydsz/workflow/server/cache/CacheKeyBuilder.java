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

  // ============================== 内部工具 ==============================

  /** 防空处理：{@code null} 转为空字符串 */
  private static String nullSafe(String value) {
    return value != null ? value : "";
  }
}
