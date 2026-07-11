package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.njydsz.pmis.common.security.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import java.util.Locale;
import java.util.Set;

/**
 * PMIS 多租户行级处理器
 *
 * <p>由 {@link MybatisPlusAutoConfiguration} 注册到 {@code TenantLineInnerInterceptor}，
 * 为所有非忽略表的 SQL 自动追加 {@code WHERE tenant_id = ?} 条件。
 *
 * <p>H3.1 修复：原配置仅启用 Pagination + OptimisticLocker，未启用 TenantLine，
 * 存在数据越权风险。当前阶段为单租户部署（tenant_id 恒为 1），作为前置防御启用。
 *
 * <p>忽略表清单：
 * <ul>
 *   <li>{@code undo_log} — Seata AT 模式回滚日志表（无 tenant_id 列）</li>
 *   <li>{@code pmis_database_change_log*} — Liquibase 历史表（兼容预留）</li>
 *   <li>{@code pmis_job_log} — 任务执行日志（系统全局资源，按设计不携带 tenant_id，
 *       通过 job_id 关联 pmis_job 间接获得租户归属）</li>
 *   <li>{@code pmis_job_node} — 调度节点心跳表（系统级资源，跨租户共享调度集群）</li>
 *   <li>{@code pmis_job_relation} — 任务依赖关系表（通过 parent_job_id/child_job_id
 *       关联 pmis_job，租户隔离通过 pmis_job.tenant_id 间接保证）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class PmisTenantLineHandler implements TenantLineHandler {

    /**
     * 忽略多租户隔离的表名（小写匹配）。
     *
     * <p>这些表按设计不携带 tenant_id 列，属于系统全局资源或通过外键间接关联租户。
     * 若不忽略，TenantLineInnerInterceptor 会追加 {@code WHERE tenant_id = ?} 导致
     * {@code column "tenant_id" does not exist} 运行时错误。
     */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "undo_log",
            "pmis_database_change_log",
            "pmis_database_change_log_lock",
            // P7-1: cronjob 系统级表（无 tenant_id 列，按设计为全局资源）
            "pmis_job_log",
            "pmis_job_node",
            "pmis_job_relation"
    );

    @Override
    public Expression getTenantId() {
        // VARCHAR(20) 雪花 ID 字符串，使用 StringValue 生成 SQL 字面量 '1'
        return new StringValue(TenantContext.getTenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        return IGNORE_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }
}
