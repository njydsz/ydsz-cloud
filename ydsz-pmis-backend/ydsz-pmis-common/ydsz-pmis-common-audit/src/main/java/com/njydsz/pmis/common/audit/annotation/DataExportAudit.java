package com.njydsz.pmis.common.audit.annotation;

import java.lang.annotation.*;

/**
 * 数据导出审计注解。
 *
 * <p>标注在 Controller 方法上，专门记录数据导出操作的行为日志。
 *
 * <p><b>注意：</b>该注解当前未实现对应的 AOP 切面，
 * 请使用 {@link Audit} 注解并设置 {@code type = AuditType.DATA, action = AuditAction.EXPORT} 替代。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @DataExportAudit(module = "项目管理", action = "导出报表", bizType = "REPORT")
 * @GetMapping("/download")
 * public void download(@RequestParam String type, HttpServletResponse response) { ... }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated 该注解当前未实现对应的 AOP 切面，
 *             请使用 {@link Audit} 注解替代，设置 {@code type = AuditType.DATA}。
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataExportAudit {

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
}
