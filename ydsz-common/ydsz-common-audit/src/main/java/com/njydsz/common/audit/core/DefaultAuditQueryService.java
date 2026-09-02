package com.njydsz.common.audit.core;.core
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.audit.storage.TableNameResolver;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;

/**
 * 基于 JDBC 的默认审计查询服务实现
 *
 * <p>提供对 {@code sys_audit_log} 表的常用查询能力， 包括按 ID、业务流水号、操作人、模块、审计类型、时间范围等维度查询。
 *
 * <p><b>分表支持：</b>当启用分表时，查询会自动根据时间范围路由到对应分表， 跨分表查询使用 UNION ALL 合并结果。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DefaultAuditQueryService implements AuditQueryService {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultAuditQueryService.class);

  /** 默认基础表名 */
  private static final String DEFAULT_BASE_TABLE_NAME = "sys_audit_log";

  /** 表名白名单正则：仅允许字母、数字、下划线 */
  private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

  /** 默认查询时间范围（月） */
  private static final int DEFAULT_QUERY_RANGE_MONTHS = 12;

  /** JDBC 模板，用于执行数据库查询 */
  private final JdbcTemplate jdbcTemplate;

  /** 表名解析器（封装分表逻辑） */
  private final TableNameResolver tableNameResolver;

  /** BeanPropertyRowMapper 缓存 */
  private final BeanPropertyRowMapper<AuditLog> rowMapper =
      BeanPropertyRowMapper.newInstance(AuditLog.class);

  /**
   * 构造默认审计查询服务（无分表）
   *
   * @param dataSource 数据源
   */
  public DefaultAuditQueryService(DataSource dataSource) {
    this(dataSource, null, DEFAULT_BASE_TABLE_NAME);
  }

  /**
   * 构造默认审计查询服务（支持分表）
   *
   * @param dataSource 数据源
   * @param shardingType 分表类型（monthly/daily/yearly），为 null 表示不分表
   * @param baseTableName 基础表名
   */
  public DefaultAuditQueryService(
      DataSource dataSource, String shardingType, String baseTableName) {
    this.jdbcTemplate =
        new JdbcTemplate(Objects.requireNonNull(dataSource, "DataSource must not be null"));
    String resolvedTableName = baseTableName != null ? baseTableName : DEFAULT_BASE_TABLE_NAME;
    validateTableName(resolvedTableName);
    this.tableNameResolver = new TableNameResolver(shardingType, resolvedTableName);
  }

  /**
   * 校验表名合法性，防止 SQL 注入
   *
   * @param tableName 表名
   * @throws IllegalArgumentException 如果表名不合法
   */
  private void validateTableName(String tableName) {
    if (tableName == null || tableName.isEmpty()) {
      throw new IllegalArgumentException("表名不能为空");
    }
    if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
      throw new IllegalArgumentException("非法的表名: " + tableName + "，仅允许字母、数字和下划线");
    }
  }

  @Override
  public AuditLog getById(String id) {
    if (id == null || id.isEmpty()) {
      return null;
    }
    try {
      if (tableNameResolver.isShardingEnabled()) {
        // 跨分表查询：查询最近 N 个月的分表
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMonths(DEFAULT_QUERY_RANGE_MONTHS);
        Set<String> tables = tableNameResolver.resolveInRange(start, end);
        for (String table : tables) {
          AuditLog result = queryFromTable(table, "id = ? LIMIT 1", id);
          if (result != null) {
            return result;
          }
        }
        return null;
      }
      // tableName validated in constructor via validateTableName() — safe from SQL injection
      String sql = buildSelectSql(tableNameResolver.resolve(null), "id = ? LIMIT 1", null);
      return jdbcTemplate.queryForObject(sql, rowMapper, id);
    } catch (Exception e) {
      LOG.warn("查询审计日志失败, id={}", id, e);
      return null;
    }
  }

  @Override
  public List<AuditLog> getByBusinessNo(String businessNo) {
    if (businessNo == null || businessNo.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      SqlContext ctx = new SqlContext();
      ctx.addCondition("business_no = ?", businessNo);
      return executeListQuery(ctx);
    } catch (Exception e) {
      LOG.warn("按业务流水号查询审计日志失败, businessNo={}", businessNo, e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<AuditLog> getByOperator(
      String operatorId, LocalDateTime startTime, LocalDateTime endTime) {
    if (operatorId == null || operatorId.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      SqlContext ctx = new SqlContext();
      ctx.addCondition("operator_id = ?", operatorId);
      ctx.setTimeRange(startTime, endTime);
      return executeListQuery(ctx);
    } catch (Exception e) {
      LOG.warn("按操作人查询审计日志失败, operatorId={}", operatorId, e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<AuditLog> getByModule(String module, LocalDateTime startTime, LocalDateTime endTime) {
    if (module == null || module.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      SqlContext ctx = new SqlContext();
      ctx.addCondition("module = ?", module);
      ctx.setTimeRange(startTime, endTime);
      return executeListQuery(ctx);
    } catch (Exception e) {
      LOG.warn("按模块查询审计日志失败, module={}", module, e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<AuditLog> getByAuditType(
      Integer auditType, LocalDateTime startTime, LocalDateTime endTime) {
    if (auditType == null) {
      return Collections.emptyList();
    }
    try {
      SqlContext ctx = new SqlContext();
      ctx.addCondition("audit_type = ?", auditType);
      ctx.setTimeRange(startTime, endTime);
      return executeListQuery(ctx);
    } catch (Exception e) {
      LOG.warn("按审计类型查询审计日志失败, auditType={}", auditType, e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<AuditLog> getByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
    try {
      SqlContext ctx = new SqlContext();
      ctx.setTimeRange(startTime, endTime);
      return executeListQuery(ctx);
    } catch (Exception e) {
      LOG.warn("按时间范围查询审计日志失败", e);
      return Collections.emptyList();
    }
  }

  // ====================== Paginated query methods ======================

  @Override
  public YdszResponse<List<AuditLog>> queryByTimeRange(
      LocalDateTime start, LocalDateTime end, int page, int size) {
    try {
      SqlContext countCtx = new SqlContext();
      countCtx.setTimeRange(start, end);
      int offset = validatePagination(page, size);
      long total = executeCountQuery(countCtx);
      if (total == 0) {
        return emptyPageResult(page, size);
      }

      SqlContext ctx = new SqlContext();
      ctx.setTimeRange(start, end);
      ctx.setPagination(size, offset);
      List<AuditLog> records = executeListQueryWithLimit(ctx);
      return PageResponse.success(total, (long) page, (long) size, records);
    } catch (Exception e) {
      LOG.warn("按时间范围分页查询审计日志失败", e);
      return emptyPageResult(page, size);
    }
  }

  @Override
  public YdszResponse<List<AuditLog>> queryByOperator(String operatorId, int page, int size) {
    if (operatorId == null || operatorId.isEmpty()) {
      return emptyPageResult(page, size);
    }
    try {
      int offset = validatePagination(page, size);
      long total = countByOperator(operatorId);
      if (total == 0) {
        return emptyPageResult(page, size);
      }
      SqlContext ctx = new SqlContext();
      ctx.addCondition("operator_id = ?", operatorId);
      ctx.setPagination(size, offset);
      List<AuditLog> records = executeListQueryWithLimit(ctx);
      return PageResponse.success(total, (long) page, (long) size, records);
    } catch (Exception e) {
      LOG.warn("按操作人分页查询审计日志失败, operatorId={}", operatorId, e);
      return emptyPageResult(page, size);
    }
  }

  @Override
  public YdszResponse<List<AuditLog>> queryByAction(Integer action, int page, int size) {
    if (action == null) {
      return emptyPageResult(page, size);
    }
    try {
      int offset = validatePagination(page, size);
      long total = countByAction(action);
      if (total == 0) {
        return emptyPageResult(page, size);
      }
      SqlContext ctx = new SqlContext();
      ctx.addCondition("action = ?", action);
      ctx.setPagination(size, offset);
      List<AuditLog> records = executeListQueryWithLimit(ctx);
      return PageResponse.success(total, (long) page, (long) size, records);
    } catch (Exception e) {
      LOG.warn("按操作类型分页查询审计日志失败, action={}", action, e);
      return emptyPageResult(page, size);
    }
  }

  @Override
  public YdszResponse<List<AuditLog>> queryByEntityType(String entityType, int page, int size) {
    if (entityType == null || entityType.isEmpty()) {
      return emptyPageResult(page, size);
    }
    try {
      int offset = validatePagination(page, size);
      long total = countByEntityType(entityType);
      if (total == 0) {
        return emptyPageResult(page, size);
      }
      SqlContext ctx = new SqlContext();
      ctx.addCondition("module = ?", entityType);
      ctx.setPagination(size, offset);
      List<AuditLog> records = executeListQueryWithLimit(ctx);
      return PageResponse.success(total, (long) page, (long) size, records);
    } catch (Exception e) {
      LOG.warn("按实体类型分页查询审计日志失败, entityType={}", entityType, e);
      return emptyPageResult(page, size);
    }
  }

  /**
   * 按链路追踪 ID 查询审计日志
   *
   * <p>使用 {@code trace_id} 列的等值查询（非 LIKE）， 可利用数据库索引，避免全表扫描导致的性能问题。
   *
   * @param traceId 链路追踪 ID
   * @return 审计日志列表，按 operation_time 倒序
   */
  @Override
  public List<AuditLog> queryByTraceId(String traceId) {
    if (traceId == null || traceId.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      SqlContext ctx = new SqlContext();
      ctx.addCondition("trace_id = ?", traceId);
      return executeListQuery(ctx);
    } catch (Exception e) {
      LOG.warn("按追踪ID查询审计日志失败, traceId={}", traceId, e);
      return Collections.emptyList();
    }
  }

  @Override
  public long countByConditions(
      String operatorId,
      Integer action,
      String module,
      Integer auditType,
      LocalDateTime startTime,
      LocalDateTime endTime) {
    try {
      SqlContext ctx = new SqlContext();
      if (operatorId != null && !operatorId.isEmpty()) {
        ctx.addCondition("operator_id = ?", operatorId);
      }
      if (action != null) {
        ctx.addCondition("action = ?", action);
      }
      if (module != null && !module.isEmpty()) {
        ctx.addCondition("module = ?", module);
      }
      if (auditType != null) {
        ctx.addCondition("audit_type = ?", auditType);
      }
      ctx.setTimeRange(startTime, endTime);
      return executeCountQuery(ctx);
    } catch (Exception e) {
      LOG.warn("按条件统计审计日志数量失败", e);
      return 0L;
    }
  }

  // ====================== Private helper methods ======================

  /** 解析时间范围涉及的分表 */
  private Set<String> resolveTables(LocalDateTime startTime, LocalDateTime endTime) {
    if (!tableNameResolver.isShardingEnabled()) {
      return Collections.singleton(tableNameResolver.resolve(null));
    }
    if (startTime == null) {
      startTime = LocalDateTime.now().minusMonths(DEFAULT_QUERY_RANGE_MONTHS);
    }
    if (endTime == null) {
      endTime = LocalDateTime.now();
    }
    return tableNameResolver.resolveInRange(startTime, endTime);
  }

  /** 从单个表查询单条记录 */
  private AuditLog queryFromTable(String tableName, String whereClause, Object... params) {
    validateTableName(tableName);
    try {
      // tableName validated by validateTableName() above; whereClause built from hardcoded
      // fragments — safe from SQL injection
      String sql = "SELECT * FROM " + tableName + " WHERE " + whereClause;
      return jdbcTemplate.queryForObject(sql, rowMapper, params);
    } catch (Exception e) {
      return null;
    }
  }

  /** 构建 SELECT SQL（不含分页） */
  private String buildSelectSql(String tableName, String condition, List<Object> params) {
    String sql = "SELECT * FROM " + tableName;
    if (condition != null && !condition.isEmpty()) {
      sql += " WHERE " + condition;
    }
    return sql;
  }

  /** 构建 SELECT SQL（带分页） */
  private String buildSelectSqlWithLimit(
      String tableName, String condition, int limit, int offset) {
    String sql = "SELECT * FROM " + tableName;
    if (condition != null && !condition.isEmpty()) {
      sql += " WHERE " + condition;
    }
    sql += " ORDER BY operation_time DESC LIMIT " + limit + " OFFSET " + offset;
    return sql;
  }

  /** 执行列表查询（公共方法，处理分表和非分表逻辑） */
  private List<AuditLog> executeListQuery(SqlContext ctx) {
    if (tableNameResolver.isShardingEnabled()) {
      Set<String> tables = resolveTables(ctx.getStartTime(), ctx.getEndTime());
      if (tables.isEmpty()) {
        return Collections.emptyList();
      }
      String whereClause = buildWhereClause(ctx);
      if (tables.size() == 1) {
        String table = tables.iterator().next();
        validateTableName(table);
        String sql =
            "SELECT * FROM " + table + " WHERE " + whereClause + " ORDER BY operation_time DESC";
        return jdbcTemplate.query(sql, rowMapper, ctx.getParamsArray());
      }
      String unionSql = buildUnionAllSql(tables, whereClause + " ORDER BY operation_time DESC");
      return jdbcTemplate.query(unionSql, rowMapper, ctx.getParamsArray());
    }
    // 非分表模式
    String sql =
        buildSelectSql(tableNameResolver.resolve(null), null, null)
            + " WHERE "
            + buildWhereClause(ctx)
            + " ORDER BY operation_time DESC";
    return jdbcTemplate.query(sql, rowMapper, ctx.getParamsArray());
  }

  /** 执行带分页的列表查询 */
  private List<AuditLog> executeListQueryWithLimit(SqlContext ctx) {
    if (tableNameResolver.isShardingEnabled()) {
      Set<String> tables = resolveTables(ctx.getStartTime(), ctx.getEndTime());
      if (tables.size() == 1) {
        String table = tables.iterator().next();
        validateTableName(table);
        String sql =
            buildSelectSqlWithLimit(table, buildWhereClause(ctx), ctx.getLimit(), ctx.getOffset());
        return jdbcTemplate.query(sql, rowMapper, ctx.getParamsArray());
      }
      // 多分表：使用子查询合并后分页
      String whereClause = buildWhereClause(ctx);
      String unionSql =
          buildUnionAllWithLimit(tables, whereClause, ctx.getLimit(), ctx.getOffset());
      return jdbcTemplate.query(unionSql, rowMapper, ctx.getParamsArray());
    }
    // 非分表模式
    String sql =
        buildSelectSqlWithLimit(
            tableNameResolver.resolve(null),
            buildWhereClause(ctx),
            ctx.getLimit(),
            ctx.getOffset());
    return jdbcTemplate.query(sql, rowMapper, ctx.getParamsArray());
  }

  /** 执行计数查询 */
  private long executeCountQuery(SqlContext ctx) {
    if (tableNameResolver.isShardingEnabled()) {
      Set<String> tables = resolveTables(ctx.getStartTime(), ctx.getEndTime());
      long total = 0;
      for (String table : tables) {
        validateTableName(table);
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + buildWhereClause(ctx);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, ctx.getParamsArray());
        total += count != null ? count : 0L;
      }
      return total;
    }
    // 非分表模式
    String sql =
        "SELECT COUNT(*) FROM "
            + tableNameResolver.resolve(null)
            + " WHERE "
            + buildWhereClause(ctx);
    Long count = jdbcTemplate.queryForObject(sql, Long.class, ctx.getParamsArray());
    return count != null ? count : 0L;
  }

  /** 构建 WHERE 子句 */
  private String buildWhereClause(SqlContext ctx) {
    StringBuilder sb = new StringBuilder("1=1");
    for (String condition : ctx.getConditions()) {
      sb.append(" AND ").append(condition);
    }
    if (ctx.getStartTime() != null) {
      sb.append(" AND operation_time >= ?");
    }
    if (ctx.getEndTime() != null) {
      sb.append(" AND operation_time <= ?");
    }
    return sb.toString();
  }

  /** 构建 UNION ALL 查询 SQL */
  private String buildUnionAllSql(Set<String> tables, String whereClause) {
    if (tables.size() == 1) {
      String table = tables.iterator().next();
      validateTableName(table);
      // whereClause built from hardcoded fragments (column = ?) — safe from SQL injection
      return "SELECT * FROM " + table + " WHERE " + whereClause;
    }
    StringBuilder sql = new StringBuilder();
    int idx = 0;
    for (String table : tables) {
      validateTableName(table);
      if (idx > 0) {
        sql.append(" UNION ALL ");
      }
      // whereClause built from hardcoded fragments (column = ?) — safe from SQL injection
      sql.append("SELECT * FROM ").append(table).append(" WHERE ").append(whereClause);
      idx++;
    }
    return sql.toString();
  }

  /** 构建 UNION ALL 带分页的查询 SQL */
  private String buildUnionAllWithLimit(
      Set<String> tables, String whereClause, int limit, int offset) {
    if (tables.size() == 1) {
      String table = tables.iterator().next();
      validateTableName(table);
      // whereClause built from hardcoded fragments (column = ?) — safe from SQL injection
      return "SELECT * FROM "
          + table
          + " WHERE "
          + whereClause
          + " ORDER BY operation_time DESC LIMIT "
          + limit
          + " OFFSET "
          + offset;
    }
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT * FROM (");
    int idx = 0;
    for (String table : tables) {
      validateTableName(table);
      if (idx > 0) {
        sql.append(" UNION ALL ");
      }
      // whereClause built from hardcoded fragments (column = ?) — safe from SQL injection
      sql.append("SELECT * FROM ").append(table).append(" WHERE ").append(whereClause);
      idx++;
    }
    sql.append(") AS combined ORDER BY operation_time DESC LIMIT ")
        .append(limit)
        .append(" OFFSET ")
        .append(offset);
    return sql.toString();
  }

  /**
   * 校验并规范化分页参数，计算偏移量
   *
   * @param page 页码（从1开始）
   * @param size 每页大小
   * @return 计算后的偏移量
   */
  private int validatePagination(int page, int size) {
    if (page < 1) {
      page = 1;
    }
    if (size < 1) {
      size = 20;
    }
    return (page - 1) * size;
  }

  /**
   * 构建空分页结果
   *
   * @param page 页码
   * @param size 每页大小
   * @return 无记录的空分页结果
   */
  private YdszResponse<List<AuditLog>> emptyPageResult(int page, int size) {
    return PageResponse.empty((long) page, (long) size);
  }

  /** 按时间范围统计审计日志数量 */
  private long countByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
    SqlContext ctx = new SqlContext();
    ctx.setTimeRange(startTime, endTime);
    return executeCountQuery(ctx);
  }

  /** 按操作人统计审计日志数量 */
  private long countByOperator(String operatorId) {
    String sql =
        "SELECT COUNT(*) FROM " + tableNameResolver.resolve(null) + " WHERE operator_id = ?";
    Long count = jdbcTemplate.queryForObject(sql, Long.class, operatorId);
    return count != null ? count : 0L;
  }

  /** 按操作类型统计审计日志数量 */
  private long countByAction(Integer action) {
    String sql = "SELECT COUNT(*) FROM " + tableNameResolver.resolve(null) + " WHERE action = ?";
    Long count = jdbcTemplate.queryForObject(sql, Long.class, action);
    return count != null ? count : 0L;
  }

  /** 按实体类型统计审计日志数量 */
  private long countByEntityType(String entityType) {
    String sql = "SELECT COUNT(*) FROM " + tableNameResolver.resolve(null) + " WHERE module = ?";
    Long count = jdbcTemplate.queryForObject(sql, Long.class, entityType);
    return count != null ? count : 0L;
  }

  /** SQL 查询上下文，封装条件、参数、时间范围和分页信息 */
  private static class SqlContext {

    private final List<String> conditions = new ArrayList<>(4);