package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.njydsz.pmis.common.security.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

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
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class PmisTenantLineHandler implements TenantLineHandler {

    /** 忽略多租户隔离的表名（小写匹配） */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "undo_log",
            "pmis_database_change_log",
            "pmis_database_change_log_lock"
    );

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.getTenantId());
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
