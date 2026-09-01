package com.njydsz.agent.domain.gateway;

import java.util.List;
import java.util.Map;

/**
 * Text2SQL 服务接口（领域网关）
 *
 * <p>将自然语言查询转换为 SQL 并执行，返回结构化结果。 实现位于 infra 层，通过 LLM 生成 SQL + 安全护栏 + 数据库执行三层编排。
 *
 * <p>安全约束：
 *
 * <ul>
 *   <li>仅允许 SELECT 语句（禁止 INSERT / UPDATE / DELETE / DROP / ALTER / TRUNCATE）
 *   <li>SQL 注入检测（拒绝含注释、多语句、存储过程调用等可疑模式）
 *   <li>结果行数限制（默认 100 行，防止全表扫描导致 OOM）
 *   <li>执行超时（默认 10s，防止慢查询挂起连接）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface Text2SQLService {

  /**
   * 将自然语言查询转换为 SQL 并执行。
   *
   * @param naturalLanguageQuery 自然语言查询（如"查询最近 7 天创建的项目数量"）
   * @param tenantId 租户 ID（用于数据隔离）
   * @return 查询结果（列名 + 数据行）
   * @throws Text2SQLException SQL 生成或执行失败
   */
  Text2SQLResult query(String naturalLanguageQuery, String tenantId) throws Text2SQLException;

  /**
   * Text2SQL 查询结果值对象。
   *
   * @param columns 列名列表
   * @param rows 数据行（每行为 columnName -> value 的 Map）
   * @param rowCount 总行数
   * @param generatedSql 实际执行的 SQL（供审计）
   * @param executionTimeMs 执行耗时（毫秒）
   */
  record Text2SQLResult(
      List<String> columns,
      List<Map<String, Object>> rows,
      int rowCount,
      String generatedSql,
      long executionTimeMs) {

    /** 创建空结果 */
    public static Text2SQLResult empty(String sql) {
      return new Text2SQLResult(List.of(), List.of(), 0, sql, 0);
    }
  }

  /**
   * Text2SQL 异常。
   *
   * @param message 错误描述
   * @param errorCode 错误码
   */
  class Text2SQLException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final String errorCode;

    /**
     * 构造异常。
     *
     * @param message 错误描述
     * @param errorCode 错误码
     */
    public Text2SQLException(String message, String errorCode) {
      super(message);
      this.errorCode = errorCode;
    }

    /**
     * 构造异常（携带根因）。
     *
     * @param message 错误描述
     * @param errorCode 错误码
     * @param cause 根因异常
     */
    public Text2SQLException(String message, String errorCode, Throwable cause) {
      super(message, cause);
      this.errorCode = errorCode;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
      return errorCode;
    }
  }
}
