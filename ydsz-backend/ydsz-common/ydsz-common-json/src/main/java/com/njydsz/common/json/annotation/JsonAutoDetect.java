package com.njydsz.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jackson 兼容注解：控制字段/Getter/Setter/Creator 的自动检测可见性。
 *
 * <p><b>已废弃</b>，请改用原生注解 {@link JsonVisibility}。本注解仅作为 Jackson
 * 兼容入口保留，在 {@link com.njydsz.common.json.provider.FieldMetadataLoader}
 * 中通过 {@link Visibility#toYdszVisibility()} 委托到 {@link JsonVisibility.Visibility}。</p>
 *
 * <p><b>迁移示例：</b></p>
 * <pre>
 * // 旧（已废弃）
 * {@code @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)}
 *
 * // 新（推荐）
 * {@code @JsonVisibility(fieldVisibility = JsonVisibility.Visibility.ANY)}
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 改用 {@link JsonVisibility}，本注解仅为 Jackson 兼容入口，功能完全等价。
 */
@Deprecated(since = "1.0.0", forRemoval = false)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonAutoDetect {

    /**
     * 字段可见性级别。
     */
    Visibility fieldVisibility() default Visibility.DEFAULT;

    /**
     * Getter 方法可见性级别。
     */
    Visibility getterVisibility() default Visibility.DEFAULT;

    /**
     * Setter 方法可见性级别。
     */
    Visibility setterVisibility() default Visibility.DEFAULT;

    /**
     * Creator 方法可见性级别。
     */
    Visibility creatorVisibility() default Visibility.DEFAULT;

    /**
     * 可见性级别枚举（与 Jackson 一致）。
     */
    enum Visibility {
        /** 默认（使用全局配置） */
        DEFAULT,
        /** 最高：任何修饰符都可访问 */
        ANY,
        /** 非 private */
        NON_PRIVATE,
        /** protected 及以上 */
        PROTECTED_AND_PUBLIC,
        /** 仅 public */
        PUBLIC_ONLY,
        /** 最低：不自动检测 */
        NONE;

        /**
         * 将 Jackson 兼容枚举映射到 JsonVisibility.Visibility。
         *
         * @return 对应的 JsonVisibility.Visibility 枚举值
         */
        public JsonVisibility.Visibility toYdszVisibility() {
            switch (this) {
                case ANY: return JsonVisibility.Visibility.ANY;
                case NON_PRIVATE: return JsonVisibility.Visibility.ANY; // Json 无 NON_PRIVATE，降级为 ANY
                case PROTECTED_AND_PUBLIC: return JsonVisibility.Visibility.PROTECTED_AND_PUBLIC;
                case PUBLIC_ONLY: return JsonVisibility.Visibility.PUBLIC_ONLY;
                case NONE: return JsonVisibility.Visibility.NONE;
                default: return JsonVisibility.Visibility.ANY;
            }
        }
    }
}
