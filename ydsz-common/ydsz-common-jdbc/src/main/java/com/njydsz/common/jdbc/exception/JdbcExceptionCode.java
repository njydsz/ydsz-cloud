package com.njydsz.common.jdbc.exception;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * JDBC 模块异常码
 *
 * <p>覆盖数据库访问层的各类异常场景，遵循统一异常码规范：
 *
 * <ul>
 *   <li>D01xxx — 数据源异常（连接池、路由、健康检查）
 *   <li>D02xxx — SQL 执行异常（解析失败、权限拦截、防火墙拦截）
 *   <li>D03xxx — 读写分离异常（从库不可用、延迟超标）
 *   <li>D04xxx — 熔断与限流异常
 * </ul>
 *
 * <p>每个异常码包含：
 *
 * <ul>
 *   <li>code — 唯一错误码（字符串形式，便于日志检索）
 *   <li>key — 国际化消息键（对应 messages_*.properties）
 *   <li>httpStatus — HTTP 状态码（语义化返回给前端）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see JdbcException
 * @see com.njydsz.common.exception.code.CoreExceptionCode
 */
@Getter
@YdszExceptionCode(module = "jdbc", description = "JDBC 模块数据访问异常码")
public enum JdbcExceptionCode implements ExceptionCode {

  // ==================== D01 数据源异常 ====================

  /**
   * 数据源不可用
   *
   * <p>连接池耗尽、数据源未注册或数据源健康检查失败时抛出。
   */
  DATASOURCE_UNAVAILABLE("D01001", "jdbc.datasource.unavailable", 503),

  /**
   * 数据源路由失败
   *
   * <p>动态路由数据源无法解析目标数据源时抛出。
   */
  DATASOURCE_ROUTE_FAILED("D01002", "jdbc.datasource.route.failed", 500),

  /**
   * 连接池耗尽
   *
   * <p>HikariCP 连接池达到最大连接数且等待超时时抛出。
   */
  CONNECTION_POOL_EXHAUSTED("D01003", "jdbc.connection.pool.exhausted", 503),

  // ==================== D02 SQL 执行异常 ====================

  /**
   * SQL 解析失败
   *
   * <p>JSqlParser 无法解析 SQL 语法时抛出（通常由 SQL 注入或语法错误导致）。
   */
  SQL_PARSE_FAILED("D02001", "jdbc.sql.parse.failed", 400),

  /**
   * SQL 防火墙拦截
   *
   * <p>SQL 防火墙检测到危险操作（如全表 UPDATE/DELETE 无 WHERE 条件）时拦截。
   */
  SQL_FIREWALL_BLOCKED("D02002", "jdbc.sql.firewall.blocked", 403),

  /**
   * 数据权限拦截
   *
   * <p>数据权限上下文缺失或权限配置错误时抛出。
   */
  DATA_PERMISSION_DENIED("D02003", "jdbc.data.permission.denied", 403),

  /**
   * 深度分页被拒绝
   *
   * <p>查询偏移量超过安全阈值时拦截，防止深度分页导致数据库性能劣化。
   */
  DEEP_PAGINATION_BLOCKED("D02004", "jdbc.deep.pagination.blocked", 400),

  // ==================== D03 读写分离异常 ====================

  /**
   * 从库不可用
   *
   * <p>所有从库均因延迟超标被摘除时抛出，读写分离自动降级走主库。
   */
  SLAVE_UNAVAILABLE("D03001", "jdbc.slave.unavailable", 503),

  /**
   * 从库延迟超标
   *
   * <p>单个从库复制延迟超过阈值，被临时摘除出路由池。
   */
  SLAVE_LATENCY_EXCEEDED("D03002", "jdbc.slave.latency.exceeded", 503),

  // ==================== D04 熔断与限流异常 ====================

  /**
   * 数据库熔断器打开
   *
   * <p>数据库熔断器处于 OPEN 状态，请求被拒绝。 触发条件：连续失败次数达到阈值。
   */
  CIRCUIT_BREAKER_OPEN("D04001", "jdbc.circuit.breaker.open", 503),

  /**
   * 数据库熔断器半开探测失败
   *
   * <p>熔断器处于 HALF_OPEN 状态但探测请求仍然失败。
   */
  CIRCUIT_BREAKER_HALF_OPEN_FAILED("D04002", "jdbc.circuit.breaker.half.open.failed", 503);

  // ==================== 字段定义 ====================

  /** 异常错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  JdbcExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }

  @Override
  public int getHttpStatus() {
    return httpStatus;
  }
}
