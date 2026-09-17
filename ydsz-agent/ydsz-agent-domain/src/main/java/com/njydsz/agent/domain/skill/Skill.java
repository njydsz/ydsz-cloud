package com.njydsz.agent.domain.skill;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Skill 注解（标记一个 Spring Bean 方法为可调用的 Skill）
 *
 * <p>标注在 Bean 方法上，Skill 注册中心会自动扫描并注册为 Skill 定义。
 * 适用于将业务能力封装为 Skill 供 Agent 调用。
 *
 * <pre>{@code
 * @Skill(
 *     skillCode = "project-progress",
 *     name = "查询项目进度",
 *     description = "查询指定项目的当前进度，包括完成率、延期任务数等",
 *     targetType = SkillExecutionTarget.LOCAL
 * )
 * public ProgressResult queryProgress(@SkillParam("projectId") String projectId) {
 *     return progressService.query(projectId);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Skill {

  /**
   * Skill 全局唯一编码。
   *
   * @return 编码（建议小写下划线）
   */
  String skillCode();

  /**
   * 可读名称。
   *
   * @return 名称
   */
  String name() default "";

  /**
   * 用途描述（告诉 LLM 这个 Skill 做什么）。
   *
   * @return 描述
   */
  String description() default "";

  /**
   * 执行目标类型。
   *
   * @return 执行目标（默认 LOCAL）
   */
  SkillExecutionTarget targetType() default SkillExecutionTarget.LOCAL;

  /**
   * 超时时间（秒）。
   *
   * @return 超时秒数（0 表示使用系统默认值）
   */
  int timeoutSeconds() default 30;
}
