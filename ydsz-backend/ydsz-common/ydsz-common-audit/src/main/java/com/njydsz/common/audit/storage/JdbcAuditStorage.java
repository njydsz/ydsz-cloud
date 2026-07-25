package com.njydsz.common.audit.storage;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.njydsz.common.audit.core.AuditStorage;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.audit.sharding.TableShardingStrategy;
import com.njydsz.common.exception.custom.SysException;

/**
 * JDBC 审计日志存储实现
 * <p>
 * 将审计日志写入数据库表，支持分表策略。内部复用 Spring 容器中的
 * {@link NamedParameterJdbcTemplate}，避免每次创建新实例。
 * </p>
 *
 * <p><b>依赖说明：</b>本类使用 {@code javax.sql.DataSource}，该接口属于 JDK 标准库，
 * 不受 Jakarta EE 迁移影响，在 Spring Boot 3.x 中无需修改。</p>
 *
 * <p><b>多数据库方言支持：</b></p>
 * <ul>
 *   <li>MySQL：默认 INSERT VALUES 批量插入</li>
 *   <li>PostgreSQL：兼容标准批量插入</li>
 *   <li>Oracle：使用参数化批量插入</li>
 * </ul>
 *
 * <p><b>分表支持：</b></p>
 * <ul>
 *   <li>写入时根据操作时间自动路由到对应分表</li>
 *   <li>批量写入时按分表分组，避免跨表批量问题</li>
 * </ul>
 *
 * <p><b>DDL 示例（MySQL）：</b></p>
 * <pre>{@code
 * CREATE TABLE sys_audit_log (
 *   id             VARCHAR(64)  PRIMARY KEY COMMENT '主键',
 *   audit_type     INT          NOT NULL COMMENT '审计类型',
 *   action         INT          NOT NULL COMMENT '操作',
 *   status         INT          NOT NULL COMMENT '状态 1-成功 0-失败',
 *   module         VARCHAR(128) COMMENT '模块',
 *   content        TEXT         COMMENT '内容',
 *   business_no    VARCHAR(64)  COMMENT '业务流水号',
 *   operator_id    VARCHAR(64)  COMMENT '操作人ID',
 *   operator_name  VARCHAR(64)  COMMENT '操作人姓名',
 *   ip_address     VARCHAR(64)  COMMENT '请求IP',
 *   request_params TEXT         COMMENT '请求参数',
 *   error_message  TEXT         COMMENT '错误信息',
 *   cost_time      BIGINT       COMMENT '耗时(ms)',
 *   operation_time DATETIME     NOT NULL COMMENT '操作时间',
 *   created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
 * );
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JdbcAuditStorage implements AuditStorage {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditStorage.class);

    /** 批量写入每批次大小 */
    private static final int BATCH_SIZE = 500;

    /** 默认基础表名 */
    private static final String BASE_TABLE_NAME = "sys_audit_log";

    /** 审计日志表列定义（与 AsyncAuditRecorder 保持一致） */
    private static final String INSERT_COLUMNS =
            "(id, audit_type, action, status, module, content, " +
            "business_no, operator_id, operator_code, operator_name, ip_address, ip_location, " +
            "user_agent, request_params, response_result, error_message, cost_time, " +
            "app_id, app_code, app_name, extra_info, operation_time, created_at)";

    /** INSERT 语句命名参数值模板 */
    private static final String INSERT_VALUES =
            "VALUES (:id, :auditType, :action, :status, :module, :content, " +
            ":businessNo, :operatorId, :operatorCode, :operatorName, :ipAddress, :ipLocation, " +
            ":userAgent, :requestParams, :responseResult, :errorMessage, :costTime, " +
            ":appId, :appCode, :appName, :extraInfo, :operationTime, :createdAt)";

    /** 命名参数 JDBC 模板，用于执行参数化 SQL */
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    /** 分表策略（可为 null） */
    private final TableShardingStrategy shardingStrategy;
    /** 基础表名 */
    private final String baseTableName;

    /** 表名白名单正则：仅允许字母、数字、下划线，禁止特殊字符 */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    /**
     * 构造函数 - 使用 DataSource 创建 NamedParameterJdbcTemplate（无分表）
     *
     * @param dataSource 数据源
     */
    public JdbcAuditStorage(DataSource dataSource) {
        this(dataSource, null, BASE_TABLE_NAME);
    }

    /**
     * 构造函数 - 直接注入 NamedParameterJdbcTemplate（无分表）
     *
     * @param namedParameterJdbcTemplate Spring 管理的 NamedParameterJdbcTemplate
     */
    public JdbcAuditStorage(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this(namedParameterJdbcTemplate, null, BASE_TABLE_NAME);
    }

    /**
     * 构造函数 - 支持分表策略
     *
     * @param dataSource        数据源
     * @param shardingStrategy  分表策略（可为 null）
     * @param baseTableName     基础表名
     */
    public JdbcAuditStorage(DataSource dataSource, TableShardingStrategy shardingStrategy, String baseTableName) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.shardingStrategy = shardingStrategy;
        this.baseTableName = baseTableName != null ? baseTableName : BASE_TABLE_NAME;
        log.info("【审计存储】JdbcAuditStorage 初始化完成, 分表策略={}, 基础表名={}",
                shardingStrategy != null ? shardingStrategy.getShardType() : "DISABLED", this.baseTableName);
    }

    /**
     * 构造函数 - 支持分表策略（推荐方式，复用已有 NamedParameterJdbcTemplate）
     *
     * @param namedParameterJdbcTemplate Spring 管理的 NamedParameterJdbcTemplate
     * @param shardingStrategy           分表策略（可为 null）
     * @param baseTableName              基础表名
     */
    public JdbcAuditStorage(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                            TableShardingStrategy shardingStrategy, String baseTableName) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.shardingStrategy = shardingStrategy;
        this.baseTableName = baseTableName != null ? baseTableName : BASE_TABLE_NAME;
        log.info("【审计存储】JdbcAuditStorage 初始化完成, 分表策略={}, 基础表名={}",
                shardingStrategy != null ? shardingStrategy.getShardType() : "DISABLED", this.baseTableName);
    }

    @Override
    public void save(AuditLog auditLog) {
        if (auditLog == null) {
            return;
        }
        try {
            String tableName = resolveTableName(auditLog);
            String sql = buildInsertSql(tableName);
            Map<String, Object> params = buildParamMap(auditLog);
            namedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            log.error("【审计存储】写入审计日志失败 id={}", auditLog.getId(), e);
        }
    }

    @Override
    public void saveBatch(List<AuditLog> auditLogs) {
        if (auditLogs == null || auditLogs.isEmpty()) {
            return;
        }
        try {
            if (shardingStrategy != null) {
                saveBatchWithSharding(auditLogs);
            } else {
                saveBatchNoSharding(auditLogs);
            }
        } catch (Exception e) {
            log.error("【审计存储】批量写入审计日志失败, count={}", auditLogs.size(), e);
            throw new SysException("审计日志批量写入失败", e);
        }
    }

    /**
     * 无分表模式的批量写入
     */
    private void saveBatchNoSharding(List<AuditLog> auditLogs) {
        int total = auditLogs.size();
        for (int offset = 0; offset < total; offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, total);
            List<AuditLog> batch = auditLogs.subList(offset, end);
            saveBatchStandard(baseTableName, batch);
        }
    }

    /**
     * 分表模式的批量写入：按分表分组后分别批量写入
     */
    private void saveBatchWithSharding(List<AuditLog> auditLogs) {
        Map<String, List<AuditLog>> grouped = auditLogs.stream()
                .collect(Collectors.groupingBy(this::resolveTableName));

        for (Map.Entry<String, List<AuditLog>> entry : grouped.entrySet()) {
            String tableName = entry.getKey();
            List<AuditLog> batchList = entry.getValue();

            for (int offset = 0; offset < batchList.size(); offset += BATCH_SIZE) {
                int end = Math.min(offset + BATCH_SIZE, batchList.size());
                List<AuditLog> batch = batchList.subList(offset, end);
                saveBatchStandard(tableName, batch);
            }
        }
    }

    /**
     * 标准批量插入（适用于所有数据库方言）
     */
    private void saveBatchStandard(String tableName, List<AuditLog> batch) {
        String sql = buildInsertSql(tableName);
        Map<String, Object>[] batchParams = IntStream.range(0, batch.size())
                .mapToObj(i -> buildParamMap(batch.get(i)))
                .toArray(Map[]::new);
        namedParameterJdbcTemplate.batchUpdate(sql, batchParams);
    }

    /**
     * 根据审计日志解析目标表名
     */
    private String resolveTableName(AuditLog auditLog) {
        if (shardingStrategy == null) {
            return baseTableName;
        }
        LocalDateTime time = auditLog.getOperationTime();
        if (time == null) {
            time = auditLog.getCreatedAt();
        }
        if (time == null) {
            time = LocalDateTime.now();
        }
        return shardingStrategy.getTableName(baseTableName, time);
    }

    /**
     * 构建 INSERT SQL
     */
    private String buildInsertSql(String tableName) {
        return "INSERT INTO " + validateTableName(tableName) + " " + INSERT_COLUMNS + " " + INSERT_VALUES;
    }

    /**
     * 校验表名安全性，防止 SQL 注入
     * <p>表名无法使用 PreparedStatement 参数绑定，因此通过白名单正则校验来消除注入风险。
     *
     * @param tableName 待校验的表名
     * @return 校验通过的表名
     * @throws IllegalArgumentException 表名不符合安全规则时
     */
    private String validateTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("表名包含非法字符: " + tableName);
        }
        return tableName;
    }

    /**
     * 构建 NamedParameterJdbcTemplate 参数 Map
     */
    private Map<String, Object> buildParamMap(AuditLog auditLog) {
        Map<String, Object> params = new HashMap<>(22);
        params.put("id", auditLog.getId());
        params.put("auditType", auditLog.getAuditType());
        params.put("action", auditLog.getAction());
        params.put("status", auditLog.getStatus());
        params.put("module", auditLog.getModule());
        params.put("content", auditLog.getContent());
        params.put("businessNo", auditLog.getBusinessNo());
        params.put("operatorId", auditLog.getOperatorId());
        params.put("operatorCode", auditLog.getOperatorCode());
        params.put("operatorName", auditLog.getOperatorName());
        params.put("ipAddress", auditLog.getIpAddress());
        params.put("ipLocation", auditLog.getIpLocation());
        params.put("userAgent", auditLog.getUserAgent());
        params.put("requestParams", auditLog.getRequestParams());
        params.put("responseResult", auditLog.getResponseResult());
        params.put("errorMessage", auditLog.getErrorMessage());
        params.put("costTime", auditLog.getCostTime());
        params.put("appId", auditLog.getAppId());
        params.put("appCode", auditLog.getAppCode());
        params.put("appName", auditLog.getAppName());
        params.put("extraInfo", auditLog.getExtraInfo());
        params.put("operationTime", auditLog.getOperationTime() != null
                ? Timestamp.valueOf(auditLog.getOperationTime()) : new Timestamp(System.currentTimeMillis()));
        params.put("createdAt", auditLog.getCreatedAt() != null
                ? Timestamp.valueOf(auditLog.getCreatedAt()) : new Timestamp(System.currentTimeMillis()));
        return params;
    }

    @Override
    public String getType() {
        return "JDBC";
    }

    /**
     * 清理过期日志（无分表模式）
     */
    public int cleanExpiredLogs(int retentionDays) {
        if (shardingStrategy == null) {
            return cleanFromTable(baseTableName, retentionDays);
        }
        int total = 0;
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(retentionDays);
        Set<String> tables = shardingStrategy.getTableNamesInRange(baseTableName, startTime, endTime);
        for (String table : tables) {
            total += cleanFromTable(table, retentionDays);
        }
        return total;
    }

    /**
     * 从指定表中清理过期审计日志
     *
     * <p>使用 Java 计算过期时间点并传参给 SQL，避免使用 MySQL 专有的
     * {@code DATE_SUB(NOW(), INTERVAL ? DAY)} 函数，保证在 PostgreSQL/Oracle/SQL Server 等数据库上兼容。
     *
     * @param tableName     表名
     * @param retentionDays 日志保留天数
     * @return 清理的记录数
     */
    private int cleanFromTable(String tableName, int retentionDays) {
        String safeTable = validateTableName(tableName);
        String sql = "DELETE FROM " + safeTable + " WHERE created_at < ?";
        // 在 Java 侧计算过期时间点，跨数据库兼容
        Timestamp expireTime = new Timestamp(
                System.currentTimeMillis() - (long) retentionDays * 24L * 60L * 60L * 1000L);
        try {
            return namedParameterJdbcTemplate.getJdbcTemplate().update(sql, expireTime);
        } catch (Exception e) {
            log.warn("【审计存储】清理过期日志失败, table={}", tableName, e);
            return 0;
        }
    }

    /**
     * 获取分表策略
     */
    public TableShardingStrategy getShardingStrategy() {
        return shardingStrategy;
    }

    /**
     * 获取基础表名
     */
    public String getBaseTableName() {
        return baseTableName;
    }
}
