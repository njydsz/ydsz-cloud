package com.njydsz.common.jdbc.interceptor;

import java.sql.Connection;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.domain.config.DomainProperties;
import com.njydsz.common.exception.custom.SysException;

import lombok.extern.slf4j.Slf4j;

/**
 * 安全查询拦截器（ORDER BY 注入防护 + 深度分页检测）。
 *
 * <p>从 {@link com.njydsz.common.domain.query.PageQuery} 中抽取 SQL 安全相关职责，
 * 统一在 MyBatis 拦截器层处理，实现关注点分离。
 *
 * <p>拦截规则：
 * <ul>
 *   <li>ORDER BY 字段安全校验 — 防止 SQL 注入（仅允许 {@code [a-zA-Z_][a-zA-Z0-9_.]*} 模式）</li>
 *   <li>深度分页检测 — offset 超过阈值时 WARN 日志或拒绝执行</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   domain:
 *     page:
 *       safe-query-enabled: true           # 启用安全查询拦截
 *       order-by-strict-mode: false       # true=拒绝非法排序字段, false=忽略非法排序字段
 *       cursor-warning-threshold: 10000   # 深度分页警告阈值
 *       cursor-reject-threshold: 50000    # 深度分页拒绝阈值
 * </pre>
 *
 * @author ydsz-team
 * @see com.njydsz.common.domain.query.PageQuery 原职责来源
 * @since 1.7.0
 */
@Slf4j
public class SafeQueryInnerInterceptor implements InnerInterceptor {

    /** 安全字段校验正则（与 PageQuery 保持一致） */
    private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");

    /** 排序字段白名单（可选，为空表示仅使用正则校验） */
    private Set<String> orderByWhitelist = null;

    /** 是否启用安全拦截 */
    private boolean enabled = true;

    /** 严格模式：true=拒绝非法排序字段抛异常, false=忽略非法排序字段 */
    private boolean strictMode = false;

    /** 深度分页警告阈值（offset >= 此值时打 WARN） */
    private long cursorWarningThreshold = 10000L;

    /** 深度分页拒绝阈值（offset >= 此值时抛异常） */
    private long cursorRejectThreshold = 50000L;

    /**
     * 初始化安全查询拦截器（从 DomainProperties 加载配置）。
     *
     * @param properties 领域配置属性
     */
    public SafeQueryInnerInterceptor(DomainProperties properties) {
        if (properties != null && properties.getPage() != null) {
            this.cursorWarningThreshold = properties.getPage().getCursorWarningThreshold();
            this.cursorRejectThreshold = properties.getPage().getCursorRejectThreshold();
        }
    }

    /**
     * 默认构造方法（使用默认配置）。
     */
    public SafeQueryInnerInterceptor() {
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        if (!enabled) {
            return;
        }

        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();

        // 仅拦截 SELECT 语句
        if (ms.getSqlCommandType() != SqlCommandType.SELECT) {
            return;
        }

        String sql = mpSh.mPBoundSql().sql();
        if (sql == null || sql.isEmpty()) {
            return;
        }

        // ORDER BY 安全校验
        validateOrderBySafety(sql);

        // 深度分页检测
        checkDeepPagination(sql);
    }

    /**
     * 校验 ORDER BY 子句的安全性。
     *
     * @param sql 原始 SQL
     */
    private void validateOrderBySafety(String sql) {
        // 简单检测：存在 ORDER OF 关键字时校验列名
        String upperSql = sql.toUpperCase();
        if (!upperSql.contains("ORDER BY")) {
            return;
        }

        // 提取 ORDER BY 后的内容
        int orderByIndex = upperSql.lastIndexOf("ORDER BY");
        String orderByClause = sql.substring(orderByIndex + 8).trim();

        // 移除 LIMIT 后的内容
        int limitIndex = orderByClause.toUpperCase().indexOf("LIMIT");
        if (limitIndex > 0) {
            orderByClause = orderByClause.substring(0, limitIndex).trim();
        }

        // 移除 OFFSET 后的内容
        int offsetIndex = orderByClause.toUpperCase().indexOf("OFFSET");
        if (offsetIndex > 0) {
            orderByClause = orderByClause.substring(0, offsetIndex).trim();
        }

        // 移除结尾的 ); 等多余字符
        orderByClause = orderByClause.replaceAll("[);]+$", "").trim();

        if (orderByClause.isEmpty()) {
            return;
        }

        // 按多排序字段分割
        String[] columns = orderByClause.split(",");
        for (String columnExpr : columns) {
            columnExpr = columnExpr.trim();
            if (columnExpr.isEmpty()) {
                continue;
            }

            // 移除 ASC/DESC 后缀
            String columnName = columnExpr.replaceAll("(?i)\\s+(ASC|DESC)$", "").trim();

            // 处理 table.column 格式
            if (columnName.contains(".")) {
                String[] parts = columnName.split("\\.");
                for (String part : parts) {
                    if (!isSafeColumn(part.trim())) {
                        handleUnsafeColumn(part.trim(), columnName);
                    }
                }
            } else {
                if (!isSafeColumn(columnName)) {
                    handleUnsafeColumn(columnName, columnName);
                }
            }
        }
    }

