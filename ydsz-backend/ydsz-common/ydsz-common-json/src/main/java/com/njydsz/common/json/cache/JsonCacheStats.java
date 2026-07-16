package com.njydsz.common.json.cache;

import com.njydsz.common.json.asm.AsmBeanCodecGenerator;
import com.njydsz.common.json.provider.SerializationContext;

/**
 * Json 缓存统计信息。
 *
 * <p>统一暴露 Json 内部各缓存的运行时统计信息，
 * 供 Actuator HealthIndicator / Metrics / 日志诊断使用。
 *
 * <p><b>统计范围：</b>
 * <ul>
 *   <li>ASM 序列化器/反序列化器缓存（命中数/未命中数/大小）</li>
 *   <li>ASM 生成类数量 + 降级级别</li>
 *   <li>Bean 序列化器缓存大小</li>
 *   <li>SerializerCache 大小</li>
 *   <li>ThreadLocal 内存占用估计</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class JsonCacheStats {

    private JsonCacheStats() {
        throw new UnsupportedOperationException("JsonCacheStats is a utility class");
    }

    /**
     * 获取完整缓存统计信息。
     *
     * @return 格式化的统计信息字符串
     */
    public static String getStats() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("=== Json Cache Stats ===\n");
        sb.append("  ASM: ").append(AsmBeanCodecGenerator.getAsmStats()).append('\n');
        sb.append("  ASM Codec Cache: ").append(AsmCodecCache.getCacheSize()).append('\n');
        sb.append("  BeanSerializer Cache: ").append(BeanSerializerCache.size()).append('\n');
        sb.append("  ThreadLocal Memory (est): ").append(SerializationContext.estimateThreadLocalMemory()).append(" bytes\n");
        sb.append("=============================");
        return sb.toString();
    }

    /**
     * 获取 ASM 降级级别。
     *
     * @return ASM 降级级别
     */
    public static AsmBeanCodecGenerator.AsmLevel getAsmLevel() {
        return AsmBeanCodecGenerator.getAsmLevel();
    }

    /**
     * 获取 ASM 已生成类数量。
     *
     * @return 已生成的动态类数量，或 -1 表示已降级
     */
    public static int getAsmGeneratedCount() {
        if (getAsmLevel() == AsmBeanCodecGenerator.AsmLevel.REFLECTION) {
            return -1;
        }
        String stats = AsmBeanCodecGenerator.getAsmStats();
        // 解析 "Generated: 123/10000" 格式
        try {
            String generatedPart = stats.split("Generated: ")[1].split("/")[0];
            return Integer.parseInt(generatedPart.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 获取 ASM 序列化器缓存大小。
     *
     * @return 缓存大小
     */
    public static int getSerializerCacheSize() {
        return AsmCodecCache.getCacheSize().contains("SerializerCache: ")
                ? extractNumber(AsmCodecCache.getCacheSize(), "SerializerCache: ")
                : 0;
    }

    private static int extractNumber(String text, String prefix) {
        int start = text.indexOf(prefix);
        if (start < 0) return 0;
        start += prefix.length();
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(text.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
