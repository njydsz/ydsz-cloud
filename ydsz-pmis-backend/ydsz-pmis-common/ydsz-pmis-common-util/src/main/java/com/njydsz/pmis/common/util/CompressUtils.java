package com.njydsz.pmis.common.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 压缩工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class CompressUtils {

    private static final int BUFFER_SIZE = 8192;

    private CompressUtils() {
    }

    /**
     * GZIP 压缩
     *
     * @param data 原始数据
     * @return 压缩后的数据
     */
    public static byte[] gzipCompress(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("GZIP compress failed", e);
        }
    }

    /**
     * GZIP 解压
     *
     * @param compressed 压缩数据
     * @return 解压后的数据
     */
    public static byte[] gzipDecompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return compressed;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(compressed);
             GZIPInputStream gzip = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = gzip.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("GZIP decompress failed", e);
        }
    }

    /**
     * GZIP 压缩字符串
     *
     * @param str 原始字符串
     * @return 压缩后的 Base64 字符串
     */
    public static String compressString(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        byte[] compressed = gzipCompress(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64Utils.encode(compressed);
    }

    /**
     * GZIP 解压字符串
     *
     * @param compressedStr 压缩的 Base64 字符串
     * @return 解压后的字符串
     */
    public static String decompressString(String compressedStr) {
        if (compressedStr == null || compressedStr.isEmpty()) {
            return compressedStr;
        }
        byte[] compressed = Base64Utils.decodeToBytes(compressedStr);
        byte[] decompressed = gzipDecompress(compressed);
        return new String(decompressed, java.nio.charset.StandardCharsets.UTF_8);
    }
}
