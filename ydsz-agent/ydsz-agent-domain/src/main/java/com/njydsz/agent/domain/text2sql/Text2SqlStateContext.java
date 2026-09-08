package com.njydsz.agent.domain.text2sql;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Text2SQL 状态上下文值对象。
 *
 * <p>在 StateGraph 编排过程中跨节点传递的不可变上下文，携带用户原始问题、租户 ID、
 * Schema 召回结果、生成的 SQL 及各项校验结论。
 *
 * <p><b>线程安全</b>：全字段 final 且集合经不可变封装，实例不可变。
 * 修改操作通过 {@link Builder} 创建新实例。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class Text2SqlStateContext implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 默认集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  private final String query;
  private final String tenantId;
  private final List<TableSchema> recalledSchemas;
  private final String generatedSql;
  private final Boolean validResult;
  private final Double feasibilityScore;
  private final String feasibilityReason;
  private final Double consistencyScore;
  private final String consistencyReason;
  private final Text2SqlStateNode failedNode;
  private final String failureReason;

  /**
   * 全参构造（私有，由 Builder 调用）。
   */
  private Text2SqlStateContext(
      String query,
      String tenantId,
      List<TableSchema> recalledSchemas,
      String generatedSql,
      Boolean validResult,
      Double feasibilityScore,
      String feasibilityReason,
      Double consistencyScore,
      String consistencyReason,
      Text2SqlStateNode failedNode,
      String failureReason) {
    this.query = Objects.requireNonNull(query, "query 不能为 null");
    this.tenantId = tenantId;
    this.recalledSchemas = recalledSchemas != null ? List.copyOf(recalledSchemas) : List.of();
    this.generatedSql = generatedSql;
    this.validResult = validResult;
    this.feasibilityScore = feasibilityScore;
    this.feasibilityReason = feasibilityReason;
    this.consistencyScore = consistencyScore;
    this.consistencyReason = consistencyReason;
    this.failedNode = failedNode;
    this.failureReason = failureReason;
  }

  /**
   * 创建 Builder（用于组装上下文）。
   *
   * @param query 用户原始自然语言问题（必填）
   * @param tenantId 租户 ID
   * @return Builder 实例
   */
  public static Builder builder(String query, String tenantId) {
    return new Builder(query, tenantId);
  }

  public String getQuery() {
    return query;
  }

  public String getTenantId() {
    return tenantId;
  }

  public List<TableSchema> getRecalledSchemas() {
    return recalledSchemas;
  }

  public Optional<String> getGeneratedSql() {
    return Optional.ofNullable(generatedSql);
  }

  public Optional<Boolean> getValidResult() {
    return Optional.ofNullable(validResult);
  }

  public Optional<Double> getFeasibilityScore() {
    return Optional.ofNullable(feasibilityScore);
  }

  public Optional<String> getFeasibilityReason() {
    return Optional.ofNullable(feasibilityReason);
  }

  public Optional<Double> getConsistencyScore() {
    return Optional.ofNullable(consistencyScore);
  }

  public Optional<String> getConsistencyReason() {
    return Optional.ofNullable(consistencyReason);
  }

  public Optional<Text2SqlStateNode> getFailedNode() {
    return Optional.ofNullable(failedNode);
  }

  public Optional<String> getFailureReason() {
    return Optional.ofNullable(failureReason);
  }

  /**
   * 判断链路是否完整通过（无失败节点）。
   *
   * @return true=链路完整通过
   */
  public boolean isSuccessful() {
    return failedNode == null;
  }

  /**
   * Text2SqlStateContext 构建器。
   */
  public static final class Builder {

    private final String query;
    private final String tenantId;
    private List<TableSchema> recalledSchemas;
    private String generatedSql;
    private Boolean validResult;
    private Double feasibilityScore;
    private String feasibilityReason;
    private Double consistencyScore;
    private String consistencyReason;
    private Text2SqlStateNode failedNode;
    private String failureReason;

    private Builder(String query, String tenantId) {
      this.query = query;
      this.tenantId = tenantId;
      this.recalledSchemas = new ArrayList<>(COLLECTION_CAPACITY);
    }

    /**
     * 设置召回的相关表 Schema 列表。
     *
     * @param recalledSchemas 召回的 Schema 列表
     * @return Builder
     */
    public Builder recalledSchemas(List<TableSchema> recalledSchemas) {
      this.recalledSchemas =
          recalledSchemas != null
              ? new ArrayList<>(recalledSchemas)
              : new ArrayList<>(COLLECTION_CAPACITY);
      return this;
    }

    /**
     * 设置生成的 SQL。
     *
     * @param generatedSql SQL 字符串
     * @return Builder
     */
    public Builder generatedSql(String generatedSql) {
      this.generatedSql = generatedSql;
      return this;
    }

    /**
     * 设置安全校验结论。
     *
     * @param validResult true=通过
     * @return Builder
     */
    public Builder validResult(boolean validResult) {
      this.validResult = validResult;
      return this;
    }

    /**
     * 设置可行性评估分数。
     *
     * @param feasibilityScore 0.0-1.0 分数
     * @return Builder
     */
    public Builder feasibilityScore(double feasibilityScore) {
      this.feasibilityScore = feasibilityScore;
      return this;
    }

    /**
     * 设置可行性评估原因。
     *
     * @param feasibilityReason 原因说明
     * @return Builder
     */
    public Builder feasibilityReason(String feasibilityReason) {
      this.feasibilityReason = feasibilityReason;
      return this;
    }

    /**
     * 设置语义一致性分数。
     *
     * @param consistencyScore 0.0-1.0 分数
     * @return Builder
     */
    public Builder consistencyScore(double consistencyScore) {
      this.consistencyScore = consistencyScore;
      return this;
    }

    /**
     * 设置语义一致性校验原因。
     *
     * @param consistencyReason 原因说明
     * @return Builder
     */
    public Builder consistencyReason(String consistencyReason) {
      this.consistencyReason = consistencyReason;
      return this;
    }

    /**
     * 设置失败节点。
     *
     * @param failedNode 失败节点枚举
     * @return Builder
     */
    public Builder failedNode(Text2SqlStateNode failedNode) {
      this.failedNode = failedNode;
      return this;
    }

    /**
     * 设置失败原因。
     *
     * @param failureReason 失败原因描述
     * @return Builder
     */
    public Builder failureReason(String failureReason) {
      this.failureReason = failureReason;
      return this;
    }

    /**
     * 构建 Text2SqlStateContext 实例。
     *
     * @return 不可变上下文实例
     */
    public Text2SqlStateContext build() {
      return new Text2SqlStateContext(
          query,
          tenantId,
          recalledSchemas,
          generatedSql,
          validResult,
          feasibilityScore,
          feasibilityReason,
          consistencyScore,
          consistencyReason,
          failedNode,
          failureReason);
    }
  }

  @Override
  public String toString() {
    return "Text2SqlStateContext{query='"
        + query
        + "', failedNode="
        + failedNode
        + ", feasibilityScore="
        + feasibilityScore
        + ", consistencyScore="
        + consistencyScore
        + '}';
  }
}
