package com.njydsz.common.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Web 端请求体缓存配置属性
 *
 * <p>配置前缀：{@code ydsz.web.content-cache}
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>{@code
 * ydsz:
 *   web:
 *     content-cache:
 *       max-size: 2097152  # 2MB，超过部分不缓存（不会 OOM）
 * }</pre>
 *
 * @author ydsz-team
 * @see com.njydsz.common.web.filter.ContentCachingFilter
 */
@Data
@ConfigurationProperties(prefix = "ydsz.web.content-cache")
public class WebContentCacheProperties {

    /**
     * 最大缓存字节数
     *
     * <p>默认 2MB（2097152 字节）。超过此大小的请求体不会被缓存，
     * 但请求仍正常处理。主要用于防止大文件上传场景下的 OOM。
     */
    private int maxSize = 2097152;
}
