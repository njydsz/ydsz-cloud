package com.njydsz.common.tenant.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.tenant.TenantContext;

/**
 * 测试用租户上下文工具类。
 *
 * <p>提供不依赖 Spring Test 上下文的静态方法，可直接在单元测试中
 * 手动设置和清除租户上下文。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * class OrderMapperTest {
 *
 *     @BeforeEach
 *     void setUp() {
 *         TenantTestUtils.setUpContext(TenantContext.of("tenant_test"));
 *     }
 *
 *     @AfterEach
 *     void tearDown() {
 *         TenantTestUtils.clearContext();
 *     }
 *
 *     @Test
 *     void shouldFilterByTenant() {
 *         // 此时 SQL 拦截器会注入 tenant_id = 'tenant_test' 条件
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see WithMockTenant
 * @see TenantTestExecutionListener
 */
public final class TenantTestUtils {

    private TenantTestUtils() {
    }

    /**
     * 初始化租户上下文（简单模式）。
     *
     * @param tenantId 租户 ID
     */
    public static void setUpContext(String tenantId) {
        setUpContext(tenantId, false);
    }

    /**
     * 初始化租户上下文（指定是否系统租户）。
     *
     * @param tenantId     租户 ID
     * @param systemTenant 是否系统租户
     */
    public static void setUpContext(String tenantId, boolean systemTenant) {
        TenantContext context = systemTenant
                ? TenantContext.system(tenantId)
                : TenantContext.of(tenantId);
        RequestContext.put(BizContextKeys.KEY_TENANT_CONTEXT, context);
        RequestContext.setTenantId(tenantId);
    }

    /**
     * 初始化完整租户上下文（支持多字段）。
     *
     * <pre>{@code
     * Map<String, Object> fields = new HashMap<>();
     * fields.put("tenantId", "tenant_acme");
     * fields.put("companyId", "comp_001");
     * TenantTestUtils.setUpContext("tenant_acme", fields);
     * }</pre>
     *
     * @param tenantId 主租户 ID
     * @param fields   字段值 Map
     */
    public static void setUpContext(String tenantId, Map<String, Object> fields) {
        TenantContext.Builder builder = TenantContext.builder(tenantId);
        if (fields != null) {
            fields.forEach((claim, value) -> {
                if (value instanceof String s) {
                    builder.field(claim, s);
                } else if (value instanceof List<?> list) {
                    builder.fieldValues(claim, list.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .toList());
                }
            });
        }
        TenantContext context = builder.build();
        RequestContext.put(BizContextKeys.KEY_TENANT_CONTEXT, context);
        RequestContext.setTenantId(tenantId);
    }

    /**
     * 初始化跳过隔离的上下文（模拟匿名 URL）。
     */
    public static void setUpSkipIsolation() {
        RequestContext.put(BizContextKeys.KEY_TENANT_CONTEXT, TenantContext.skip());
    }

    /**
     * 清除租户上下文。
     */
    public static void clearContext() {
        RequestContext.remove(BizContextKeys.KEY_TENANT_CONTEXT);
        RequestContext.remove(RequestContext.KEY_TENANT_ID);
    }

    /**
     * 获取当前租户上下文。
     *
     * @return 租户上下文，可能为 null
     */
    public static TenantContext getCurrentContext() {
        return (TenantContext) RequestContext.get(BizContextKeys.KEY_TENANT_CONTEXT);
    }

    /**
     * 断言当前上下文为指定租户。
     *
     * @param expectedTenantId 期望的租户 ID
     */
    public static void assertCurrentTenant(String expectedTenantId) {
        TenantContext ctx = getCurrentContext();
        if (ctx == null) {
            throw new AssertionError("Expected tenant '" + expectedTenantId
                    + "' but no tenant context is set");
        }
        if (!expectedTenantId.equals(ctx.getTenantId())) {
            throw new AssertionError("Expected tenant '" + expectedTenantId
                    + "' but was '" + ctx.getTenantId() + "'");
        }
    }
}
