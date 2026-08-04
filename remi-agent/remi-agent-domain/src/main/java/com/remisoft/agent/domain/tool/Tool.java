package com.remisoft.agent.domain.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具注解（标记一个方法为 LLM 可调用的工具）
 *
 * <p>标注在 Spring Bean 的 public 方法上，工具注册中心会自动扫描并注册。
 *
 * <pre>{@code
 * @Tool(description = "查询项目进度信息")
 * public ProjectProgressVO getProjectProgress(@ToolParam("项目ID") String projectId) {
 *     return projectService.getProgress(projectId);
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {

    /**
     * 工具名称（默认使用方法名）
     */
    String name() default "";

    /**
     * 工具描述（告诉 LLM 这个工具做什么）
     */
    String description() default "";

    /**
     * 是否启用
     */
    boolean enabled() default true;
}
