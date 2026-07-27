package com.njydsz.common.tenant.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.tenant.TenantDimension;

/**
 * 租户列名覆盖注解。
 *
 * <p>标注在 DO 类上，指定该表使用的租户列名（覆盖全局默认 {@code tenant_id}）。
 * 支持 per-table 自定义列名，满足不同表使用不同列名的场景。
 *
 * <p><b>使用示例：</b>
 * <pre>
 * &#64;TenantColumn("org_id")  // 该表用 org_id 而非 tenant_id
 * &#64;TableName("ydsz_file_node")
 * public class FileNodeDO extends MpBaseEntity&lt;String&gt; { ... }
 * </pre>
 *
 * <p>多级租户维度指定：
 * <pre>
 * &#64;TenantColumn(
 *     value = "tenant_id",
 *     dimensions = {TenantDimension.GROUP, TenantDimension.COMPANY}
 * )
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantColumn {

    /**
     * 列名（默认 tenant_id）。
     *
     * @return 列名
     */
    String value() default "tenant_id";

    /**
     * 多级租户维度（默认空 = 使用全局配置）。
     *
     * @return 维度数组
     */
    TenantDimension[] dimensions() default {};
}
