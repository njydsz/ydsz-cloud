package com.njydsz.pmis.common.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 逻辑删除（软删除）标记注解
 *
 * <p>标注在实体类上，表明该实体支持逻辑删除。
 * 配合 SQL 拦截器可在删除时执行 UPDATE 而非 DELETE。
 * 查询时自动追加 {@code WHERE deleted = 0} 条件。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Data
 * @SoftDelete
 * public class Product extends BaseIdEntity<Long> {
 *
 *     private String productName;
 *
 *     private Integer deleted;
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SoftDelete {

    /**
     * 删除标记字段名，默认 "deleted"
     */
    String value() default "deleted";
}