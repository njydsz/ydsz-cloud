package com.njydsz.common.util.diff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.util.api.Experimental;

/**
 * 字段差异追踪注解
 *
 * <p>标注在实体字段上，用于标记需要进行变更对比的字段。 当实体被更新时，系统会自动对比该字段的前后值并记录到操作日志中。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * public class UserDO {
 *     @DiffField(fieldName = "用户名")
 *     private String username;
 *
 *     @DiffField(fieldName = "邮箱", sensitive = true)
 *     private String email;
 *
 *     @DiffField(fieldName = "状态", formatter = StatusFormatter.class)
 *     private Integer status;
 * }
 * }</pre>
 *
 * <p><b>注意事项：</b>
 *
 * <ul>
 *   <li>仅对标注的字段进行差异对比，未标注的字段会被忽略
 *   <li>敏感字段会自动脱敏后记录（保留前 2 后 2 位）
 *   <li>支持自定义格式化器，用于复杂对象的展示
 *   <li>配合 {@link DiffCalculator} 使用可自动生成差异报告
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see DiffCalculator
 * @see FieldDiff
 */
@Experimental("能力储备：字段级差异对比（审计日志场景），当前平台内暂无消费方，启用前请确认测试覆盖")
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DiffField {

  /**
   * 字段中文名称
   *
   * <p>用于在差异报告中展示字段的友好名称。 如果不指定，默认使用字段的 Java 名称。
   *
   * @return 字段中文名称
   */
  String fieldName() default "";

  /**
   * 是否为敏感字段
   *
   * <p>敏感字段在记录差异时会自动脱敏，仅保留前 2 后 2 位。
   *
   * @return 是否为敏感字段
   */
  boolean sensitive() default false;

  /**
   * 自定义格式化器
   *
   * <p>用于将字段值转换为可读的字符串格式。 如果不指定，使用 toString() 方法。
   *
   * @return 格式化器类
   */
  Class<? extends DiffValueFormatter> formatter() default DiffValueFormatter.class;

  /**
   * 是否忽略该字段的变更
   *
   * <p>设置为 true 时，即使字段值发生变化也不会记录到差异报告中。 适用于 updatedAt、version 等系统自动更新的字段。
   *
   * @return 是否忽略
   */
  boolean ignore() default false;
}
