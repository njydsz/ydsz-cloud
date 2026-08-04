package com.njydsz.common.excel.columnar;

import java.util.Objects;

/**
 * 列式存储通用配置基类。
 *
 * <p>封装 Parquet/ORC 共享的配置项（压缩、批大小、字典编码等）。
 * 子类 {@link ParquetConfig}、{@link OrcConfig} 各自扩展特有参数。
 *
 * <h2>关键参数说明</h2>
 * <ul>
 *   <li><b>batchSize</b>：读侧批处理大小（行）；ORC 写入 vector 大小默认 1024，写入侧实际受 rowGroup/stripeSize 影响</li>
 *   <li><b>compression</b>：压缩算法，默认 SNAPPY</li>
 *   <li><b>enableDictionary</b>：是否启用字典编码（对低基数列显著减小文件体积，默认 true）</li>
 *   <li><b>withHeader</b>：读取时第一行是否为表头（Parquet/ORC 无原生表头概念，需业务自行约定）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ColumnarConfig {

    /** 默认批处理行数，与 ORC VectorizedRowBatch 的默认容量保持一致 */
    public static final int DEFAULT_BATCH_SIZE = 1024;

    /** Parquet 默认 row group 大小（128MB），对齐 HDFS 块大小以避免跨块读放大 */
    public static final long DEFAULT_ROW_GROUP_SIZE = 128L * 1024L * 1024L; // 128MB

    /** Parquet 默认 page 大小（1MB），是压缩与编码的最小单元 */
    public static final int DEFAULT_PAGE_SIZE = 1024 * 1024; // 1MB

    /** ORC 默认 stripe 大小（64MB），决定单次刷盘的数据量与读取并行粒度 */
    public static final long DEFAULT_STRIPE_SIZE = 64L * 1024L * 1024L; // 64MB

    protected int batchSize = DEFAULT_BATCH_SIZE;
    protected ColumnarCompression compression = ColumnarCompression.defaultCodec();
    protected boolean enableDictionary = true;
    protected boolean withHeader = true;

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 设置批处理行数。
     *
     * <p>该值直接决定单批驻留堆内的行数，调大可减少批次切换开销但线性抬高内存占用，
     * 宽表场景建议下调。写入侧的实际落盘粒度仍由 rowGroup/stripe 大小主导。
     *
     * @param batchSize 批处理行数，必须 &gt;= 1
     * @throws IllegalArgumentException 当 {@code batchSize < 1} 时抛出
     */
    public void setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1, got " + batchSize);
        }
        this.batchSize = batchSize;
    }

    public ColumnarCompression getCompression() {
        return compression;
    }

    /**
     * 设置压缩算法。
     *
     * @param compression 压缩算法，不可为 {@code null}
     * @throws NullPointerException 当 {@code compression} 为 {@code null} 时抛出
     */
    public void setCompression(ColumnarCompression compression) {
        this.compression = Objects.requireNonNull(compression, "compression must not be null");
    }

    public boolean isEnableDictionary() {
        return enableDictionary;
    }

    public void setEnableDictionary(boolean enableDictionary) {
        this.enableDictionary = enableDictionary;
    }

    public boolean isWithHeader() {
        return withHeader;
    }

    public void setWithHeader(boolean withHeader) {
        this.withHeader = withHeader;
    }

    /**
     * 链式 builder 入口。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 默认配置。
     */
    public static ColumnarConfig defaults() {
        return new ColumnarConfig();
    }

    /**
     * 复制当前配置的通用字段到目标对象。
     */
    protected void copyCommonTo(ColumnarConfig target) {
        target.batchSize = this.batchSize;
        target.compression = this.compression;
        target.enableDictionary = this.enableDictionary;
        target.withHeader = this.withHeader;
    }

    @Override
    public String toString() {
        return "ColumnarConfig{batchSize=" + batchSize
                + ", compression=" + compression
                + ", dictionary=" + enableDictionary
                + ", withHeader=" + withHeader
                + "}";
    }

    /**
     * 通用 Builder（非泛型，子类继承并扩展自身参数）。
     */
    public static class Builder {
        protected int batchSize = DEFAULT_BATCH_SIZE;
        protected ColumnarCompression compression = ColumnarCompression.defaultCodec();
        protected boolean enableDictionary = true;
        protected boolean withHeader = true;

        /**
         * 设置批处理行数。
         *
         * <p>调大可减少批次切换开销但线性抬高堆内存占用，宽表场景建议下调。
         *
         * @param batchSize 批处理行数，必须 &gt;= 1
         * @return 当前构建器，便于链式调用
         * @throws IllegalArgumentException 当 {@code batchSize < 1} 时抛出
         */
        public Builder batchSize(int batchSize) {
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSize must be >= 1, got " + batchSize);
            }
            this.batchSize = batchSize;
            return this;
        }

        /**
         * 设置压缩算法。
         *
         * @param compression 压缩算法，不可为 {@code null}
         * @return 当前构建器，便于链式调用
         * @throws NullPointerException 当 {@code compression} 为 {@code null} 时抛出
         */
        public Builder compression(ColumnarCompression compression) {
            this.compression = Objects.requireNonNull(compression, "compression must not be null");
            return this;
        }

        /**
         * 设置是否启用字典编码。
         *
         * <p>低基数列（如状态码、地区名）开启后可显著压缩体积；
         * 高基数列（如订单号）开启反而增加字典维护开销，应显式关闭。
         *
         * @param enableDictionary {@code true} 表示启用字典编码
         * @return 当前构建器，便于链式调用
         */
        public Builder enableDictionary(boolean enableDictionary) {
            this.enableDictionary = enableDictionary;
            return this;
        }

        /**
         * 设置是否按表头语义处理首行。
         *
         * <p>Parquet/ORC 自带 schema、并无原生表头概念，此开关仅供上层导入导出场景
         * 与 Excel 行为对齐时使用。
         *
         * @param withHeader {@code true} 表示首行视为表头
         * @return 当前构建器，便于链式调用
         */
        public Builder withHeader(boolean withHeader) {
            this.withHeader = withHeader;
            return this;
        }

        /**
         * 构造通用 {@link ColumnarConfig}。子类重写以返回自身类型。
         */
        public ColumnarConfig build() {
            ColumnarConfig cfg = new ColumnarConfig();
            cfg.batchSize = this.batchSize;
            cfg.compression = this.compression;
            cfg.enableDictionary = this.enableDictionary;
            cfg.withHeader = this.withHeader;
            return cfg;
        }
    }
}
