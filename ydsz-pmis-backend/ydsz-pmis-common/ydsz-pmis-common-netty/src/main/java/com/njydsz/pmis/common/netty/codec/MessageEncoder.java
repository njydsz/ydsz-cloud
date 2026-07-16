package com.njydsz.pmis.common.netty.codec;

/**
 * 消息编码器接口 — 将业务对象编码为 Netty 可发送的字节流。
 *
 * @param <T> 业务消息类型
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface MessageEncoder<T> {

    /**
     * 将业务消息对象编码为字节数组。
     *
     * @param message 消息对象
     * @return 编码后的字节数组
     * @throws Exception 编码异常
     */
    byte[] encode(T message) throws Exception;
}
