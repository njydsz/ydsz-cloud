package com.njydsz.common.netty.ssl;

/**
 * Netty SSL/TLS 上下文创建异常。
 *
 * <p>封装 SSL 上下文初始化过程中的各种错误（证书加载失败、密码错误、格式不支持等），
 * 便于调用方精确捕获和处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NettySslException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** SSL 上下文类型（server / client） */
    private final String contextType;

    /**
     * 构造 Netty SSL 异常。
     *
     * @param contextType SSL 上下文类型（server / client）
     * @param message     错误消息
     */
    public NettySslException(String contextType, String message) {
        super(message);
        this.contextType = contextType;
    }

    /**
     * 构造 Netty SSL 异常。
     *
     * @param contextType SSL 上下文类型（server / client）
     * @param message     错误消息
     * @param cause       根因
     */
    public NettySslException(String contextType, String message, Throwable cause) {
        super(message, cause);
        this.contextType = contextType;
    }

    /**
     * 获取 SSL 上下文类型。
     *
     * @return 上下文类型（server / client）
     */
    public String getContextType() {
        return contextType;
    }
}
