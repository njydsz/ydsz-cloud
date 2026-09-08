package com.njydsz.userinfo.domain.provision;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 供给执行结果（P0-1 Identity Provisioning 管道）。
 *
 * <p>记录一次同步执行的处理统计，包括新增、更新、停用、失败等计数，
 * 以及执行耗时和错误详情。供管理界面展示和审计日志使用。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @param connectorType 执行同步的连接器类型
 * @param totalProcessed 处理总数
 * @param created 新增用户数
 * @param updated 更新用户数
 * @param deactivated 停用用户数
 * @param failed 失败数
 * @param durationMs 执行耗时（毫秒）
 * @param syncToken 本次同步后的增量令牌（供下次增量拉取）
 * @param errors 错误详情列表（最多保留 100 条，超出截断并记录总数）
 */
public record ProvisionResult(
    String connectorType,
    int totalProcessed,
    int created,
    int updated,
    int deactivated,
    int failed,
    long durationMs,
    String syncToken,
    List<String> errors)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 错误详情最大保留条数 */
  private static final int MAX_ERRORS = 100;

  /**
   * 构造成功的供给结果。
   *
   * @param connectorType 连接器类型
   * @param totalProcessed 处理总数
   * @param created 新增数
   * @param updated 更新数
   * @param deactivated 停用数
   * @param durationMs 执行耗时
   * @param syncToken 增量令牌
   */
  public ProvisionResult(
      String connectorType,
      int totalProcessed,
      int created,
      int updated,
      int deactivated,
      long durationMs,
      String syncToken) {
    this(connectorType, totalProcessed, created, updated, deactivated, 0, durationMs, syncToken,
        new ArrayList<>(0));
  }

  /**
   * 合并两个供给结果（用于多连接器汇总）。
   *
   * @param other 另一个供给结果
   * @return 合并后的新结果
   */
  public ProvisionResult merge(ProvisionResult other) {
    List<String> mergedErrors = new ArrayList<>(this.errors);
    mergedErrors.addAll(other.errors);
    // 截断到最大保留条数
    List<String> truncatedErrors = mergedErrors.size() > MAX_ERRORS
        ? mergedErrors.subList(0, MAX_ERRORS)
        : mergedErrors;
    return new ProvisionResult(
        this.connectorType + "+" + other.connectorType,
        this.totalProcessed + other.totalProcessed,
        this.created + other.created,
        this.updated + other.updated,
        this.deactivated + other.deactivated,
        this.failed + other.failed,
        this.durationMs + other.durationMs,
        null,
        truncatedErrors);
  }

  /**
   * 判断本次同步是否有错误。
   *
   * @return true 表示存在失败或错误详情
   */
  public boolean hasErrors() {
    return failed > 0 || !errors.isEmpty();
  }
}
