package com.njydsz.common.excel.columnar;

import java.util.Locale;

/**
 * 列式存储压缩编解码器。
 *
 * <p>Parquet/ORC 通用压缩策略枚举。Parquet 侧映射 {@code parquet.hadoop.metadata.CompressionCodecName}，
 * ORC 侧映射 {@code org.apache.orc.CompressionKind}。默认采用 SNAPPY（平衡压缩率与 CPU 开销）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ColumnarCompression {

    /** 不压缩（最快，体积最大，仅调试用） */
    NONE,
    /** Snappy 压缩（默认，平衡压缩率与速度） */
    SNAPPY,
    /** Gzip 压缩（压缩率高，CPU 较重） */
    GZIP,
    /** LZ4 压缩（速度优于 Snappy，压缩率略低） */
    LZ4,
    /** Zstandard 压缩（高压缩率，可配置 level） */
    ZSTD;

    /**
     * 返回默认的压缩编解码器。
     *
     * <p>SNAPPY 在压缩率与 CPU 开销之间取得平衡，作为未显式指定时的兜底值。
     *
     * @return 默认压缩策略 {@link #SNAPPY}
     */
    public static ColumnarCompression defaultCodec() {
        return SNAPPY;
    }

    /**
     * 将字符串形式的编解码器名解析为枚举。
     *
     * <p>名称大小写不敏感（内部统一转大写）；{@code null} 或无法识别的名称
     * 均降级返回 {@link #defaultCodec()}，保证解析过程永不抛异常。
     *
     * @param name 编解码器名称，可为 {@code null}
     * @return 对应的压缩枚举；无法解析时返回默认值 {@link #SNAPPY}
     */
    public static ColumnarCompression fromName(String name) {
        if (name == null) {
            return defaultCodec();
        }
        try {
            return ColumnarCompression.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return defaultCodec();
        }
    }
}
