package com.njydsz.pmis.message.server.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * P2-21: 消息压缩工具。
 *
 * <p>对大消息体（>1KB）进行 GZIP 压缩后再投递 MQ，减少网络带宽和存储成本。
 *
 * <p>压缩流程：
 * <ol>
 *   <li>判断消息体大小，超过阈值才压缩</li>
 *   <li>GZIP 压缩 + Base64 编码（确保 MQ 消息可序列化）</li>
 *   <li>添加压缩标记头 {@code X-Compressed: GZIP}</li>
 *   <li>消费端根据标记头自动解压</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
public final class MessageCompressor {

    private MessageCompressor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 压缩阈值（1KB） */
    private static final int COMPRESS_THRESHOLD = 1024;

    /** 压缩标记前缀 */
    private static final String COMPRESSED_PREFIX = "GZIP:";

    /**
     * 压缩消息体（如果超过阈值）。
     *
     * @param body 原始消息体
     * @return 压缩后的消息体（带前缀标记），或原始消息体（未超阈值）
     */
    public static String compressIfNeeded(String body) {
        if (body == null || body.length() < COMPRESS_THRESHOLD) {
            return body;
        }
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(bytes);
            }
            String compressed = Base64.getEncoder().encodeToString(baos.toByteArray());
            String result = COMPRESSED_PREFIX + compressed;
            log.debug("[Compressor] 压缩: original={}B compressed={}B ratio={}%",
                    bytes.length, result.length(), result.length() * 100 / bytes.length);
            return result;
        } catch (IOException e) {
            log.warn("[Compressor] 压缩失败,返回原始消息体: err={}", e.getMessage(), e);
            return body;
        }
    }

    /**
     * 解压消息体（如果带压缩标记）。
     *
     * @param body 消息体（可能带 GZIP: 前缀）
     * @return 解压后的原始消息体，或原始输入（无标记）
     */
    public static String decompressIfNeeded(String body) {
        if (body == null || !body.startsWith(COMPRESSED_PREFIX)) {
            return body;
        }
        try {
            String compressed = body.substring(COMPRESSED_PREFIX.length());
            byte[] bytes = Base64.getDecoder().decode(compressed);
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = gzip.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[Compressor] 解压失败,返回原始消息体: err={}", e.getMessage(), e);
            return body;
        }
    }

    /**
     * 检查消息体是否已压缩。
     *
     * @param body 消息体
     * @return true 表示已压缩
     */
    public static boolean isCompressed(String body) {
        return body != null && body.startsWith(COMPRESSED_PREFIX);
    }
}
