package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实验性功能。
 *
 * <p>被标记的 API 属于 JSON 引擎的非核心扩展功能（如 JSON Schema 校验、
 * JSON Patch/Merge Patch/Pointer 等 RFC 扩展），尚未稳定，不保证向后兼容。</p>
 *
 * <p>业务方使用这些功能时需注意：
 * <ul>
 *   <li>API 可能在后续版本中被重构、重命名或移除</li>
 *   <li>功能可能存在未覆盖的边界场景</li>
 *   <li>建议在非关键路径使用，或做好隔离层</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Experimental {

    /**
     * 备注：实验性功能的风险说明或替代方案建议。
     *
     * @return 备注信息
     */
    String value() default "";
}
