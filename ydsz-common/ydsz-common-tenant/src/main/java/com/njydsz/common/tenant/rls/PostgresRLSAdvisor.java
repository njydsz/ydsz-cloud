package com.njydsz.common.tenant.rls;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PostgreSQL Row-Level Security (RLS) 顾问工具。
 *
 * <p>提供工具方法生成 PostgreSQL RLS 集成所需的 DDL 语句，
 * 包括启用 RLS、创建策略、设置角色等。
 *
 * <h3>使用方式</h3>
 * <p>在应用启动时调用 generateDdl() 获取 DDL 脚本，由 DBA 审核后执行。
 *
 * <pre>{@code
 * // 在 CommandLineRunner 或 Flyway 迁移中
 * @Bean
 * CommandLineRunner rlsInitializer(PostgresRLSAdvisor advisor) {
 *     return args -> {
 *         List<String> ddl = advisor.generateDdl(List.of("orders", "customers"));
 *         ddl.forEach(log::info); // 输出到日志，由运维执行
 *     };
 * }
 * }</pre>
 *
 * <h3>RLS 策略模式</h3>
 * <p>使用 SET ROLE 方式隔离租户：
 * <ol>
 *   <li>应用连接池使用 role: {@code app_user}（无 RLS 限制）</li>
 *   <li>在 JDBC URL 中追加 {@code currentSchema} 或代码中执行 SET ROLE</li>
 *   <li>RLS policy 检查 current_user = tenant_id::text</li>
 * </ol>
 *
 * <p>或者使用 session 变量模式：
 * <ol>
 *   <li>执行 {@code SET app.tenant_id = 'xxx'}</li>
 *   <li>RLS policy 检查: {@code tenant_id = current_setting('app.tenant_id')}</li>
 * </ol>
 *
 * <p>推荐使用 session 变量模式，无需为每个租户创建数据库角色。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public final class PostgresRLSAdvisor {

    private PostgresRLSAdvisor() {
    }

    /**
     * 生成启用 RLS 的 DDL 脚本（使用 session 变量模式）。
     *
     * @param tableNames     需要启用 RLS 的表名
     * @param tenantColumn   租户列名（默认 tenant_id）
     * @param ignoreTables   跳过 RLS 的表名
     * @return DDL 语句列表（按执行顺序）
     */
    public static List<String> generateDdl(List<String> tableNames, String tenantColumn,
                                           Set<String> ignoreTables) {
        List<String> ddl = new ArrayList<>();

        // 1. 开启 session 变量模式所需的配置
        ddl.add("-- 为每个表创建 RLS 策略（session 变量模式）");
        ddl.add("-- 应用层需在每次请求开始时执行: SET app.tenant_id = '<tenantId>';");
        ddl.add("");

        for (String tableName : tableNames) {
            if (ignoreTables != null && ignoreTables.contains(tableName.toLowerCase())) {
                ddl.add("-- 表 " + tableName + " 在 ignore-tables 中，跳过 RLS");
                continue;
            }

            ddl.add(String.format("-- ===== 表: %s =====", tableName));
            // 启用 RLS
            ddl.add(String.format("ALTER TABLE %s ENABLE ROW LEVEL SECURITY;", tableName));
            // 创建策略
            ddl.add(String.format(
                "CREATE POLICY tenant_isolation_policy ON %s " +
                "FOR ALL " +
                "TO public " +
                "USING (%s::text = current_setting('app.tenant_id', true)) " +
                "WITH CHECK (%s::text = current_setting('app.tenant_id', true));",
                tableName, tenantColumn, tenantColumn));
            ddl.add("");
        }

        return ddl;
    }

    /**
     * 生成启用 RLS 的 DDL（使用默认列名 tenant_id）。
     *
     * @param tableNames 表名列表
     * @return DDL 语句列表
     */
    public static List<String> generateDdl(List<String> tableNames) {
        return generateDdl(tableNames, "tenant_id", Set.of());
    }

    /**
     * 生成删除 RLS 策略的 DDL（用于回滚或清理）。
     *
     * @param tableNames 表名列表
     * @return DDL 语句列表
     */
    public static List<String> generateDisableDdl(List<String> tableNames) {
        List<String> ddl = new ArrayList<>();
        for (String tableName : tableNames) {
            ddl.add(String.format(
                "DROP POLICY IF EXISTS tenant_isolation_policy ON %s;", tableName));
            ddl.add(String.format("ALTER TABLE %s DISABLE ROW LEVEL SECURITY;", tableName));
        }
        return ddl;
    }

    /**
     * 生成 session 变量初始化的 SQL（应用层每次请求开始时调用）。
     *
     * @param tenantId 租户 ID
     * @return SQL 语句
     */
    public static String buildSetSessionVariableSql(String tenantId) {
        return String.format("SET app.tenant_id = '%s';", escapeSql(tenantId));
    }

    /**
     * 生成应用层 JDBC URL 中追加的参数（用于 SET ROLE 模式）。
     *
     * <p>当使用角色模式时，在 URL 中追加 {@code ?options=-c%20app.tenant_id%3Dxxx}。
     * 此处返回参数值，由应用层根据租户 ID 动态拼接。
     *
     * @return URL 参数格式字符串
     */
    public static String buildJdbcOptionsParam(String tenantId) {
        return String.format("options=-c app.tenant_id=%s", tenantId);
    }

    /**
     * 简易 SQL 逃逸（仅用于租户 ID 值，不用于通用 SQL 防护）。
     * <p>注意：实际应使用 PreparedStatement，此方法仅用于输出 DDL。
     */
    private static String escapeSql(String value) {
        if (value == null) return "NULL";
        return value.replace("'", "''");
    }
}
