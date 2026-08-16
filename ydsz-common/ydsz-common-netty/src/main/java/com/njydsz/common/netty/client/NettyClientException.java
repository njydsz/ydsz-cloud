package com.njydsz.common.netty.client;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * Netty Client 运行时异常。
 *
 * <p>封装 Client 连接、发送、重连等过程中的各种错误（连接失败、发送失败、Channel 未就绪等），
 * 便于调用方精确捕获和处理。
 *
 * <p>继承 {@link SysException}，错误码固定为 {@link CoreExceptionCode#NETWORK_ERROR}（B01055），
 * 表示基础设施层网络故障。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NettyClientException extends SysException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造 Netty Client 异常。
     *
     * @param message 错误消息
     */
    public NettyClientException(String message) {
        super(CoreExceptionCode.NETWORK_ERROR);
        setMessage(message);
    }

    /**
     * 构造 Netty Client 异常。
     *
     * @param message 错误消息
     * @param cause   根因
     */
    public NettyClientException(String message, Throwable cause) {
        super(CoreExceptionCode.NETWORK_ERROR, cause);
        setMessage(message);
    }
}
