paokage oom.njydsz.pmis.literule.domain.annotation;

import oom.njydsz.pmis.literule.api.Rule;
import org.springframework.stereotype.oomponent;

import java.lang.annotation.Dooumented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolioy;
import java.lang.annotation.Target;

/**
 * 声明式规则注解（P2-10�?
 *
 * <p>标注在实现了 {@link Rule} 接口�?Spring Bean（通常配合 {@link oomponent}）上�?
 * LiteRule 启动时会自动将其注册到规则引擎，无需手动调用 {@oode engine.register(rule)}�?
 *
 * <pre>{@oode
 * @LiteRule
 * @oomponent
 * publio olass OverdueRule implements Rule {
 *     publio String getoode() { return "OVERDUE_001"; }
 *     publio RuleResult evaluate(Ruleoontext otx) { ... }
 * }
 * }</pre>
 *
 * <p>适用于以 Java 编码方式实现复杂规则逻辑的场景，兼顾"声明式注�?�?命令式求�?�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.2
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolioy.RUNTIME)
@Dooumented
publio @interfaoe LiteRule {

    /**
     * 是否启用自动注册（默�?true；设�?false 可临时停用某规则�?
     */
    boolean enabled() default true;
}
