package com.njydsz.pmis.common.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 更新人字段标记注�?
 *
 * <p>标注在实体字段上，表明该字段用于记录数据更新人�?
 * 框架�?INSERT/UPDATE 操作时自动填充此字段�?
 *
 * <p><b>使用示例�?/b>
 * <pre>{@code
 * public class Product extends BaseIdEntity<Long> {
 *
 *     @UpdatedBy
 *     private String updatedBy;
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
public @interface UpdatedBy {
}
