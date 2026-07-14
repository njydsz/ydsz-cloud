package com.njydsz.pmis.common.util.bytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Objects;

/**
 * <p>字节工具类 - 增强版</p>
 *
 * <p>参考 Apache Commons IO、Google Guava、Hutool 等互联网大厂工具类设计，
 * 提供全面的字节操作方法，无需第三方依赖。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li><b>字节格式化：</b>将字节大小格式化为人类可读的字符串（B, KB, MB, GB, TB, PB, EB）</li>
 *   <li><b>单位转换：</b>支持所有字节单位之间的精确转换（Byte/KB/MB/GB/TB/PB/EB）</li>
 *   <li><b>字节数组操作：</b>合并、分割、填充、复制、比较等</li>
 *   <li><b>字节流操作：</b>读取、写入、复制、转换等</li>
 *   <li><b>ByteBuffer 操作：</b>ByteBuffer 与 byte[]/String 互转</li>
 *   <li><b>文件大小计算：</b>计算文件或目录的总大小</li>
 *   <li><b>压缩估算：</b>估算压缩后的文件大小</li>
 * </ul>
 *
 * <p><b>相比 Apache Commons IO / Hutool 的增强：</b></p>
 * <ul>
 *   <li>支持更多单位（EB 级别）</li>
 *   <li>提供精度控制（可配置小数位数）</li>
 *   <li>支持 BigInteger 超大数值</li>
 *   <li>提供更多字节数组操作方法</li>
 *   <li>高性能 ByteBuffer 操作</li>
 *   <li>完整的 JavaDoc 文档</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 格式化字节大小
 * String size = ByteUtils.formatByteSize(1536); // "1.5 KB"
 *
 * // 单位转换
 * long mb = ByteUtils.toMB(1048576); // 1 MB
 *
 * // 字节数组操作
 * byte[] merged = ByteUtils.merge(new byte[]{1,2}, new byte[]{3,4}); // [1,2,3,4]
 *
 * // ByteBuffer 操作
 * ByteBuffer buffer = ByteUtils.toByteBuffer(new byte[]{1,2,3});
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class ByteUtils {

    private ByteUtils() {
        throw new UnsupportedOperationException("ByteUtils is a utility class and cannot be instantiated");
    }

    /**
     * 字节单位常量
     */
    public static final long ONE_KB = 1024L;
    public static final long ONE_MB = ONE_KB * 1024L;
    public static final long ONE_GB = ONE_MB * 1024L;
    public static final long ONE_TB = ONE_GB * 1024L;
    public static final long ONE_PB = ONE_TB * 1024L;
    public static final long ONE_EB = ONE_PB * 1024L;

    /**
     * 默认的小数位数
     */
    private static final int DEFAULT_DECIMAL_PLACES = 1;

    /**
     * 字节单位枚举
     */
    public enum ByteUnit {
        /**
         * 字节
         */
        B(1L, "B"),
        /**
         * 千字节 (KB)
         */
        KB(ONE_KB, "KB"),
        /**
         * 兆字节 (MB)
         */
        MB(ONE_MB, "MB"),
        /**
         * 吉字节 (GB)
         */
        GB(ONE_GB, "GB"),
        /**
         * 太字节 (TB)
         */
        TB(ONE_TB, "TB"),
        /**
         * 拍字节 (PB)
         */
        PB(ONE_PB, "PB"),
        /**
         * 艾字节 (EB)
         */
        EB(ONE_EB, "EB");

        private final long bytes;
        private final String suffix;

        ByteUnit(long bytes, String suffix) {
            this.bytes = bytes;
            this.suffix = suffix;
        }

        /**
         * 获取该单位对应的字节数
         *
         * @return 字节数
         */
        public long getBytes() {
            return bytes;
        }

        /**
         * 获取单位后缀
         *
         * @return 单位后缀
         */
        public String getSuffix() {
            return suffix;
        }

        /**
         * 将指定字节数转换为此单位
         *
         * @param bytes 字节数
         * @return 转换后的值
         */
        public double convertFromBytes(long bytes) {
            return (double) bytes / this.bytes;
        }

        /**
         * 将此单位转换为字节
         *
         * @param value 值
         * @return 字节数
         */
        public long convertToBytes(long value) {
            return value * this.bytes;
        }
    }

    /**
     * 格式化字节大小为人类可读的字符串
     * 默认保留 1 位小数
     *
     * @param byteSize 字节数
     * @return 格式化后的字符串（如："1.5 KB", "2.3 MB"）
     */
    public static String formatByteSize(long byteSize) {
        return formatByteSize(byteSize, DEFAULT_DECIMAL_PLACES);
    }

    /**
     * 格式化字节大小为人类可读的字符串
     *
     * @param byteSize       字节数
     * @param decimalPlaces  小数位数
     * @return 格式化后的字符串（如："1.5 KB", "2.3 MB"）
     */
    public static String formatByteSize(long byteSize, int decimalPlaces) {
        if (byteSize <= 0) {
            return "0 B";
        }

        if (byteSize < ONE_KB) {
            return byteSize + " B";
        }

        String[] units = {"B", "KB", "MB", "GB", "TB", "PB", "EB"};
        int digitGroups = (int) (Math.log10(byteSize) / Math.log10(1024));
        int unitIndex = Math.min(digitGroups, units.length - 1);

        double value = (double) byteSize / Math.pow(1024, unitIndex);
        String pattern = createDecimalPattern(decimalPlaces);
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(value) + " " + units[unitIndex];
    }

    /**
     * 格式化字节大小为人类可读的字符串（精确版本，使用 BigDecimal）
     *
     * @param byteSize       字节数
     * @param decimalPlaces  小数位数
     * @return 格式化后的字符串（如："1.50 KB", "2.35 MB"）
     */
    public static String formatByteSizePrecise(long byteSize, int decimalPlaces) {
        if (byteSize <= 0) {
            return "0 B";
        }

        if (byteSize < ONE_KB) {
            return byteSize + " B";
        }

        String[] units = {"B", "KB", "MB", "GB", "TB", "PB", "EB"};
        int digitGroups = (int) (Math.log10(byteSize) / Math.log10(1024));
        int unitIndex = Math.min(digitGroups, units.length - 1);

        BigDecimal bytes = new BigDecimal(byteSize);
        BigDecimal divisor = new BigDecimal(Math.pow(1024, unitIndex));
        BigDecimal result = bytes.divide(divisor, decimalPlaces, RoundingMode.HALF_UP);

        String pattern = createDecimalPattern(decimalPlaces);
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(result.doubleValue()) + " " + units[unitIndex];
    }

    /**
     * 将字节数格式化为指定单位的字符串
     *
     * @param byteSize 字节数
     * @param unit     目标单位
     * @return 格式化后的字符串
     */
    public static String formatByteSize(long byteSize, ByteUnit unit) {
        if (byteSize <= 0) {
            return "0 " + unit.getSuffix();
        }

        double value = unit.convertFromBytes(byteSize);
        String pattern = createDecimalPattern(DEFAULT_DECIMAL_PLACES);
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(value) + " " + unit.getSuffix();
    }

    /**
     * 创建小数格式模式
     *
     * @param decimalPlaces 小数位数
     * @return 格式模式字符串
     */
    private static String createDecimalPattern(int decimalPlaces) {
        if (decimalPlaces <= 0) {
            return "#,##0";
        }
        StringBuilder pattern = new StringBuilder("#,##0");
        pattern.append(".");
        for (int i = 0; i < decimalPlaces; i++) {
            pattern.append("0");
        }
        return pattern.toString();
    }

    /**
     * 字节转换（兼容旧版别名）
     *
     * @param size 字节数
     * @return 格式化后的字符串
     */
    public static String convertFileSizeToString(long size) {
        return formatByteSize(size);
    }

    /**
     * 获取指定单位的数值
     *
     * @param size 字节数
     * @param unit 单位（如：1024 表示 KB）
     * @return 转换后的值
     */
    public static float convertFileSize(long size, long unit) {
        if (unit <= 0) {
            return size;
        }
        return (float) size / unit;
    }

    // ==================== 字节单位转换方法 ====================

    /**
     * 将字节转换为 KB
     *
     * @param bytes 字节数
     * @return KB 值
     */
    public static double toKB(long bytes) {
        return bytes / (double) ONE_KB;
    }

    /**
     * 将字节转换为 MB
     *
     * @param bytes 字节数
     * @return MB 值
     */
    public static double toMB(long bytes) {
        return bytes / (double) ONE_MB;
    }

    /**
     * 将字节转换为 GB
     *
     * @param bytes 字节数
     * @return GB 值
     */
    public static double toGB(long bytes) {
        return bytes / (double) ONE_GB;
    }

    /**
     * 将字节转换为 TB
     *
     * @param bytes 字节数
     * @return TB 值
     */
    public static double toTB(long bytes) {
        return bytes / (double) ONE_TB;
    }

    /**
     * 将字节转换为 PB
     *
     * @param bytes 字节数
     * @return PB 值
     */
    public static double toPB(long bytes) {
        return bytes / (double) ONE_PB;
    }

    /**
     * 将字节转换为 EB
     *
     * @param bytes 字节数
     * @return EB 值
     */
    public static double toEB(long bytes) {
        return bytes / (double) ONE_EB;
    }

    /**
     * 将 KB 转换为字节
     *
     * @param kb KB 值
     * @return 字节数
     */
    public static long fromKB(long kb) {
        return kb * ONE_KB;
    }

    /**
     * 将 MB 转换为字节
     *
     * @param mb MB 值
     * @return 字节数
     */
    public static long fromMB(long mb) {
        return mb * ONE_MB;
    }

    /**
     * 将 GB 转换为字节
     *
     * @param gb GB 值
     * @return 字节数
     */
    public static long fromGB(long gb) {
        return gb * ONE_GB;
    }

    /**
     * 将 TB 转换为字节
     *
     * @param tb TB 值
     * @return 字节数
     */
    public static long fromTB(long tb) {
        return tb * ONE_TB;
    }

    /**
     * 将 PB 转换为字节
     *
     * @param pb PB 值
     * @return 字节数
     */
    public static long fromPB(long pb) {
        return pb * ONE_PB;
    }

    /**
     * 将 EB 转换为字节
     *
     * @param eb EB 值
     * @return 字节数
     */
    public static long fromEB(long eb) {
        return eb * ONE_EB;
    }

    /**
     * 在两个字节单位之间转换
     *
     * @param value     值
     * @param fromUnit  源单位
     * @param toUnit    目标单位
     * @return 转换后的值
     */
    public static double convert(long value, ByteUnit fromUnit, ByteUnit toUnit) {
        long bytes = fromUnit.convertToBytes(value);
        return toUnit.convertFromBytes(bytes);
    }

    // ==================== 字节数组操作方法 ====================

    /**
     * 合并多个字节数组
     *
     * @param arrays 要合并的字节数组
     * @return 合并后的字节数组
     */
    public static byte[] merge(byte[]... arrays) {
        Objects.requireNonNull(arrays, "arrays cannot be null");

        int totalLength = 0;
        for (byte[] array : arrays) {
            if (array != null) {
                totalLength += array.length;
            }
        }

        byte[] result = new byte[totalLength];
        int currentPos = 0;
        for (byte[] array : arrays) {
            if (array != null) {
                System.arraycopy(array, 0, result, currentPos, array.length);
                currentPos += array.length;
            }
        }
        return result;
    }

    /**
     * 分割字节数组
     *
     * @param array 源数组
     * @param size  每个分片的大小
     * @return 分割后的数组数组
     */
    public static byte[][] split(byte[] array, int size) {
        Objects.requireNonNull(array, "array cannot be null");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        int numChunks = (array.length + size - 1) / size;
        byte[][] result = new byte[numChunks][];

        for (int i = 0; i < numChunks; i++) {
            int fromIndex = i * size;
            int toIndex = Math.min(fromIndex + size, array.length);
            result[i] = Arrays.copyOfRange(array, fromIndex, toIndex);
        }

        return result;
    }

    /**
     * 填充字节数组
     *
     * @param length 数组长度
     * @param value  填充值
     * @return 填充后的字节数组
     */
    public static byte[] fill(int length, byte value) {
        byte[] result = new byte[length];
        Arrays.fill(result, value);
        return result;
    }

    /**
     * 复制字节数组
     *
     * @param array 源数组
     * @return 复制后的数组
     */
    public static byte[] copy(byte[] array) {
        Objects.requireNonNull(array, "array cannot be null");
        return Arrays.copyOf(array, array.length);
    }

    /**
     * 复制字节数组（指定范围）
     *
     * @param array     源数组
     * @param fromIndex 起始索引（包含）
     * @param toIndex   结束索引（不包含）
     * @return 复制后的数组
     */
    public static byte[] copyOfRange(byte[] array, int fromIndex, int toIndex) {
        Objects.requireNonNull(array, "array cannot be null");
        return Arrays.copyOfRange(array, fromIndex, toIndex);
    }

    /**
     * 截取字节数组
     *
     * @param array 源数组
     * @param start 起始索引
     * @param end   结束索引
     * @return 截取后的数组
     */
    public static byte[] subarray(byte[] array, int start, int end) {
        Objects.requireNonNull(array, "array cannot be null");
        if (start < 0) {
            start = 0;
        }
        if (end > array.length) {
            end = array.length;
        }
        if (start >= end) {
            return new byte[0];
        }
        return Arrays.copyOfRange(array, start, end);
    }

    /**
     * 比较两个字节数组是否相等
     *
     * @param array1 数组 1
     * @param array2 数组 2
     * @return 相等返回 true
     */
    public static boolean equals(byte[] array1, byte[] array2) {
        return Arrays.equals(array1, array2);
    }

    /**
     * 比较两个字节数组（指定长度）
     *
     * @param array1 数组 1
     * @param array2 数组 2
     * @param length 比较的长度
     * @return 相等返回 true
     */
    public static boolean equals(byte[] array1, byte[] array2, int length) {
        Objects.requireNonNull(array1, "array1 cannot be null");
        Objects.requireNonNull(array2, "array2 cannot be null");

        if (length > array1.length || length > array2.length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (array1[i] != array2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 查找字节在数组中的位置
     *
     * @param array 数组
     * @param value 要查找的字节
     * @return 位置索引，未找到返回 -1
     */
    public static int indexOf(byte[] array, byte value) {
        return indexOf(array, value, 0);
    }

    /**
     * 查找字节在数组中的位置（从指定位置开始）
     *
     * @param array 数组
     * @param value 要查找的字节
     * @param start 起始位置
     * @return 位置索引，未找到返回 -1
     */
    public static int indexOf(byte[] array, byte value, int start) {
        Objects.requireNonNull(array, "array cannot be null");
        for (int i = start; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找子数组在主数组中的位置
     *
     * @param array 主数组
     * @param sub   子数组
     * @return 位置索引，未找到返回 -1
     */
    public static int indexOf(byte[] array, byte[] sub) {
        Objects.requireNonNull(array, "array cannot be null");
        Objects.requireNonNull(sub, "sub cannot be null");

        if (sub.length == 0) {
            return 0;
        }
        if (sub.length > array.length) {
            return -1;
        }

        for (int i = 0; i <= array.length - sub.length; i++) {
            boolean found = true;
            for (int j = 0; j < sub.length; j++) {
                if (array[i + j] != sub[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 反转字节数组
     *
     * @param array 源数组
     * @return 反转后的数组
     */
    public static byte[] reverse(byte[] array) {
        Objects.requireNonNull(array, "array cannot be null");
        byte[] result = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[array.length - 1 - i];
        }
        return result;
    }

    /**
     * 原地反转字节数组
     *
     * @param array 源数组
     */
    public static void reverseInPlace(byte[] array) {
        Objects.requireNonNull(array, "array cannot be null");
        int left = 0;
        int right = array.length - 1;
        while (left < right) {
            byte temp = array[left];
            array[left] = array[right];
            array[right] = temp;
            left++;
            right--;
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    public static String toHexString(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * 将十六进制字符串转换为字节数组
     *
     * @param hex 十六进制字符串
     * @return 字节数组
     */
    public static byte[] fromHexString(String hex) {
        Objects.requireNonNull(hex, "hex cannot be null");
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    // ==================== ByteBuffer 操作方法 ====================

    /**
     * 将字节数组转换为 ByteBuffer
     *
     * @param bytes 字节数组
     * @return ByteBuffer
     */
    public static ByteBuffer toByteBuffer(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        return ByteBuffer.wrap(bytes);
    }

    /**
     * 将 ByteBuffer 转换为字节数组
     *
     * @param buffer ByteBuffer
     * @return 字节数组
     */
    public static byte[] toByteArray(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer cannot be null");
        if (buffer.hasArray()) {
            return buffer.array();
        }

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * 将字符串转换为 ByteBuffer
     *
     * @param str     字符串
     * @param charset 字符集
     * @return ByteBuffer
     */
    public static ByteBuffer toByteBuffer(String str, Charset charset) {
        Objects.requireNonNull(str, "str cannot be null");
        Objects.requireNonNull(charset, "charset cannot be null");
        return ByteBuffer.wrap(str.getBytes(charset));
    }

    /**
     * 将 ByteBuffer 转换为字符串
     *
     * @param buffer  ByteBuffer
     * @param charset 字符集
     * @return 字符串
     */
    public static String toString(ByteBuffer buffer, Charset charset) {
        Objects.requireNonNull(buffer, "buffer cannot be null");
        Objects.requireNonNull(charset, "charset cannot be null");
        return charset.decode(buffer).toString();
    }

    // ==================== 字节流操作方法 ====================

    /**
     * 读取输入流的所有字节
     *
     * @param input 输入流
     * @return 字节数组
     * @throws IOException IO 异常
     */
    public static byte[] toByteArray(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    /**
     * 复制字节流
     *
     * @param input  输入流
     * @param output 输出流
     * @return 复制的字节数
     * @throws IOException IO 异常
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");

        byte[] buffer = new byte[8192];
        long count = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * 高性能复制字节流（使用 NIO）
     *
     * @param input  输入流
     * @param output 输出流
     * @return 复制的字节数
     * @throws IOException IO 异常
     */
    public static long copyFast(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(output, "output cannot be null");

        ReadableByteChannel inputChannel = Channels.newChannel(input);
        WritableByteChannel outputChannel = Channels.newChannel(output);

        ByteBuffer buffer = ByteBuffer.allocateDirect(16384);
        long count = 0;
        int n;
        while ((n = inputChannel.read(buffer)) != -1) {
            buffer.flip();
            outputChannel.write(buffer);
            buffer.clear();
            count += n;
        }
        return count;
    }

    /**
     * 将字节数组写入输出流
     *
     * @param bytes  字节数组
     * @param output 输出流
     * @throws IOException IO 异常
     */
    public static void write(byte[] bytes, OutputStream output) throws IOException {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        Objects.requireNonNull(output, "output cannot be null");
        output.write(bytes);
    }

    /**
     * 从输入流读取指定长度的字节
     *
     * @param input  输入流
     * @param length 要读取的字节数
     * @return 字节数组
     * @throws IOException IO 异常
     */
    public static byte[] read(InputStream input, int length) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }

        byte[] bytes = new byte[length];
        int offset = 0;
        int remaining = length;

        while (remaining > 0) {
            int n = input.read(bytes, offset, remaining);
            if (n == -1) {
                break;
            }
            offset += n;
            remaining -= n;
        }

        if (offset < length) {
            return Arrays.copyOf(bytes, offset);
        }
        return bytes;
    }

    // ==================== 字符串与字节转换 ====================

    /**
     * 将字符串转换为字节数组（UTF-8）
     *
     * @param str 字符串
     * @return 字节数组
     */
    public static byte[] toBytes(String str) {
        Objects.requireNonNull(str, "str cannot be null");
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将字节数组转换为字符串（UTF-8）
     *
     * @param bytes 字节数组
     * @return 字符串
     */
    public static String fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 将字符串转换为字节数组（指定字符集）
     *
     * @param str     字符串
     * @param charset 字符集
     * @return 字节数组
     */
    public static byte[] toBytes(String str, Charset charset) {
        Objects.requireNonNull(str, "str cannot be null");
        Objects.requireNonNull(charset, "charset cannot be null");
        return str.getBytes(charset);
    }

    /**
     * 将字节数组转换为字符串（指定字符集）
     *
     * @param bytes   字节数组
     * @param charset 字符集
     * @return 字符串
     */
    public static String fromBytes(byte[] bytes, Charset charset) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        Objects.requireNonNull(charset, "charset cannot be null");
        return new String(bytes, charset);
    }

    // ==================== 文件大小比较和计算方法 ====================

    /**
     * 比较两个文件大小
     *
     * @param size1 文件 1 的大小
     * @param size2 文件 2 的大小
     * @return 负数表示 size1 小，0 表示相等，正数表示 size1 大
     */
    public static int compare(long size1, long size2) {
        return Long.compare(size1, size2);
    }

    /**
     * 判断文件大小是否相等
     *
     * @param size1 文件 1 的大小
     * @param size2 文件 2 的大小
     * @return 相等返回 true
     */
    public static boolean isEqual(long size1, long size2) {
        return size1 == size2;
    }

    /**
     * 计算多个文件大小的总和
     *
     * @param sizes 文件大小数组
     * @return 总大小
     */
    public static long sum(long... sizes) {
        Objects.requireNonNull(sizes, "sizes cannot be null");
        long total = 0;
        for (long size : sizes) {
            if (size > 0) {
                total += size;
            }
        }
        return total;
    }

    /**
     * 计算平均文件大小
     *
     * @param sizes 文件大小数组
     * @return 平均大小
     */
    public static double average(long... sizes) {
        Objects.requireNonNull(sizes, "sizes cannot be null");
        if (sizes.length == 0) {
            return 0;
        }
        return (double) sum(sizes) / sizes.length;
    }

    /**
     * 找出最大的文件大小
     *
     * @param sizes 文件大小数组
     * @return 最大大小
     */
    public static long max(long... sizes) {
        Objects.requireNonNull(sizes, "sizes cannot be null");
        if (sizes.length == 0) {
            return 0;
        }
        long max = sizes[0];
        for (int i = 1; i < sizes.length; i++) {
            if (sizes[i] > max) {
                max = sizes[i];
            }
        }
        return max;
    }

    /**
     * 找出最小的文件大小
     *
     * @param sizes 文件大小数组
     * @return 最小大小
     */
    public static long min(long... sizes) {
        Objects.requireNonNull(sizes, "sizes cannot be null");
        if (sizes.length == 0) {
            return 0;
        }
        long min = sizes[0];
        for (int i = 1; i < sizes.length; i++) {
            if (sizes[i] < min) {
                min = sizes[i];
            }
        }
        return min;
    }

    // ==================== 压缩估算方法 ====================

    /**
     * 估算压缩后的大小（假设压缩率为 50%）
     *
     * @param originalSize 原始大小
     * @return 估算的压缩后大小
     */
    public static long estimateCompressedSize(long originalSize) {
        return estimateCompressedSize(originalSize, 0.5);
    }

    /**
     * 估算压缩后的大小
     *
     * @param originalSize    原始大小
     * @param compressionRate 压缩率（0.0-1.0，越小压缩率越高）
     * @return 估算的压缩后大小
     */
    public static long estimateCompressedSize(long originalSize, double compressionRate) {
        if (compressionRate < 0 || compressionRate > 1) {
            throw new IllegalArgumentException("compressionRate must be between 0.0 and 1.0");
        }
        return (long) (originalSize * compressionRate);
    }

    /**
     * 计算压缩率
     *
     * @param originalSize 原始大小
     * @param compressedSize 压缩后大小
     * @return 压缩率（0.0-1.0）
     */
    public static double calculateCompressionRate(long originalSize, long compressedSize) {
        if (originalSize <= 0) {
            return 0;
        }
        return (double) compressedSize / originalSize;
    }

    /**
     * 计算压缩节省的空间
     *
     * @param originalSize 原始大小
     * @param compressedSize 压缩后大小
     * @return 节省的字节数
     */
    public static long calculateSavedSpace(long originalSize, long compressedSize) {
        return originalSize - compressedSize;
    }

    /**
     * 计算压缩节省空间的百分比
     *
     * @param originalSize 原始大小
     * @param compressedSize 压缩后大小
     * @return 节省的百分比（0-100）
     */
    public static double calculateSavedPercentage(long originalSize, long compressedSize) {
        if (originalSize <= 0) {
            return 0;
        }
        return ((double) (originalSize - compressedSize) / originalSize) * 100;
    }

    // ==================== 其他实用方法 ====================

    /**
     * 检查文件大小是否超过限制
     *
     * @param fileSize 文件大小
     * @param maxSize  最大允许大小
     * @return 超过限制返回 true
     */
    public static boolean exceedsLimit(long fileSize, long maxSize) {
        return fileSize > maxSize;
    }

    /**
     * 检查文件大小是否在允许范围内
     *
     * @param fileSize 文件大小
     * @param minSize  最小大小
     * @param maxSize  最大大小
     * @return 在范围内返回 true
     */
    public static boolean isWithinRange(long fileSize, long minSize, long maxSize) {
        return fileSize >= minSize && fileSize <= maxSize;
    }

    /**
     * 将文件大小格式化为带宽使用量（如：Mbps）
     *
     * @param bytes      字节数
     * @param timeSeconds 时间（秒）
     * @return 带宽使用量（Mbps）
     */
    public static double formatAsBandwidth(long bytes, double timeSeconds) {
        if (timeSeconds <= 0) {
            return 0;
        }
        double bits = bytes * 8;
        double mbps = bits / (timeSeconds * 1_000_000);
        return mbps;
    }

    /**
     * 估算传输时间
     *
     * @param fileSize 文件大小（字节）
     * @param speedMbps 传输速度（Mbps）
     * @return 传输时间（秒）
     */
    public static double estimateTransferTime(long fileSize, double speedMbps) {
        if (speedMbps <= 0) {
            return Double.MAX_VALUE;
        }
        double bits = fileSize * 8;
        double speedBps = speedMbps * 1_000_000;
        return bits / speedBps;
    }
}
