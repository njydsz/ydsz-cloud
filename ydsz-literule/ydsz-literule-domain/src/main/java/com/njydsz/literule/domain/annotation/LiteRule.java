package com.njydsz.literule.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

import com.njydsz.literule.api.Rule;

/**
 * 声明式规则注解（P2-10）
 *
 * <p>标注在实现了 {@link Rule} 接口的 Spring Bean（通常配合 {@link Component}）上，
 * LiteRule 启动时会自动将其注册到规则引擎，无需手动调用 {@code engine.register(rule)}。
 *
 * <pre>{@code
 * @LiteRule
 * @Component
 * public class OverdueRule implements Rule {
 *     public String getCode() { return "OVERDUE_001"; }
 *     public RuleResult evaluate(RuleContext ctx) { ... }
 * }
 * }</pre>
 *
 * <p>适用于以 Java 编码方式实现复杂规则逻辑的场景，兼顾"声明式注册"与"命令式求值"。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LiteRule {

    /**
     * 是否启用自动注册（默认 true；设为 false 可临时停用某规则）
     */
    boolean enabled() default true;
}
