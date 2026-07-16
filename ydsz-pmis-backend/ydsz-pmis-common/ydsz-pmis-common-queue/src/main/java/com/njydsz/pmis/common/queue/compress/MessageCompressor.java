package com.njydsz.pmis.common.queue.compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息压缩工具类
 *
 * <p>提供 GZIP 压缩/解压缩功能，用于大消息体在队列传输前的压缩处理。
 * 当消息体超过阈值时自动压缩，减少网络传输和存储开销。
 *
 * <p>压缩后的数据使用 Base64 编码，确保可以安全地作为字符串传输。
 * 压缩标记前缀 {@code "GZIP:"} 用于在接收端自动识别并解压。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public final class MessageCompressor {

    /** 压缩标记前缀，用于识别压缩后的消息 */
    public static final String COMPRESS_PREFIX = "GZIP:";

    /** 默认压缩阈值（4KB） */
    public static final int DEFAULT_COMPRESS_THRESHOLD = 4 * 1024;

    private MessageCompressor() {
    }

    /**
     * 如果消息体超过阈值则进行 GZIP 压缩
     *
     * @param data         原始字符串数据
     * @param thresholdBytes 压缩阈值（字节），小于此大小不压缩
     * @return 压缩后的字符串（带 GZIP: 前缀），或原始字符串（未超过阈值）
     */
    public static String compressIfNeeded(String data, int thresholdBytes) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        if (data.length() < thresholdBytes) {
            return data;
        }
        if (data.startsWith(COMPRESS_PREFIX)) {
            return data;
        }
        try {
            byte[] compressed = gzipCompress(data.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(compressed);
            String result = COMPRESS_PREFIX + encoded;
            if (log.isDebugEnabled()) {
                double ratio = (double) result.length() / data.length();
                log.debug("[MessageCompressor] 压缩完成，原始={}bytes, 压缩后={}bytes, 压缩率={}%",
                        data.length(), result.length(), String.format("%.1f", ratio * 100));
            }
            return result;
        } catch (IOException e) {
            log.warn("[MessageCompressor] 压缩失败，返回原始数据", e);
            return data;
        }
    }

    /**
     * 如果消息带有压缩标记则进行解压
     *
     * @param data 可能压缩的字符串数据
     * @return 解压后的原始字符串，或原始字符串（未压缩）
     */
    public static String decompressIfNeeded(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        if (!data.startsWith(COMPRESS_PREFIX)) {
            return data;
        }
        try {
            String encoded = data.substring(COMPRESS_PREFIX.length());
            byte[] compressed = Base64.getDecoder().decode(encoded);
            byte[] decompressed = gzipDecompress(compressed);
            return new String(decompressed, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[MessageCompressor] 解压失败，返回原始数据", e);
            return data;
        }
    }

    /**
     * 检查消息是否已被压缩
     *
     * @param data 消息字符串
     * @return true 如果消息已被压缩
     */
    public static boolean isCompressed(String data) {
        return data != null && data.startsWith(COMPRESS_PREFIX);
    }

    private static byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    private static byte[] gzipDecompress(byte[] compressed) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }
}
