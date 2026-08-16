package com.njydsz.common.base.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP 响应压缩配置属性（Web/App 共享）。
 *
 * <p>配置前缀：{@code ydsz.base.compression}
 *
 * <p>响应压缩通过 GZIP 减少网络传输量，对 API 响应和静态资源均有显著性能提升。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   base:
 *     compression:
 *       enabled: true
 *       min-response-size: 2048
 *       mime-types:
 *         - application/json
 *         - application/xml
 *         - text/html
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ydsz.base.compression")
public class BaseCompressionProperties {

    /**
     * 是否启用响应压缩。
     */
    private boolean enabled = true;

    /**
     * 最小响应体大小（字节），小于此值不压缩。
     *
     * <p>默认 2048 字节（2KB），避免小响应体压缩后反而变大。
     */
    private int minResponseSize = 2048;

    /**
     * 需要压缩的 MIME 类型列表。
     */
    private List<String> mimeTypes = Arrays.asList(
            "application/json",
            "application/xml",
            "text/html",
            "text/xml",
            "text/plain",
            "application/javascript",
            "application/x-javascript",
            "text/javascript",
            "text/css",
            "image/svg+xml"
    );

    /**
     * 排除压缩的 User-Agent 模式（正则表达式）。
     */
    private List<String> excludedUserAgents = Arrays.asList(
            "MSIE 6",
            "Mozilla/4"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinResponseSize() {
        return minResponseSize;
    }

    public void setMinResponseSize(int minResponseSize) {
        this.minResponseSize = minResponseSize;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public void setMimeTypes(List<String> mimeTypes) {
        this.mimeTypes = mimeTypes;
    }

    public List<String> getExcludedUserAgents() {
        return excludedUserAgents;
    }

    public void setExcludedUserAgents(List<String> excludedUserAgents) {
        this.excludedUserAgents = excludedUserAgents;
    }
}
