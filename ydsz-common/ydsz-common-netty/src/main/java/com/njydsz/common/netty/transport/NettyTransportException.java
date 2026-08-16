package com.njydsz.common.netty.transport;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * Netty 传输层异常。
 *
 * <p>封装传输模式检测、EventLoop 创建等过程中的各种错误（原生传输不可用、线程创建失败等），
 * 便于调用方精确捕获和处理。
 *
 * <p>继承 {@link SysException}，错误码固定为 {@link CoreExceptionCode#NETWORK_ERROR}（B01055），
 * 表示基础设施层网络故障。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NettyTransportException extends SysException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造 Netty 传输层异常。
     *
     * @param message 错误消息
     */
    public NettyTransportException(String message) {
        super(CoreExceptionCode.NETWORK_ERROR);
        setMessage(message);
    }

    /**
     * 构造 Netty 传输层异常。
     *
     * @param message 错误消息
     * @param cause   根因
     */
    public NettyTransportException(String message, Throwable cause) {
        super(CoreExceptionCode.NETWORK_ERROR, cause);
        setMessage(message);
    }
}