    /**
     * 判断字段名是否安全。
     *
     * @param column 字段名
     * @return 安全返回 true
     */
    private boolean isSafeColumn(String column) {
        if (column == null || column.isEmpty()) {
            return false;
        }
        // 白名单优先
        if (orderByWhitelist != null && !orderByWhitelist.isEmpty()) {
            return orderByWhitelist.contains(column);
        }
        // 正则校验
        return SAFE_COLUMN_PATTERN.matcher(column).matches();
    }

    /**
     * 处理不安全字段。
     *
     * @param fieldName 检测到的字段名
     * @param fullExpr 完整表达式（用于日志）
     */
    private void handleUnsafeColumn(String fieldName, String fullExpr) {
        String message = String.format(
            "SQL 安全拦截：检测到不安全的排序字段 '%s'（表达式: '%s'），已%s。仅允许 [a-zA-Z_][a-zA-Z0-9_.]* 模式",
            fieldName, fullExpr, strictMode ? "拒绝（严格模式）" : "忽略"
        );

        if (strictMode) {
            log.error(message);
            throw new SysException(message);
        } else {
            log.warn(message);
        }
    }

    /**
     * 检测深度分页（简单正则匹配 LIMIT offset 模式）。
     *
     * @param sql SQL 语句
     */
    private void checkDeepPagination(String sql) {
        String upperSql = sql.toUpperCase();

        // 检测 LIMIT offset, size 模式
        java.util.regex.Matcher matcher = Pattern.compile(
            "LIMIT\\s+(\\d+)\\s*,\\s*(\\d+)", Pattern.CASE_INSENSITIVE
        ).matcher(sql);

        if (matcher.find()) {
            long offset = Long.parseLong(matcher.group(1));
            evaluateDeepPagination(offset);
            return;
        }

        // 检测 LIMIT size OFFSET offset 模式
        matcher = Pattern.compile(
            "LIMIT\\s+(\\d+)\\s+OFFSET\\s+(\\d+)", Pattern.CASE_INSENSITIVE
        ).matcher(sql);

        if (matcher.find()) {
            long offset = Long.parseLong(matcher.group(2));
            evaluateDeepPagination(offset);
        }
    }

    /**
     * 评估深度分页风险。
     *
     * @param offset 偏移量
     */
    private void evaluateDeepPagination(long offset) {
        if (offset >= cursorRejectThreshold) {
            String message = String.format(
                "深度分页拒绝：offset=%d 超过拒绝阈值=%d，请使用游标分页（CursorPage）",
                offset, cursorRejectThreshold
            );
            log.error(message);
            throw new SysException(message);
        }

        if (offset >= cursorWarningThreshold) {
            log.warn("深度分页警告：offset={} 超过警告阈值={}，建议改用游标分页",
                offset, cursorWarningThreshold);
        }
    }

    // ===== Getters / Setters =====

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public Set<String> getOrderByWhitelist() {
        return orderByWhitelist;
    }

    public void setOrderByWhitelist(Set<String> orderByWhitelist) {
        this.orderByWhitelist = orderByWhitelist;
    }

    public long getCursorWarningThreshold() {
        return cursorWarningThreshold;
    }

    public void setCursorWarningThreshold(long cursorWarningThreshold) {
        this.cursorWarningThreshold = cursorWarningThreshold;
    }

    public long getCursorRejectThreshold() {
        return cursorRejectThreshold;
    }

    public void setCursorRejectThreshold(long cursorRejectThreshold) {
        this.cursorRejectThreshold = cursorRejectThreshold;
    }
}
