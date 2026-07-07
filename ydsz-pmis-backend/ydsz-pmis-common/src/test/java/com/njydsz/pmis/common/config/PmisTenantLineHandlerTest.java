package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.security.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PmisTenantLineHandler} 单元测试（P7-1）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>getTenantId 返回当前 TenantContext 的值</li>
 *   <li>getTenantIdColumn 固定返回 "tenant_id"</li>
 *   <li>ignoreTable 对系统级表返回 true（undo_log / pmis_job_log 等）</li>
 *   <li>ignoreTable 对业务表返回 false（pmis_job / pmis_job_alert_rule 等）</li>
 *   <li>ignoreTable 大小写不敏感</li>
 *   <li>ignoreTable null 安全</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PmisTenantLineHandler 多租户行级处理器测试")
class PmisTenantLineHandlerTest {

    private PmisTenantLineHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PmisTenantLineHandler();
        // 确保测试间 ThreadLocal 干净
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getTenantId: 未设置 TenantContext 时返回默认值 '1'")
    void getTenantId_defaultValue() {
        Expression expr = handler.getTenantId();

        assertTrue(expr instanceof StringValue);
        assertEquals("1", ((StringValue) expr).getValue());
    }

    @Test
    @DisplayName("getTenantId: 已设置 TenantContext 时返回上下文值")
    void getTenantId_contextValue() {
        TenantContext.setTenantId("99999");

        Expression expr = handler.getTenantId();

        assertTrue(expr instanceof StringValue);
        assertEquals("99999", ((StringValue) expr).getValue());
    }

    @Test
    @DisplayName("getTenantIdColumn: 固定返回 'tenant_id'")
    void getTenantIdColumn_returnsTenantId() {
        assertEquals("tenant_id", handler.getTenantIdColumn());
    }

    @Test
    @DisplayName("ignoreTable: null 表名返回 true（安全降级）")
    void ignoreTable_null_returnsTrue() {
        assertTrue(handler.ignoreTable(null));
    }

    @Test
    @DisplayName("ignoreTable: Seata 回滚日志表返回 true")
    void ignoreTable_undoLog_returnsTrue() {
        assertTrue(handler.ignoreTable("undo_log"));
    }

    @Test
    @DisplayName("ignoreTable: Liquibase 历史表返回 true")
    void ignoreTable_liquibaseTables_returnTrue() {
        assertTrue(handler.ignoreTable("pmis_database_change_log"));
        assertTrue(handler.ignoreTable("pmis_database_change_log_lock"));
    }

    @Test
    @DisplayName("ignoreTable: cronjob 系统级表返回 true（P7-1 修复）")
    void ignoreTable_cronjobSystemTables_returnTrue() {
        // pmis_job_log: 任务执行日志（系统全局资源，按设计不携带 tenant_id）
        assertTrue(handler.ignoreTable("pmis_job_log"),
                "pmis_job_log 应被忽略（无 tenant_id 列）");
        // pmis_job_node: 调度节点心跳表（跨租户共享调度集群）
        assertTrue(handler.ignoreTable("pmis_job_node"),
                "pmis_job_node 应被忽略（无 tenant_id 列）");
        // pmis_job_relation: 任务依赖关系表（通过外键间接关联租户）
        assertTrue(handler.ignoreTable("pmis_job_relation"),
                "pmis_job_relation 应被忽略（无 tenant_id 列）");
    }

    @Test
    @DisplayName("ignoreTable: cronjob 业务表返回 false（有 tenant_id 列）")
    void ignoreTable_cronjobBusinessTables_returnFalse() {
        // pmis_job: 任务定义表（有 tenant_id 列）
        assertFalse(handler.ignoreTable("pmis_job"),
                "pmis_job 不应被忽略（有 tenant_id 列）");
        // pmis_job_alert_rule: 告警规则表（有 tenant_id 列）
        assertFalse(handler.ignoreTable("pmis_job_alert_rule"),
                "pmis_job_alert_rule 不应被忽略（有 tenant_id 列）");
        // pmis_job_alert_log: 告警日志表（有 tenant_id 列）
        assertFalse(handler.ignoreTable("pmis_job_alert_log"),
                "pmis_job_alert_log 不应被忽略（有 tenant_id 列）");
        // pmis_job_slow_log: 慢任务日志表（有 tenant_id 列，P6-3 新增）
        assertFalse(handler.ignoreTable("pmis_job_slow_log"),
                "pmis_job_slow_log 不应被忽略（有 tenant_id 列）");
    }

    @Test
    @DisplayName("ignoreTable: 大写表名也能匹配（大小写不敏感）")
    void ignoreTable_caseInsensitive() {
        assertTrue(handler.ignoreTable("UNDO_LOG"),
                "大写 UNDO_LOG 应被忽略");
        assertTrue(handler.ignoreTable("PMIS_JOB_LOG"),
                "大写 PMIS_JOB_LOG 应被忽略");
        assertTrue(handler.ignoreTable("Pmis_Job_Node"),
                "混合大小写 Pmis_Job_Node 应被忽略");
    }

    @Test
    @DisplayName("ignoreTable: 未知业务表返回 false（默认应用租户过滤）")
    void ignoreTable_unknownTable_returnsFalse() {
        assertFalse(handler.ignoreTable("pmis_project"));
        assertFalse(handler.ignoreTable("pmis_user"));
        assertFalse(handler.ignoreTable("some_new_table"));
    }
}
