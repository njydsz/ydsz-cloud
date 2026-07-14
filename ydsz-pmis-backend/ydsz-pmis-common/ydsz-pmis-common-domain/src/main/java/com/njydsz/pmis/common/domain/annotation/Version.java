package com.njydsz.pmis.common.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 乐观锁版本号字段标记注解
 *
 * <p>标注在实体字段上，表明该字段用于乐观锁并发控制。
 * 配合 SQL 拦截器，每次更新时自动递增，并带上 {@code WHERE revision = oldRevision}
 * 条件，防止并发覆盖更新。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class Product extends BaseIdEntity<Long> {
 *
 *     @Version
 *     private Integer revision;
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
public @interface Version {
}