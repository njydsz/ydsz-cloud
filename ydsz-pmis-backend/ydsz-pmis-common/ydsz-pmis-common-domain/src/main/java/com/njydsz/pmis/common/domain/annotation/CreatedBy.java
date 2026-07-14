package com.njydsz.pmis.common.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 创建人字段标记注解
 *
 * <p>标注在实体字段上，表明该字段用于记录数据创建人。
 * 框架在 INSERT 操作时自动填充此字段。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class Product extends BaseIdEntity<Long> {
 *
 *     @CreatedBy
 *     private String createdBy;
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CreatedBy {
}
