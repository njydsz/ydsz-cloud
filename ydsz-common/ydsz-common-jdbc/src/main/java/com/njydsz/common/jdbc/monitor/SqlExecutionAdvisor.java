package com.njydsz.common.jdbc.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * SQL 执行计划建议器
 *
 * <p>对慢 SQL 进行静态分析，识别常见反模式并输出优化建议。
 * 作为开发辅助工具，不直接在生产执行 EXPLAIN（避免额外数据库负载），
 * 而是通过正则和语法模式匹配给出可落地的优化提示。
 *
 * <p><b>提供的建议类型：</b>
 * <ul>
 *   <li>检测 SELECT * — 建议只查询必要字段</li>
 *   <li>检测缺少 LIMIT — 建议增加分页限制</li>
 *   <li>检测子查询 — 建议改写为 JOIN</li>
 *   <li>检测 OR 条件 — 建议改写为 UNION 或 IN</li>
 *   <li>检测 LIKE '%xxx' 前缀模糊 — 建议改为后缀模糊或全文索引</li>
 *   <li>检测 ORDER BY + LIMIT 深翻页 — 建议改为游标分页</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * List&lt;String&gt; suggestions = SqlExecutionAdvisor.analyze(slowSql);
 * suggestions.forEach(s -> log.warn("优化建议: {}", s));
 * }</pre>
 *
 * <p><b>注意：</b>此为静态分析工具，建议在开发/测试环境或慢 SQL 告警回调中使用。
 * 对于深度优化，建议结合数据库 EXPLAIN 和 EXPLAIN ANALYZE 进一步验证。
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Slf4j
public final class SqlExecutionAdvisor {

    private SqlExecutionAdvisor() {
    }

    /** SELECT * 模式（含空白字符变体） */
    private static final Pattern SELECT_ALL_PATTERN =
            Pattern.compile("^SELECT\\s+\\*\\s+FROM", Pattern.CASE_INSENSITIVE);

    /** 子查询模式：WHERE xxx IN (SELECT */
    private static final Pattern SUBQUERY_IN_PATTERN =
            Pattern.compile("WHERE\\s+\\w+\\s+IN\\s*\\(\\s*SELECT", Pattern.CASE_INSENSITIVE);

    /** 子查询模式：FROM (SELECT */
    private static final Pattern SUBQUERY_FROM_PATTERN =
            Pattern.compile("FROM\\s*\\(\\s*SELECT", Pattern.CASE_INSENSITIVE);

    /** OR 条件模式（简单匹配） */
    private static final Pattern OR_CONDITION_PATTERN =
            Pattern.compile("WHERE.*\\bOR\\b", Pattern.CASE_INSENSITIVE);

    /** 前缀模糊 LIKE '%xxx' */
    private static final Pattern LIKE_PREFIX_WILDCARD_PATTERN =
            Pattern.compile("LIKE\\s+'%", Pattern.CASE_INSENSITIVE);

    /** 深翻页：LIMIT 配合大 OFFSET */
    private static final Pattern DEEP_PAGINATION_PATTERN =
            Pattern.compile("LIMIT\\s+\\d+\\s*,\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /** 缺少 WHERE 条件的 DELETE（全表删除风险） */
    private static final Pattern UNCONDITIONAL_DELETE_PATTERN =
            Pattern.compile("^DELETE\\s+FROM\\s+\\w+\\s*$", Pattern.CASE_INSENSITIVE);

    /** ORDER BY 子句 */
    private static final Pattern ORDER_BY_PATTERN =
            Pattern.compile("ORDER\\s+BY\\s+(.+?)(?:\\s+LIMIT|\\s*$)", Pattern.CASE_INSENSITIVE);

    /** 深翻页阈值 */
    private static final int DEEP_PAGINATION_THRESHOLD = 1000;

    /**
     * 分析 SQL 并返回优化建议列表
     *
     * @param sql 原始 SQL 语句
     * @return 优化建议列表（无问题时返回空列表）
     */
    public static List<String> analyze(String sql) {
        List<String> suggestions = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) {
            return suggestions;
        }
        String normalizedSql = sql.trim();

        // 1. SELECT * 检测
        if (SELECT_ALL_PATTERN.matcher(normalizedSql).find()) {
            suggestions.add("检测到 SELECT *，建议只查询必要字段以减少 I/O 和网络传输");
        }

        // 2. 子查询检测
        if (SUBQUERY_IN_PATTERN.matcher(normalizedSql).find()
                || SUBQUERY_FROM_PATTERN.matcher(normalizedSql).find()) {
            suggestions.add("检测到子查询，建议改写为 JOIN 以提升可读性和执行效率");
        }

        // 3. OR 条件检测
        if (OR_CONDITION_PATTERN.matcher(normalizedSql).find()) {
            suggestions.add("检测到 OR 条件，若涉及不同字段建议改写为 UNION ALL 以利用索引");
        }

        // 4. 前缀模糊 LIKE 检测
        if (LIKE_PREFIX_WILDCARD_PATTERN.matcher(normalizedSql).find()) {
            suggestions.add("检测到 LIKE '%xxx' 前缀模糊查询，无法利用 B+树索引，建议改为后缀模糊或全文索引");
        }

        // 5. 深翻页检测
        Matcher deepPagingMatcher = DEEP_PAGINATION_PATTERN.matcher(normalizedSql);
        if (deepPagingMatcher.find()) {
            try {
                int offset = Integer.parseInt(deepPagingMatcher.group(1));
                if (offset > DEEP_PAGINATION_THRESHOLD) {
                    suggestions.add(String.format(
                            "检测到深翻页（OFFSET %d），建议改为游标分页（WHERE id > last_id LIMIT n）",
                            offset));
                }
            } catch (NumberFormatException ignored) {
                // 解析失败跳过
            }
        }

        // 6. 无条件 DELETE 检测（危险操作）
        if (UNCONDITIONAL_DELETE_PATTERN.matcher(normalizedSql).matches()) {
            suggestions.add("检测到无条件 DELETE 操作（全表删除），请确认是否预期行为");
        }

        // 7. ORDER BY 索引建议
        Matcher orderMatcher = ORDER_BY_PATTERN.matcher(normalizedSql);
        if (orderMatcher.find()) {
            String orderByClause = orderMatcher.group(1).trim();
            if (!orderByClause.isEmpty() && !orderByClause.contains("?")) {
                suggestions.add(String.format(
                        "ORDER BY %s 建议创建包含排序字段的复合索引（遵循最左前缀原则）",
                        orderByClause));
            }
        }

        // 8. 输出 EXPLAIN 建议
        if (normalizedSql.toUpperCase().startsWith("SELECT")) {
            suggestions.add(String.format(
                    "建议执行 EXPLAIN %s 分析执行计划，关注 type、key、rows 列",
                    normalizedSql));
        }

        return suggestions;
    }
}
