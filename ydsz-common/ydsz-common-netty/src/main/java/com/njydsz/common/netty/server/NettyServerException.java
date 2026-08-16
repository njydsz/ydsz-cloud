package com.njydsz.common.netty.server;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * Netty Server 运行时异常。
 *
 * <p>封装 Server 启动、关闭、端口绑定等过程中的各种错误（端口占用、启动超时、关闭异常等），
 * 便于调用方精确捕获和处理。
 *
 * <p>继承 {@link SysException}，错误码固定为 {@link CoreExceptionCode#NETWORK_ERROR}（B01055），
 * 表示基础设施层网络故障。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NettyServerException extends SysException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造 Netty Server 异常。
     *
     * @param message 错误消息
     */
    public NettyServerException(String message) {
        super(CoreExceptionCode.NETWORK_ERROR);
        setMessage(message);
    }

    /**
     * 构造 Netty Server 异常。
     *
     * @param message 错误消息
     * @param cause   根因
     */
    public NettyServerException(String message, Throwable cause) {
        super(CoreExceptionCode.NETWORK_ERROR, cause);
        setMessage(message);
    }
}
