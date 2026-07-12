package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据导出审计注解
 *
 * <p>标注在导出 Controller 方法上，由 DataExportAuditAspect 拦截并发布审计事件。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataExportAudit {

    /**
     * 导出模块名称（用于审计日志分类检索）
     * <p>示例：{@code "项目管理"}、{@code "合同管理"}
     */
    String module();

    /**
     * 导出动作描述（用于审计日志记录具体操作）
     * <p>示例：{@code "导出项目列表"}、{@code "导出合同明细"}
     */
    String action();

    /**
     * 业务类型编码（用于审计日志业务维度归类）
     * <p>示例：{@code "PROJECT"}、{@code "CONTRACT"}
     */
    String bizType() default "";
}
