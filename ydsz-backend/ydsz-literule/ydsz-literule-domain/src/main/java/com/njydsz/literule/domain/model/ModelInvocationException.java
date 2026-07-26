package com.njydsz.literule.domain.model;

import com.njydsz.common.exception.custom.InfrastructureException;

/**
 * 模型调用异常（P3-1 规则+模型融合）
 *
 * <p>当 {@link ModelInputRegistry#collectAllModelOutputs} 配置为
 * {@code fallbackOnError=false} 时，任一 {@link ModelInputProvider} 调用失败
 * （超时/异常/中断）将抛出本异常，中断规则引擎评估流程。
 *
 * <p>典型场景：业务要求"模型必须可用"，模型异常时不应继续评估规则，
 * 避免基于缺失模型输出的规则误判。
 *
 * <p>继承 {@link InfrastructureException}，纳入 common-exception 统一异常体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ModelInvocationException extends InfrastructureException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造模型调用异常
     *
     * @param message 异常信息
     * @param cause   原始异常
     */
    public ModelInvocationException(String message, Throwable cause) {
        super(message);
        this.initCause(cause);
    }

    /**
     * 构造模型调用异常
     *
     * @param message 异常信息
     */
    public ModelInvocationException(String message) {
        super(message);
    }
}
