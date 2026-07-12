package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * <p>用于 Controller 方法上，由 OperationLogAspect 拦截，自动记录操作日志。
 *
 * <p>用法：
 * <pre>
 *   {@code @OperationLog(module = "用户管理", action = "创建用户", bizType = "USER")}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作所属模块名称（用于日志检索与分类）
     * <p>示例：{@code "用户管理"}、{@code "合同管理"}
     */
    String module() default "";

    /**
     * 操作动作描述（记录具体执行的操作）
     * <p>示例：{@code "创建用户"}、{@code "更新合同金额"}
     */
    String action() default "";

    /**
     * 业务类型编码（用于日志业务维度归类）
     * <p>示例：{@code "USER"}、{@code "CONTRACT"}
     */
    String bizType() default "";

    /**
     * 是否保存请求参数（默认 true）
     * <p>开启后会将方法入参序列化为 JSON 存入操作日志的 params 字段
     */
    boolean saveParams() default true;

    /**
     * 是否保存响应结果（默认 false）
     * <p>开启后会将方法返回值序列化为 JSON 存入操作日志的 result 字段
     */
    boolean saveResult() default false;

    /**
     * 是否保存变更前/后数据（diff 快照）
     *
     * <p>P1-5 修复：为 true 时，Aspect 会从 {@link com.njydsz.pmis.common.log.OperationLogContext}
     * 采集业务层设置的 beforeData/afterData，写入 pmis_operation_log.before_data/after_data。
     *
     * <p>业务层需在方法内通过 OperationLogContext.setBeforeData()/setAfterData() 设置快照。
     */
    boolean saveDiff() default false;

    /** 排除保存的字段（脱敏） */
    String[] excludeFields() default {"password", "oldPassword", "newPassword"};
}
