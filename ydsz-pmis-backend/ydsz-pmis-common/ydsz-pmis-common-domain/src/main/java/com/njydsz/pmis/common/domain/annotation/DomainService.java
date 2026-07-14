ackage com.njydsz.pmis.common.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * 领域服务标记注解
 *
 * <p>标注在领域服务类上，用于DDD架构约束和组件扫描。
 * 继承自 Spring {@link Component}，被标注的类会被自动注册为 Spring Bean。</p>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @DomainService
 * public class OrderDomainService {
 *     public void placeOrder(Order order) {
 *         // 领域逻辑
 *     }
 * }
 *
 * // 指定 Bean 名称
 * @DomainService("orderService")
 * public class OrderDomainService {
 *     // ...
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {
    /**
     * 领域服务的 Bean 名称，默认为空（使用 Spring 默认命名策略）
     *
     * @return Bean 名称
     */
    String value() default "";
}
