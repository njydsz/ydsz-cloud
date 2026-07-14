package com.njydsz.pmis.common.feign.compress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import com.njydsz.pmis.common.util.string.StringUtils;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * GZIP 请求压缩拦截器。
 *
 * <p>对 Feign 请求体进行 GZIP 压缩，减少网络传输量，提升性能。
 *
 * <p><b>功能特性：</b>
 * <ul>
 *   <li>自动检测并压缩包含请求体的请求</li>
 *   <li>添加 {@code Content-Encoding: gzip} 请求头</li>
 *   <li>可配置压缩阈值（小于该阈值的请求不压缩）</li>
 *   <li>可配置排除的 Content-Type</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <ul>
 *   <li>通过 {@code ydsz.feign.compress.enabled=true} 启用</li>
 *   <li>需要服务端支持 GZIP 解压缩</li>
 * </ul>
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>{@code
 * ydsz:
 *   feign:
 *     compress:
 *       enabled: true
 *       min-size: 1024
 *       excluded-content-types:
 *         - application/octet-stream
 *         - image/*
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class GzipRequestCompressInterceptor implements RequestInterceptor {

    /** Content-Encoding 请求头名称 */
    private static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
    /** gzip 编码值 */
    private static final String VALUE_GZIP = "gzip";
    /** Content-Type 请求头名称 */
    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    /** 最小压缩阈值（字节），小于此值的请求体不压缩 */
    private final int minCompressSize;
    /** 排除压缩的 Content-Type 列表 */
    private final String[] excludedContentTypes;

    /**
     * 使用默认压缩阈值（1024 字节）构造压缩拦截器。
     */
    public GzipRequestCompressInterceptor() {
        this(1024);
    }

    /**
     * 使用自定义压缩阈值构造压缩拦截器。
     *
     * @param minCompressSize 最小压缩阈值（字节）
     */
    public GzipRequestCompressInterceptor(int minCompressSize) {
        this(minCompressSize, new String[]{});
    }

    /**
     * 使用自定义压缩阈值和排除列表构造压缩拦截器。
     *
     * @param minCompressSize      最小压缩阈值（字节）
     * @param excludedContentTypes 排除压缩的 Content-Type 列表，支持通配符（如 image/*）
     */
    public GzipRequestCompressInterceptor(int minCompressSize, String[] excludedContentTypes) {
        this.minCompressSize = minCompressSize;
        this.excludedContentTypes = excludedContentTypes != null ? excludedContentTypes : new String[]{};
    }

    /**
     * 对请求体进行 GZIP 压缩处理。
     *
     * <p>当请求体大小超过阈值且 Content-Type 不在排除列表中时，
     * 对请求体进行 GZIP 压缩并添加 Content-Encoding: gzip 请求头。
     *
     * @param requestTemplate Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        if (requestTemplate.body() == null) {
            return;
        }

        byte[] body = requestTemplate.body();
        if (body.length < minCompressSize) {
            return;
        }

        String contentType = requestTemplate.headers().get(HEADER_CONTENT_TYPE) != null
                ? requestTemplate.headers().get(HEADER_CONTENT_TYPE).iterator().next()
                : "";

        if (isExcludedContentType(contentType)) {
            return;
        }

        byte[] compressedBody = compress(body);
        if (compressedBody != null && compressedBody.length < body.length) {
            requestTemplate.body(compressedBody, StandardCharsets.UTF_8);
            requestTemplate.header(HEADER_CONTENT_ENCODING, VALUE_GZIP);
        }
    }

    private boolean isExcludedContentType(String contentType) {
        if (StringUtils.isEmpty(contentType)) {
            return false;
        }
        for (String excluded : excludedContentTypes) {
            if (excluded.contains("*")) {
                String prefix = excluded.substring(0, excluded.indexOf("*"));
                if (contentType.startsWith(prefix)) {
                    return true;
                }
            } else if (contentType.equals(excluded)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用 GZIP 算法压缩字节数据。
     *
     * @param data 待压缩的原始字节数组
     * @return 压缩后的字节数组，压缩失败或输入为空时返回 null
     */
    private byte[] compress(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data);
            gzipOut.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
