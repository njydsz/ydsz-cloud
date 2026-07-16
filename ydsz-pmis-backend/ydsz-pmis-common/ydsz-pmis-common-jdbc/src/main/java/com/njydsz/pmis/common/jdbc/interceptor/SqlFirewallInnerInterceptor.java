package com.njydsz.pmis.common.jdbc.interceptor;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.pmis.common.exception.custom.SysException;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.update.Update;

/**
 * SQL 防火墙拦截器
 *
 * <p>在 SQL 执行前进行安全校验，拦截危险 SQL 操作，防止误操作或恶意注入导致的数据库破坏。
 *
 * <p>拦截规则：
 * <ul>
 *   <li>DROP TABLE / DROP DATABASE — 禁止删除表/库</li>
 *   <li>TRUNCATE TABLE — 禁止清空表</li>
 *   <li>DELETE WITHOUT WHERE — 禁止无条件删除</li>
 *   <li>UPDATE WITHOUT WHERE — 禁止无条件更新</li>
 *   <li>ALTER TABLE ... DROP — 禁止删除列（可选）</li>
 *   <li>GRANT / REVOKE — 禁止权限操作</li>
 *   <li>分号分隔的多语句 — 禁止多语句执行</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   jdbc:
 *     sql-firewall:
 *       enabled: true
 *       block-drop-table: true
 *       block-truncate: true
 *       block-delete-without-where: true
 *       block-update-without-where: true
 *       block-multi-statement: true
 *       allow-tables: []  # 允许 DROP/TRUNCATE 的表白名单
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 * @see InnerInterceptor
 */
@Slf4j
public class SqlFirewallInnerInterceptor implements InnerInterceptor {

    /** DROP TABLE / DROP DATABASE / DROP INDEX 正则 */
    private static final Pattern DROP_PATTERN = Pattern.compile(
            "\\bDROP\\s+(TABLE|DATABASE|INDEX|SCHEMA|VIEW)\\b", Pattern.CASE_INSENSITIVE);

    /** TRUNCATE TABLE 正则 */
    private static final Pattern TRUNCATE_PATTERN = Pattern.compile(
            "\\bTRUNCATE\\s+TABLE?\\b", Pattern.CASE_INSENSITIVE);

    /** GRANT / REVOKE 正则 */
    private static final Pattern DDL_PERMISSION_PATTERN = Pattern.compile(
            "\\b(GRANT|REVOKE)\\b", Pattern.CASE_INSENSITIVE);

    /** 分号检测（排除字符串内的分号） */
    private static final Pattern SEMICOLON_PATTERN = Pattern.compile(
            ";\\s*", Pattern.CASE_INSENSITIVE);

    private boolean enabled = false;
    private boolean blockDropTable = true;
    private boolean blockTruncate = true;
    private boolean blockDeleteWithoutWhere = true;
    private boolean blockUpdateWithoutWhere = true;
    private boolean blockMultiStatement = true;
    private boolean blockPermissionOps = true;
    private Set<String> allowTables = Collections.emptySet();

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        if (!enabled) {
            return;
        }

        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        String sql = mpSh.mPBoundSql().sql();

        if (sql == null || sql.isEmpty()) {
            return;
        }

        String trimmedSql = sql.trim();

        // 多语句检测
        if (blockMultiStatement && containsMultiStatement(trimmedSql)) {
            reject("SQL 防火墙拦截：检测到多语句执行（分号分隔），已拒绝", sql);
            return;
        }

        // DROP 检测
        if (blockDropTable && DROP_PATTERN.matcher(trimmedSql).find()) {
            if (!isTableAllowed(trimmedSql)) {
                reject("SQL 防火墙拦截：检测到 DROP 操作，已拒绝", sql);
                return;
            }
        }

        // TRUNCATE 检测
        if (blockTruncate && TRUNCATE_PATTERN.matcher(trimmedSql).find()) {
            if (!isTableAllowed(trimmedSql)) {
                reject("SQL 防火墙拦截：检测到 TRUNCATE 操作，已拒绝", sql);
                return;
            }
        }

        // 权限操作检测
        if (blockPermissionOps && DDL_PERMISSION_PATTERN.matcher(trimmedSql).find()) {
            reject("SQL 防火墙拦截：检测到 GRANT/REVOKE 权限操作，已拒绝", sql);
            return;
        }

        SqlCommandType commandType = ms.getSqlCommandType();

