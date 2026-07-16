package com.njydsz.pmis.common.socket.compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.njydsz.pmis.common.socket.config.WebSocketProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 消息压缩器（P2-3）。
 *
 * <p>对超过 {@link WebSocketProperties.Compression#getMinSize()} 的消息进行 GZIP 压缩，
 * 压缩后 Base64 编码以兼容 STOMP 文本协议。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class MessageCompressor {

    private final WebSocketProperties properties;

    /** 压缩标记前缀 */
    private static final String COMPRESSED_PREFIX = "GZIP:";

    /**
     * 根据配置决定是否压缩消息。
     *
     * @param data 原始消息字符串
     * @return 压缩后的消息（或原始消息）
     */
    public String compressIfNeeded(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        if (!properties.getCompression().isEnabled()) {
            return data;
        }
        if (data.length() < properties.getCompression().getMinSize()) {
            return data;
        }
        try {
            return compress(data);
        } catch (Exception e) {
            log.warn("[WS-Compress] 压缩失败, 返回原始消息: err={}", e.getMessage());
            return data;
        }
    }

    /**
     * 如果消息被压缩，则解压。
     *
     * @param data 消息字符串
     * @return 解压后的消息（或原始消息）
     */
    public String decompressIfNeeded(String data) {
        if (data == null || !data.startsWith(COMPRESSED_PREFIX)) {
            return data;
        }
        try {
            return decompress(data.substring(COMPRESSED_PREFIX.length()));
        } catch (Exception e) {
            log.warn("[WS-Compress] 解压失败: err={}", e.getMessage());
            return data;
        }
    }

    /**
     * GZIP 压缩 + Base64 编码。
     */
    private String compress(String data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    /**
     * Base64 解码 + GZIP 解压。
     */
    private String decompress(String base64) throws IOException {
        byte[] compressed = Base64.getDecoder().decode(base64);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             GZIPInputStream gzip = new GZIPInputStream(bis)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 判断消息是否已压缩。
     *
     * @param data 消息字符串
     * @return true 表示已压缩
     */
    public boolean isCompressed(String data) {
        return data != null && data.startsWith(COMPRESSED_PREFIX);
    }
}
