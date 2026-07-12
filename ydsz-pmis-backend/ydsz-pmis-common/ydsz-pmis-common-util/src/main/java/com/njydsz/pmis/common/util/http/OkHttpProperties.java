package com.njydsz.pmis.common.util.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OkHttp 配置属性
 *
 * <p>配置前缀：{@code ydsz.util.okhttp}
 *
 * <p>提供 OkHttp 客户端的连接池、超时时间等配置。
 * 通过 {@link UtilAutoConfiguration#okHttpClient(OkHttpProperties)} 自动装配到 Spring 容器。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ydsz:
 *   util:
 *     okhttp:
 *       max-idle-connections: 50
 *       keep-alive-duration: 5
 *       connect-timeout: 5
 *       read-timeout: 30
 *       write-timeout: 30
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ydsz.util.okhttp")
public class OkHttpProperties {

    /**
     * 最大空闲连接数
     *
     * <p>连接池中允许的最大空闲连接数量。默认 50。
     */
    private int maxIdleConnections = 50;

    /**
     * 连接保持时间（分钟）
     *
     * <p>空闲连接在连接池中的存活时间，超过此时间的连接将被回收。
     * 默认 5 分钟。
     */
    private long keepAliveDuration = 5;

    /**
     * 连接超时时间（秒）
     *
     * <p>建立 TCP 连接的超时时间。默认 5 秒。
     */
    private long connectTimeout = 5;

    /**
     * 读取超时时间（秒）
     *
     * <p>从服务器读取响应的超时时间。默认 30 秒。
     */
    private long readTimeout = 30;

    /**
     * 写入超时时间（秒）
     *
     * <p>向服务器发送请求体的超时时间。默认 30 秒。
     */
    private long writeTimeout = 30;

    public int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    public void setMaxIdleConnections(int maxIdleConnections) {
        this.maxIdleConnections = maxIdleConnections;
    }

    public long getKeepAliveDuration() {
        return keepAliveDuration;
    }

    public void setKeepAliveDuration(long keepAliveDuration) {
        this.keepAliveDuration = keepAliveDuration;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    public long getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(long writeTimeout) {
        this.writeTimeout = writeTimeout;
    }
}