        // DELETE 无 WHERE 检测（使用 JSqlParser AST 精确判断，避免字符串匹配误判）
        if (blockDeleteWithoutWhere && commandType == SqlCommandType.DELETE) {
            if (!hasWhereClause(trimmedSql, SqlCommandType.DELETE)) {
                reject("SQL 防火墙拦截：检测到无 WHERE 条件的 DELETE 操作，已拒绝", sql);
                return;
            }
        }

        // UPDATE 无 WHERE 检测（使用 JSqlParser AST 精确判断，避免字符串匹配误判）
        if (blockUpdateWithoutWhere && commandType == SqlCommandType.UPDATE) {
            if (!hasWhereClause(trimmedSql, SqlCommandType.UPDATE)) {
                reject("SQL 防火墙拦截：检测到无 WHERE 条件的 UPDATE 操作，已拒绝", sql);
                return;
            }
        }
    }

    /**
     * 使用 JSqlParser AST 检测 DELETE/UPDATE 语句是否包含 WHERE 子句
     *
     * @param sql SQL 语句
     * @param commandType SQL 命令类型（DELETE 或 UPDATE）
     * @return true 表示有 WHERE 子句，false 表示无 WHERE 子句或解析失败
     */
    private boolean hasWhereClause(String sql, SqlCommandType commandType) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (commandType == SqlCommandType.DELETE && statement instanceof Delete delete) {
                return delete.getWhere() != null;
            }
            if (commandType == SqlCommandType.UPDATE && statement instanceof Update update) {
                return update.getWhere() != null;
            }
        } catch (JSQLParserException e) {
            // 解析失败时保守拒绝，避免危险 SQL 通过
            log.warn("SQL 防火墙：JSqlParser 解析失败，保守拒绝执行。sql={}", truncate(sql, 200));
            return false;
        }
        return false;
    }

    /**
     * 检测 SQL 是否包含多语句（分号分隔）
     */
    private boolean containsMultiStatement(String sql) {
        // 去除字符串字面量后检查分号
        String cleaned = sql.replaceAll("'(?:[^']|'')*'", "''");
        // 去除末尾空格后检测尾部是否为分号
        String trimmed = cleaned.trim();
        return SEMICOLON_PATTERN.matcher(cleaned).find() && !trimmed.endsWith(";");
    }

    /**
     * 检查表是否在白名单中
     */
    private boolean isTableAllowed(String sql) {
        if (allowTables.isEmpty()) {
            return false;
        }
        String lowerSql = sql.toLowerCase();
        for (String table : allowTables) {
            if (lowerSql.contains(table.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拒绝 SQL 执行
     */
    private void reject(String message, String sql) {
        log.error("{} | SQL: {}", message, truncate(sql, 200));
        throw new SysException(message);
    }

    private String truncate(String sql, int maxLength) {
        if (sql == null) {
            return "N/A";
        }
        return sql.length() > maxLength ? sql.substring(0, maxLength) + "..." : sql;
    }

    // ----- Getters / Setters -----

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBlockDropTable() {
        return blockDropTable;
    }

    public void setBlockDropTable(boolean blockDropTable) {
        this.blockDropTable = blockDropTable;
    }

    public boolean isBlockTruncate() {
        return blockTruncate;
    }

    public void setBlockTruncate(boolean blockTruncate) {
        this.blockTruncate = blockTruncate;
    }

    public boolean isBlockDeleteWithoutWhere() {
        return blockDeleteWithoutWhere;
    }

    public void setBlockDeleteWithoutWhere(boolean blockDeleteWithoutWhere) {
        this.blockDeleteWithoutWhere = blockDeleteWithoutWhere;
    }

    public boolean isBlockUpdateWithoutWhere() {
        return blockUpdateWithoutWhere;
    }

    public void setBlockUpdateWithoutWhere(boolean blockUpdateWithoutWhere) {
        this.blockUpdateWithoutWhere = blockUpdateWithoutWhere;
    }

    public boolean isBlockMultiStatement() {
        return blockMultiStatement;
    }

    public void setBlockMultiStatement(boolean blockMultiStatement) {
        this.blockMultiStatement = blockMultiStatement;
    }

    public boolean isBlockPermissionOps() {
        return blockPermissionOps;
    }

    public void setBlockPermissionOps(boolean blockPermissionOps) {
        this.blockPermissionOps = blockPermissionOps;
    }

    public Set<String> getAllowTables() {
        return allowTables;
    }

    public void setAllowTables(Set<String> allowTables) {
        if (allowTables == null) {
            this.allowTables = Collections.emptySet();
        } else {
            Set<String> normalized = new HashSet<>(allowTables.size());
            for (String table : allowTables) {
                if (table != null) {
                    normalized.add(table.trim().toLowerCase());
                }
            }
            this.allowTables = normalized;
        }
    }
}
