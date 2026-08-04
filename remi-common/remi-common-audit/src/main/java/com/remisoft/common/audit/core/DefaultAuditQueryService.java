package com.remisoft.common.audit.core;

import java.time.LocalDateTime;
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

import com.remisoft.common.audit.domain.AuditLog;
import com.remisoft.common.audit.sharding.TableShardingStrategy;
import com.remisoft.common.core.response.PageResponse;

/**
 * 基于 JDBC 的默认审计查询服务实现
 *
 * <p>提供对 {@code sys_audit_log} 表的常用查询能力，
 * 包括按 ID、业务流水号、操作人、模块、审计类型、时间范围等维度查询。
 *
 * <p><b>分表支持：</b>当启用分表策略时，查询会自动根据时间范围路由到对应分表，
 * 跨分表查询使用 UNION ALL 合并结果。
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
public class DefaultAuditQueryService implements AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuditQueryService.class);

    /** 默认基础表名 */
    private static final String DEFAULT_BASE_TABLE_NAME = "sys_audit_log";

    /** 表名白名单正则：仅允许字母、数字、下划线 */
    private static final Pattern TABLE_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** JDBC 模板，用于执行数据库查询 */
    private final JdbcTemplate jdbcTemplate;
    /** 分表策略（可为 null） */
    private final TableShardingStrategy shardingStrategy;
    /** 基础表名 */
    private final String baseTableName;

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
     * @param dataSource       数据源
     * @param shardingStrategy 分表策略（可为 null）
     * @param baseTableName    基础表名
     */
    public DefaultAuditQueryService(DataSource dataSource, TableShardingStrategy shardingStrategy, String baseTableName) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "DataSource must not be null"));
        this.shardingStrategy = shardingStrategy;
        String resolvedTableName = baseTableName != null ? baseTableName : DEFAULT_BASE_TABLE_NAME;
        validateTableName(resolvedTableName);
        this.baseTableName = resolvedTableName;
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
            if (shardingStrategy != null) {
                // 跨分表查询：查询最近12个月的分表
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start = end.minusMonths(12);
                Set<String> tables = shardingStrategy.getTableNamesInRange(baseTableName, start, end);
                for (String table : tables) {
                    AuditLog result = queryFromTable(table, "id = ? LIMIT 1", id);
                    if (result != null) {
                        return result;
                    }
                }
                return null;
            }
            String sql = "SELECT * FROM " + baseTableName + " WHERE id = ? LIMIT 1";
            return jdbcTemplate.queryForObject(sql, BeanPropertyRowMapper.newInstance(AuditLog.class), id);
        } catch (Exception e) {
            log.warn("查询审计日志失败, id={}", id, e);
            return null;
        }
    }

    @Override
    public List<AuditLog> getByBusinessNo(String businessNo) {
        if (businessNo == null || businessNo.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            if (shardingStrategy != null) {
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start = end.minusMonths(12);
                Set<String> tables = shardingStrategy.getTableNamesInRange(baseTableName, start, end);
                String unionSql = buildUnionAllSql(tables, "business_no = ? ORDER BY operation_time DESC");
                return jdbcTemplate.query(unionSql, BeanPropertyRowMapper.newInstance(AuditLog.class), businessNo);
            }
            String sql = "SELECT * FROM " + baseTableName + " WHERE business_no = ? ORDER BY operation_time DESC";
            return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(AuditLog.class), businessNo);
        } catch (Exception e) {
            log.warn("按业务流水号查询审计日志失败, businessNo={}", businessNo, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<AuditLog> getByOperator(String operatorId, LocalDateTime startTime, LocalDateTime endTime) {
        if (operatorId == null || operatorId.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            if (shardingStrategy != null) {
                Set<String> tables = resolveTables(startTime, endTime);
                List<Object> params = new ArrayList<>();
                params.add(operatorId);
                String whereClause = "operator_id = ?" + appendTimeCondition(params, startTime, endTime)
                        + " ORDER BY operation_time DESC";
                String unionSql = buildUnionAllSql(tables, whereClause);
                return jdbcTemplate.query(unionSql,
                        BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
            }
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(baseTableName)
                    .append(" WHERE operator_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(operatorId);

            if (startTime != null) {
                sql.append(" AND operation_time >= ?");
                params.add(startTime);
            }
            if (endTime != null) {
                sql.append(" AND operation_time <= ?");
                params.add(endTime);
            }
            sql.append(" ORDER BY operation_time DESC");

            return jdbcTemplate.query(sql.toString(),
                    BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
        } catch (Exception e) {
            log.warn("按操作人查询审计日志失败, operatorId={}", operatorId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<AuditLog> getByModule(String module, LocalDateTime startTime, LocalDateTime endTime) {
        if (module == null || module.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            if (shardingStrategy != null) {
                Set<String> tables = resolveTables(startTime, endTime);
                List<Object> params = new ArrayList<>();
                params.add(module);
                String whereClause = "module = ?" + appendTimeCondition(params, startTime, endTime)
                        + " ORDER BY operation_time DESC";
                String unionSql = buildUnionAllSql(tables, whereClause);
                return jdbcTemplate.query(unionSql,
                        BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
            }
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(baseTableName)
                    .append(" WHERE module = ?");
            List<Object> params = new ArrayList<>();
            params.add(module);

            if (startTime != null) {
                sql.append(" AND operation_time >= ?");
                params.add(startTime);
            }
            if (endTime != null) {
                sql.append(" AND operation_time <= ?");
                params.add(endTime);
            }
            sql.append(" ORDER BY operation_time DESC");

            return jdbcTemplate.query(sql.toString(),
                    BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
        } catch (Exception e) {
            log.warn("按模块查询审计日志失败, module={}", module, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<AuditLog> getByAuditType(Integer auditType, LocalDateTime startTime, LocalDateTime endTime) {
        if (auditType == null) {
            return Collections.emptyList();
        }
        try {
            if (shardingStrategy != null) {
                Set<String> tables = resolveTables(startTime, endTime);
                List<Object> params = new ArrayList<>();
                params.add(auditType);
                String whereClause = "audit_type = ?" + appendTimeCondition(params, startTime, endTime)
                        + " ORDER BY operation_time DESC";
                String unionSql = buildUnionAllSql(tables, whereClause);
                return jdbcTemplate.query(unionSql,
                        BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
            }
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(baseTableName)
                    .append(" WHERE audit_type = ?");
            List<Object> params = new ArrayList<>();
            params.add(auditType);

            if (startTime != null) {
                sql.append(" AND operation_time >= ?");
                params.add(startTime);
            }
            if (endTime != null) {
                sql.append(" AND operation_time <= ?");
                params.add(endTime);
            }
            sql.append(" ORDER BY operation_time DESC");

            return jdbcTemplate.query(sql.toString(),
                    BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
        } catch (Exception e) {
            log.warn("按审计类型查询审计日志失败, auditType={}", auditType, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<AuditLog> getByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            if (shardingStrategy != null) {
                Set<String> tables = resolveTables(startTime, endTime);
                if (tables.isEmpty()) {
                    return Collections.emptyList();
                }
                if (tables.size() == 1) {
                    String table = tables.iterator().next();
                    List<Object> params = new ArrayList<>();
                    String whereClause = "1=1" + appendTimeCondition(params, startTime, endTime)
                            + " ORDER BY operation_time DESC";
                    return jdbcTemplate.query("SELECT * FROM " + table + " WHERE " + whereClause,
                            BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
                }
                List<Object> params = new ArrayList<>();
                String whereClause = "1=1" + appendTimeCondition(params, startTime, endTime)
                        + " ORDER BY operation_time DESC";
                String unionSql = buildUnionAllSql(tables, whereClause);
                return jdbcTemplate.query(unionSql,
                        BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
            }
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(baseTableName)
                    .append(" WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (startTime != null) {
                sql.append(" AND operation_time >= ?");
                params.add(startTime);
            }
            if (endTime != null) {
                sql.append(" AND operation_time <= ?");
                params.add(endTime);
            }
            sql.append(" ORDER BY operation_time DESC");

            return jdbcTemplate.query(sql.toString(),
                    BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
        } catch (Exception e) {
            log.warn("按时间范围查询审计日志失败", e);
            return Collections.emptyList();
        }
    }

    // ====================== Paginated query methods ======================

    @Override
    public PageResponse<List<AuditLog>> queryByTimeRange(LocalDateTime start, LocalDateTime end, int page, int size) {
        try {
            int offset = validatePagination(page, size);
            long total = countByTimeRange(start, end);
            if (total == 0) {
                return emptyPageResult(page, size);
            }

            if (shardingStrategy != null) {
                Set<String> tables = resolveTables(start, end);
                if (tables.size() == 1) {
                    String table = tables.iterator().next();
                    List<Object> params = new ArrayList<>();
                    String sql = "SELECT * FROM " + table + " WHERE 1=1"
                            + appendTimeCondition(params, start, end)
                            + " ORDER BY operation_time DESC LIMIT ? OFFSET ?";
                    params.add(size);
                    params.add(offset);
                    List<AuditLog> records = jdbcTemplate.query(sql,
                            BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
                    return PageResponse.success(total, (long) page, (long) size, records);
                }
                // 多分表：使用子查询合并后分页
                List<Object> params = new ArrayList<>();
                String whereClause = "1=1" + appendTimeCondition(params, start, end);
                String unionSql = buildUnionAllWithLimit(tables, whereClause, size, offset);
                List<AuditLog> records = jdbcTemplate.query(unionSql,
                        BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
                return PageResponse.success(total, (long) page, (long) size, records);
            }

            List<Object> params = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(baseTableName)
                    .append(" WHERE 1=1");
            appendTimeCondition(sql, params, start, end);
            sql.append(" ORDER BY operation_time DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add(offset);

            List<AuditLog> records = jdbcTemplate.query(sql.toString(),
                    BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
            return PageResponse.success(total, (long) page, (long) size, records);
        } catch (Exception e) {
            log.warn("按时间范围分页查询审计日志失败", e);
            return emptyPageResult(page, size);
        }
    }

    @Override
    public PageResponse<List<AuditLog>> queryByOperator(String operatorId, int page, int size) {
        if (operatorId == null || operatorId.isEmpty()) {
            return emptyPageResult(page, size);
        }
        try {
            int offset = validatePagination(page, size);
            long total = countByOperator(operatorId);
            if (total == 0) {
                return emptyPageResult(page, size);
            }

            List<Object> params = new ArrayList<>();
            String sql = "SELECT * FROM " + baseTableName + " WHERE operator_id = ? ORDER BY operation_time DESC LIMIT ? OFFSET ?";
            params.add(operatorId);
            params.add(size);
            params.add(offset);

            List<AuditLog> records = jdbcTemplate.query(sql,
                    BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
            return PageResponse.success(total, (long) page, (long) size, records);
        } catch (Exception e) {
            log.warn("按操作人分页查询审计日志失败, operatorId={}", operatorId, e);
            return emptyPageResult(page, size);
        }
    }

    @Override
    public PageResponse<List<AuditLog>> queryByAction(Integer action, int page, int size) {
        if (action == null) {
            return emptyPageResult(page, size);
        }
        try {
            int offset = validatePagination(page, size);
            long total = countByAction(action);
            if (total == 0) {
                return emptyPageResult(page, size);
            }

            List<Object> params = new ArrayList<>();
            String sql = "SELECT * FROM " + baseTableName + " WHERE action = ? ORDER BY operation_time DESC LIMIT ? OFFSET ?";
            params.add(action);
            params.add(size);
            params.add(offset);

            List<AuditLog> records = jdbcTemplate.query(sql,
                    BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
            return PageResponse.success(total, (long) page, (long) size, records);
        } catch (Exception e) {
            log.warn("按操作类型分页查询审计日志失败, action={}", action, e);
            return emptyPageResult(page, size);
        }
    }

    @Override
    public PageResponse<List<AuditLog>> queryByEntityType(String entityType, int page, int size) {
        if (entityType == null || entityType.isEmpty()) {
            return emptyPageResult(page, size);
        }
        try {
            int offset = validatePagination(page, size);
            long total = countByEntityType(entityType);
            if (total == 0) {
                return emptyPageResult(page, size);
            }

            List<Object> params = new ArrayList<>();
            String sql = "SELECT * FROM " + baseTableName + " WHERE module = ? ORDER BY operation_time DESC LIMIT ? OFFSET ?";
            params.add(entityType);
            params.add(size);
            params.add(offset);

            List<AuditLog> records = jdbcTemplate.query(sql,
                    BeanPropertyRowMapper.newInstance(AuditLog.class), params.toArray());
            return PageResponse.success(total, (long) page, (long) size, records);
        } catch (Exception e) {
            log.warn("按实体类型分页查询审计日志失败, entityType={}", entityType, e);
            return emptyPageResult(page, size);
        }
    }

    @Override
    public List<AuditLog> queryByTraceId(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            if (shardingStrategy != null) {
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start = end.minusMonths(12);
                Set<String> tables = shardingStrategy.getTableNamesInRange(baseTableName, start, end);
                String unionSql = buildUnionAllSql(tables,
                        "extra_info LIKE ? ORDER BY operation_time DESC");
                return jdbcTemplate.query(unionSql,
                        BeanPropertyRowMapper.newInstance(AuditLog.class), "%" + traceId + "%");
            }
            String sql = "SELECT * FROM " + baseTableName +
                    " WHERE extra_info LIKE ? ORDER BY operation_time DESC";
            return jdbcTemplate.query(sql,
                    BeanPropertyRowMapper.newInstance(AuditLog.class), "%" + traceId + "%");
        } catch (Exception e) {
            log.warn("按追踪ID查询审计日志失败, traceId={}", traceId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public long countByConditions(String operatorId, Integer action, String module, Integer auditType,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        try {
            if (shardingStrategy != null) {
                Set<String> tables = resolveTables(startTime, endTime);
                long total = 0;
                for (String table : tables) {
                    total += countFromTable(table, operatorId, action, module, auditType, startTime, endTime);
                }
                return total;
            }
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(baseTableName)
                    .append(" WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (operatorId != null && !operatorId.isEmpty()) {
                sql.append(" AND operator_id = ?");
                params.add(operatorId);
            }
            if (action != null) {
                sql.append(" AND action = ?");
                params.add(action);
            }
            if (module != null && !module.isEmpty()) {
                sql.append(" AND module = ?");
                params.add(module);
            }
            if (auditType != null) {
                sql.append(" AND audit_type = ?");
                params.add(auditType);
            }
            if (startTime != null) {
                sql.append(" AND operation_time >= ?");
                params.add(startTime);
            }
            if (endTime != null) {
                sql.append(" AND operation_time <= ?");
                params.add(endTime);
            }

            Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("按条件统计审计日志数量失败", e);
            return 0L;
        }
    }

    // ====================== Private helper methods ======================

    /**
     * 解析时间范围涉及的分表
     */
    private Set<String> resolveTables(LocalDateTime startTime, LocalDateTime endTime) {
        if (shardingStrategy == null) {
            return Collections.singleton(baseTableName);
        }
        if (startTime == null) {
            startTime = LocalDateTime.now().minusMonths(12);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        return shardingStrategy.getTableNamesInRange(baseTableName, startTime, endTime);
    }

    /**
     * 从单个表查询单条记录
     */
    private AuditLog queryFromTable(String tableName, String whereClause, Object... params) {
        validateTableName(tableName);
        try {
            String sql = "SELECT * FROM " + tableName + " WHERE " + whereClause;
            return jdbcTemplate.queryForObject(sql, BeanPropertyRowMapper.newInstance(AuditLog.class), params);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建 UNION ALL 查询 SQL
     */
    private String buildUnionAllSql(Set<String> tables, String whereClause) {
        if (tables.size() == 1) {
            String table = tables.iterator().next();
            validateTableName(table);
            return "SELECT * FROM " + table + " WHERE " + whereClause;
        }
        StringBuilder sql = new StringBuilder();
        int idx = 0;
        for (String table : tables) {
            validateTableName(table);
            if (idx > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT * FROM ").append(table).append(" WHERE ").append(whereClause);
            idx++;
        }
        return sql.toString();
    }

    /**
     * 构建 UNION ALL 带分页的查询 SQL
     */
    private String buildUnionAllWithLimit(Set<String> tables, String whereClause, int limit, int offset) {
        if (tables.size() == 1) {
            String table = tables.iterator().next();
            validateTableName(table);
            return "SELECT * FROM " + table + " WHERE " + whereClause
                    + " ORDER BY operation_time DESC LIMIT ? OFFSET ?";
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM (");
        int idx = 0;
        for (String table : tables) {
            validateTableName(table);
            if (idx > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT * FROM ").append(table).append(" WHERE ").append(whereClause);
            idx++;
        }
        sql.append(") AS combined ORDER BY operation_time DESC LIMIT ? OFFSET ?");
        return sql.toString();
    }

    /**
     * 追加时间条件到 where 子句
     */
    private String appendTimeCondition(List<Object> params, LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder sb = new StringBuilder();
        if (startTime != null) {
            sb.append(" AND operation_time >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sb.append(" AND operation_time <= ?");
            params.add(endTime);
        }
        return sb.toString();
    }

    /**
     * 追加时间条件到 StringBuilder SQL
     */
    private void appendTimeCondition(StringBuilder sql, List<Object> params,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null) {
            sql.append(" AND operation_time >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND operation_time <= ?");
            params.add(endTime);
        }
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
    private PageResponse<List<AuditLog>> emptyPageResult(int page, int size) {
        return PageResponse.success(0L, (long) page, (long) size, Collections.emptyList());
    }

    /**
     * 按时间范围统计审计日志数量
     *
     * @param startTime 起始时间（可为 null）
     * @param endTime   结束时间（可为 null）
     * @return 符合条件的记录数
     */
    private long countByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (shardingStrategy != null) {
            Set<String> tables = resolveTables(startTime, endTime);
            long total = 0;
            for (String table : tables) {
                total += countFromTable(table, null, null, null, null, startTime, endTime);
            }
            return total;
        }
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(baseTableName)
                .append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendTimeCondition(sql, params, startTime, endTime);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    /**
     * 按操作人统计审计日志数量
     *
     * @param operatorId 操作人ID
     * @return 符合条件的记录数
     */
    private long countByOperator(String operatorId) {
        String sql = "SELECT COUNT(*) FROM " + baseTableName + " WHERE operator_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, operatorId);
        return count != null ? count : 0L;
    }

    /**
     * 按操作类型统计审计日志数量
     *
     * @param action 操作类型
     * @return 符合条件的记录数
     */
    private long countByAction(Integer action) {
        String sql = "SELECT COUNT(*) FROM " + baseTableName + " WHERE action = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, action);
        return count != null ? count : 0L;
    }

    /**
     * 按实体类型统计审计日志数量
     *
     * @param entityType 实体类型
     * @return 符合条件的记录数
     */
    private long countByEntityType(String entityType) {
        String sql = "SELECT COUNT(*) FROM " + baseTableName + " WHERE module = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, entityType);
        return count != null ? count : 0L;
    }

    /**
     * 从指定分表中按条件统计审计日志数量
     *
     * @param tableName  表名
     * @param operatorId 操作人ID（可为 null）
     * @param action     操作类型（可为 null）
     * @param module     模块名（可为 null）
     * @param auditType  审计类型（可为 null）
     * @param startTime  起始时间（可为 null）
     * @param endTime    结束时间（可为 null）
     * @return 符合条件的记录数
     */
    private long countFromTable(String tableName, String operatorId, Integer action, String module,
                                Integer auditType, LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName)
                .append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (operatorId != null && !operatorId.isEmpty()) {
            sql.append(" AND operator_id = ?");
            params.add(operatorId);
        }
        if (action != null) {
            sql.append(" AND action = ?");
            params.add(action);
        }
        if (module != null && !module.isEmpty()) {
            sql.append(" AND module = ?");
            params.add(module);
        }
        if (auditType != null) {
            sql.append(" AND audit_type = ?");
            params.add(auditType);
        }
        if (startTime != null) {
            sql.append(" AND operation_time >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND operation_time <= ?");
            params.add(endTime);
        }

        try {
            Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
