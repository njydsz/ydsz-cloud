package com.njydsz.common.json.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多态子类型注解
 *
 * <p>定义单个子类型的映射关系，与 {@link JsonSubTypes} 配合使用。
 *
 * <p><b>参数说明：</b>
 *
 * <ul>
 *   <li>value: 具体的子类
 *   <li>name: JSON 中 type 属性的值，用于识别该子类型
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface JsonSubType {

  /**
   * 子类型类
   *
   * @return 具体的子类类型
   */
  Class<?> value();

  /**
   * 类型标识名称
   *
   * <p>JSON 中 type 属性的值，用于反序列化时识别具体子类
   *
   * @return 类型标识名称
   */
  String name();
}
