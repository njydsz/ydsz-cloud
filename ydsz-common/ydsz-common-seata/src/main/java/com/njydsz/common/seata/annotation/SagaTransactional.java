package com.njydsz.common.seata.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SAGA 分布式事务类级注解 — 标记服务类为 SAGA 事务编排入口。
 *
 * <p>标注了 {@link SagaTransactional} 的 Service 类中的 {@link SagaStep} 方法
 * 会被 SagaManager 自动扫描并编排执行。
 *
 * <p><b>设计状态：</b>能力储备 — SagaManager 执行器和 SAGA 状态机实现待 TODO。
 *
 * <p>规范引用：YDIZ-TX-003 (P1) TCC/SAGA 模式 try/confirm/cancel 必须幂等
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SagaTransactional {

    /** SAGA 事务名称（建议格式："服务名-流程名"，如 "order-create-saga"） */
    String name();

    /**
     * SAGA 事务超时时间（毫秒，默认 120000ms = 2 分钟）。
     *
     * <p>SAGA 为长事务模式，超时较 AT 模式更长。
     */
    int timeoutMillis() default 120000;
}
