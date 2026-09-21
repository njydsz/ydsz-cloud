package com.njydsz.common.jdbc.config;

import java.util.Arrays;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JDBC 模块统一配置属性类
 *
 * <p>提供 ydsz.jdbc 前缀的全局配置，包括模块开关、Mapper 扫描包等基础配置。
 *
 * <p>详细功能配置已拆分为独立配置类：
 *
 * <ul>
 *   <li>{@link SlowSqlProperties} — 慢 SQL 监控（{@code ydsz.jdbc.slow-sql.*}）
 *   <li>{@link SqlAuditProperties} — SQL 审计（{@code ydsz.jdbc.sql-audit.*}）
 *   <li>{@link SafeQueryProperties} — 安全查询（{@code ydsz.jdbc.safe-query.*}）
 * </ul>
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     enabled: true
 *     id-type: ASSIGN_ID          # 全局默认主键策略（与MP IdType对应）
 *     mapper-scan-packages: com.njydsz.**.mapper
 *     slow-sql:
 *       enabled: true
 *       threshold-millis: 1000
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.jdbc")
public class JdbcProperties {

  /** 是否启用 JDBC 模块（默认 true） */
  private boolean isEnabled = true;

  /**
   * 全局默认主键策略
   *
   * <p>未在实体 {@code @TableId(type=...)} 上显式声明的字段将使用此默认值，
   * 统一跨模块 ID 生成策略。支持的值：AUTO、NONE、INPUT、ASSIGN_ID、ASSIGN_UUID。
   *
   * <p>默认值：ASSIGN_ID（雪花算法）
   */
  private IdType idType = IdType.ASSIGN_ID;

  /** Mapper 扫描包路径数组（默认 com.njydsz.**.mapper） */
  private List<String> mapperScanPackages = Arrays.asList("com.njydsz.**.mapper");

  /** 慢 SQL 监控配置 */
  private SlowSqlProperties slowSql = new SlowSqlProperties();

  /** SQL 审计配置 */
  private SqlAuditProperties sqlAudit = new SqlAuditProperties();

  /** 安全查询配置（ORDER BY 注入防护 + 深度分页检测） */
  private SafeQueryProperties safeQuery = new SafeQueryProperties();

  public boolean isEnabled() { return isEnabled; }
  public void setIsEnabled(boolean isEnabled) { this.isEnabled = isEnabled; }

  public IdType getIdType() { return idType; }
  public void setIdType(IdType idType) { this.idType = idType; }

  public List<String> getMapperScanPackages() { return mapperScanPackages; }
  public void setMapperScanPackages(List<String> mapperScanPackages) { this.mapperScanPackages = mapperScanPackages; }

  public SlowSqlProperties getSlowSql() { return slowSql; }
  public void setSlowSql(SlowSqlProperties slowSql) { this.slowSql = slowSql; }

  public SqlAuditProperties getSqlAudit() { return sqlAudit; }
  public void setSqlAudit(SqlAuditProperties sqlAudit) { this.sqlAudit = sqlAudit; }

  public SafeQueryProperties getSafeQuery() { return safeQuery; }
  public void setSafeQuery(SafeQueryProperties safeQuery) { this.safeQuery = safeQuery; }
}
