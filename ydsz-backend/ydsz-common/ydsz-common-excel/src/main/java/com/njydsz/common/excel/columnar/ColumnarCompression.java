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

    public static ColumnarCompression defaultCodec() {
        return SNAPPY;
    }

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
