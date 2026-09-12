package com.njydsz.agent.domain.analysis;

import java.util.Map;

/**
 * 数据分析请求值对象（不可变 record）。
 *
 * <p>封装用户自然语言查询 + 数据源类型的组合请求。
 * 描述一次数据分析任务的输入参数，由 {@link DataSourceAnalysisService#analyze} 消费。
 *
 * @param userId 触发用户
 * @param query 自然语言分析查询
 * @param dataSourceType 数据源类型（sql / python / mixed）
 * @param tenantId 租户 ID
 * @param extraParams 额外参数
 * @author ydsz-team
 * @since 26.09.07
 */
public record DataSourceAnalysisRequest(
    String userId,
    String query,
    String dataSourceType,
    String tenantId,
    Map<String, String> extraParams) {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /**
   * 全参构造。
   *
   * @param userId 触发用户
   * @param query 自然语言分析查询
   * @param dataSourceType 数据源类型（sql / python / mixed）
   * @param tenantId 租户 ID
   * @param extraParams 额外参数
   */
  public DataSourceAnalysisRequest {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId 不能为空");
    }
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("query 不能为空");
    }
    if (dataSourceType == null || dataSourceType.isBlank()) {
      dataSourceType = "mixed";
    }
    if (extraParams == null) {
      extraParams = Map.of();
    } else {
      extraParams = Map.copyOf(extraParams);
    }
  }

  /**
   * 简化构造：仅必要字段。
   *
   * @param userId 触发用户
   * @param query 自然语言分析查询
   * @return 简化构造的实例
   */
  public static DataSourceAnalysisRequest of(String userId, String query) {
    return new DataSourceAnalysisRequest(userId, query, "mixed", null, Map.of());
  }
}
