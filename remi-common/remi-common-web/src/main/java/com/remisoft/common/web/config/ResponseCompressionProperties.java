package com.remisoft.common.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * HTTP 响应压缩配置属性
 *
 * <p>基于 Spring Boot 内置的响应压缩功能，提供更合理的默认值和统一配置入口。
 * 支持 GZIP 压缩，减少网络传输量，提升性能。
 *
 * <p><b>配置示例：</b>
 * <pre>
 * remi:
 *   web:
 *     compression:
 *       enabled: true
 *       min-response-size: 2048
 *       mime-types:
 *         - application/json
 *         - application/xml
 *         - text/html
 *         - text/xml
 *         - text/plain
 *         - application/javascript
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "remi.web.compression")
public class ResponseCompressionProperties {

    /**
     * 是否启用响应压缩
     */
    private boolean enabled = true;

    /**
     * 最小响应体大小（字节），小于此值不压缩
     * <p>默认 2048 字节（2KB），避免小响应体压缩后反而变大
     */
    private int minResponseSize = 2048;

    /**
     * 需要压缩的 MIME 类型列表
     * <p>仅对匹配的响应类型进行压缩
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
     * 排除压缩的 User-Agent 模式（正则表达式）
     * <p>某些老旧浏览器或客户端不支持压缩，可通过此配置排除
     */
    private List<String> excludedUserAgents = Arrays.asList(
            "MSIE 6",  // IE6 不支持 gzip
            "Mozilla/4"  // Netscape 4 等老旧浏览器
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
