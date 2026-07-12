package com.njydsz.pmis.common.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 租户字段标记注解
 *
 * <p>标注在实体字段上，表明该字段用于多租户数据隔离。
 * 配合 SQL 拦截器可自动注入 tenant_id 条件。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class Product extends BaseEntity<Long> {
 *
 *     @TenantId
 *     private Long tenantId;
 *
 *     private String productName;
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantId {

    /**
     * 租户字段名，默认 "tenant_id"
     */
    String value() default "tenant_id";
}
