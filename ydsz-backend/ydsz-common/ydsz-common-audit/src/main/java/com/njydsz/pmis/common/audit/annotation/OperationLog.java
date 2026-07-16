package com.njydsz.common.audit.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解（兼容旧 com.njydsz.common.annotation.OperationLog）。
 *
 * <p>标注在 Controller 方法上，记录用户操作行为日志。
 * 与 {@link Audit} 注解功能类似，但使用 String 类型参数，更灵活。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @OperationLog(module = "用户管理", action = "创建员工", bizType = "EMPLOYEE")
 * @PostMapping("/employees")
 * public Result create(@RequestBody EmployeeDTO dto) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 该注解当前未实现对应的 AOP 切面，
 *             请使用 {@link Audit} 注解替代。后续版本将补全 {@code saveDiff()} 差异审计能力。
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作所属模块名称。
     *
     * @return 模块名称
     */
    String module() default "";

    /**
     * 操作行为描述。
     *
     * @return 行为描述
     */
    String action() default "";

    /**
     * 业务类型标识，用于日志分类与过滤。
     *
     * @return 业务类型
     */
    String bizType() default "";

    /**
     * 是否将方法返回值记录到审计日志中。
     * <p>默认 false，仅在需要追踪创建/操作结果 ID 时开启。
     *
     * @return 是否保存返回值
     */
    boolean saveResult() default false;

    /**
     * 是否记录修改前后的差异字段。
     * <p>默认 false，开启后切面会对比更新前后的实体字段差异并记录。
     *
     * @return 是否保存差异
     */
    boolean saveDiff() default false;

    /**
     * 是否记录方法入参。
     * <p>默认 true，记录请求参数到审计日志。对于含敏感信息的接口可设为 false。
     *
     * @return 是否保存参数
     */
    boolean saveParams() default true;
}
