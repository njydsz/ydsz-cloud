package com.njydsz.common.seata.api;

import java.util.concurrent.Callable;

/**
 * SAGA 事务步骤
 *
 * <p>定义 SAGA 模式中的一个正向操作及其对应的补偿操作。
 *
 * @param <T> 正向操作返回值类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SagaStep<T> {

    private final String name;
    private final Callable<T> forwardAction;
    private final Runnable compensation;

    private SagaStep(String name, Callable<T> forwardAction, Runnable compensation) {
        this.name = name;
        this.forwardAction = forwardAction;
        this.compensation = compensation;
    }

    /**
     * 创建一个带补偿的 SAGA 步骤
     *
     * @param name         步骤名称
     * @param forwardAction 正向操作
     * @param compensation  补偿操作
     * @param <T>           返回值类型
     * @return SAGA 步骤
     */
    public static <T> SagaStep<T> of(String name, Callable<T> forwardAction, Runnable compensation) {
        return new SagaStep<>(name, forwardAction, compensation);
    }

    /**
     * 创建一个不带补偿的 SAGA 步骤（最后一步或不可逆操作）
     *
     * @param name         步骤名称
     * @param forwardAction 正向操作
     * @param <T>           返回值类型
     * @return SAGA 步骤
     */
    public static <T> SagaStep<T> terminal(String name, Callable<T> forwardAction) {
        return new SagaStep<>(name, forwardAction, null);
    }

    public String getName() {
        return name;
    }

    public Callable<T> getForwardAction() {
        return forwardAction;
    }

    public Runnable getCompensation() {
        return compensation;
    }

    public boolean hasCompensation() {
        return compensation != null;
    }
}
