package com.njydsz.common.tenant.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.tenant.TenantContext;

/**
 * 测试执行监听器：在测试方法执行前后注入/清除租户上下文。
 *
 * <p>配合 {@link WithMockTenant} 注解使用，自动管理测试中的租户上下文生命周期。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * @SpringBootTest
 * @TestExecutionListeners(
 *     listeners = TenantTestExecutionListener.class,
 *     mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
 * )
 * class MyServiceTest {
 *     // ...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see WithMockTenant
 */
public class TenantTestExecutionListener implements TestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        WithMockTenant annotation = findAnnotation(testContext);
        if (annotation == null) {
            return;
        }

        String tenantId = resolveTenantId(annotation);
        TenantContext.Builder builder = TenantContext.builder(tenantId)
                .superAdmin(annotation.superAdmin())
                .systemTenant(annotation.systemTenant());

        // 解析额外字段
        for (String field : annotation.fields()) {
            if (field.contains("=")) {
                String[] parts = field.split("=", 2);
                String claim = parts[0].trim();
                String value = parts[1].trim();
                if (value.contains(",")) {
                    // 多值
                    String[] values = value.split(",");
                    builder.fieldValues(claim, List.of(values));
                } else {
                    builder.field(claim, value);
                }
            }
        }

        TenantContext context = builder.build();
        RequestContext.put(BizContextKeys.KEY_TENANT_CONTEXT, context);
        RequestContext.setTenantId(tenantId);
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        RequestContext.remove(BizContextKeys.KEY_TENANT_CONTEXT);
        RequestContext.remove(RequestContext.KEY_TENANT_ID);
    }

    private WithMockTenant findAnnotation(TestContext testContext) {
        // 优先方法级别
        WithMockTenant annotation = testContext.getTestMethod()
                .getAnnotation(WithMockTenant.class);
        if (annotation == null) {
            // 类级别
            annotation = testContext.getTestClass().getAnnotation(WithMockTenant.class);
        }
        return annotation;
    }

    private String resolveTenantId(WithMockTenant annotation) {
        if (!annotation.tenantId().isEmpty()) {
            return annotation.tenantId();
        }
        return annotation.value();
    }
}
