package com.njydsz.agent.domain.skill;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Skill 入参注解（配合 {@link Skill} 使用）
 *
 * <p>标注在 Skill 方法的参数上，声明参数名称和描述，用于构造 Skill 入参 Schema。
 *
 * <pre>{@code
 * @Skill(skillCode = "project-progress", description = "查询项目进度")
 * public ProgressResult queryProgress(
 *     @SkillParam(value = "projectId", description = "项目唯一标识") String projectId) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SkillParam {

  /**
   * 参数名称。
   *
   * @return 参数名
   */
  String value();

  /**
   * 参数描述。
   *
   * @return 描述（供 LLM 理解参数含义）
   */
  String description() default "";

  /**
   * 是否必填。
   *
   * @return true=必填
   */
  boolean required() default true;

  /**
   * 参数类型（JSON Schema type）。
   *
   * @return 类型字符串，默认 "string"
   */
  String type() default "string";
}
