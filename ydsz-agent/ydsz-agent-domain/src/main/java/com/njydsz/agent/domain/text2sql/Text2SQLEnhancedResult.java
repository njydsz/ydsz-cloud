package com.njydsz.agent.domain.text2sql;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * NL2SQL 增强链路执行结果。
 *
 * <p>在标准 {@code Text2SQLResult} 五元组基础上增加增强链路的诊断信息：
 * 召回的表名列表、可行性评估说明、语义一致性分数。
 *
 * <p>增强字段均为包装类型，允许为 null（对应步骤被跳过或降级时使用）。
 *
 * @param columns 列名列表
 * @param rows 数据行
 * @param rowCount 总行数
 * @param generatedSql 实际执行的 SQL（供审计）
 * @param executionTimeMs 执行耗时（毫秒）
 * @param recalledTables 本次查询召回的相关表名列表（null 表示未启用召回或召回失败）
 * @param feasibilityReason 可行性评估原因说明（null 表示未启用或不可评估）
 * @param consistencyScore 语义一致性分数（null 表示未启用一致性校验）
 * @author ydsz-team
 * @since 26.09.01
 */
public record Text2SQLEnhancedResult(
    List<String> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    String generatedSql,
    long executionTimeMs,
    List<String> recalledTables,
    String feasibilityReason,
    Double consistencyScore)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 创建空结果（增强版）。
   *
   * @param sql 实际执行的 SQL（供审计）
   * @return 空结果实例
   */
  public static Text2SQLEnhancedResult empty(String sql) {
    return new Text2SQLEnhancedResult(List.of(), List.of(), 0, sql, 0, null, null, null);
  }

  /**
   * 将增强结果降级为标准结果。
   *
   * @return 仅含基础五元组的标准结果
   */
  public com.njydsz.agent.domain.gateway.Text2SQLService.Text2SQLResult toBaseResult() {
    return new com.njydsz.agent.domain.gateway.Text2SQLService.Text2SQLResult(
        columns, rows, rowCount, generatedSql, executionTimeMs);
  }
}
