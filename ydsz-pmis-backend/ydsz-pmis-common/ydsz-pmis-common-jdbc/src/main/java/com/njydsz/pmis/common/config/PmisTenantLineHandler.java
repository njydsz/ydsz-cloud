package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Plus 多租户拦截器 —— 从安全上下文获取租户 ID。
 * <p>
 * 此组件放在 data 模块，租户 ID 通过 {@link TenantContextHolder} 获取，
 * security 模块在认证后设置租户 ID。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class PmisTenantLineHandler implements TenantLineHandler {

    /** 忽略租户隔离的表 */
    private static final Set<String> IGNORE_TABLES;

    static {
        Set<String> tables = new HashSet<>();
        tables.add("sys_config");
        tables.add("sys_dict");
        tables.add("sys_dict_item");
        tables.add("sys_menu");
        tables.add("sys_role_menu");
        tables.add("sys_tenant");
        tables.add("pmis_ai_model_config");
        tables.add("pmis_literule_rule");
        tables.add("pmis_literule_rule_history");
        IGNORE_TABLES = Collections.unmodifiableSet(tables);
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = 0L; // 默认租户
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);
    }
}
