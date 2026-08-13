package com.njydsz.common.tenant.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 测试用 Mock 租户注解。
 *
 * <p>与 {@link TenantTestExecutionListener} 配合使用，在单元测试中模拟租户上下文。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @SpringBootTest
 * @TestExecutionListeners(listeners = TenantTestExecutionListener.class,
 *                          mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
 * class OrderServiceTest {
 *
 *     @Test
 *     @WithMockTenant("tenant_acme")
 *     void shouldOnlyReturnAcmeOrders() {
 *         // 在此测试方法中，RequestContext 中的租户上下文为 tenant_acme
 *         List<Order> orders = orderService.listAll();
 *         assertThat(orders).extracting("tenantId").containsOnly("tenant_acme");
 *     }
 *
 *     @Test
 *     @WithMockTenant(tenantId = "tenant_globex", superAdmin = true)
 *     void superAdminShouldAccessAll() {
 *         // 超级管理员模式，SQL 拦截器不会注入租户条件
 *     }
 * }
 * }</pre>
 *
 * <p><b>注意：</b>使用此注解的测试类需要注册 {@link TenantTestExecutionListener}。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WithMockTenant {

    /**
     * 模拟的租户 ID（默认 "test_tenant"）。
     */
    String value() default "test_tenant";

    /**
     * 模拟的租户 ID（与 {@link #value()} 同义，用于更清晰的语义）。
     */
    String tenantId() default "";

    /**
     * 是否模拟超级管理员（默认 false）。
     */
    boolean superAdmin() default false;

    /**
     * 是否模拟系统租户（默认 false）。
     */
    boolean systemTenant() default false;

    /**
     * 额外的多字段配置（格式：claim=value）。
     *
     * <p>示例：{@code @WithMockTenant(tenantId = "acme", fields = {"companyId=comp_001", "deptId=dept_001,dept_002"})}
     */
    String[] fields() default {};
}
