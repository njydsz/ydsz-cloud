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

    /** 模块名 */
    String module() default "";

    /** 操作 */
    String action() default "";

    /** 业务类型 */
    String bizType() default "";

    /** 是否保存请求参数 */
    boolean saveParams() default true;

    /** 是否保存响应结果 */
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
