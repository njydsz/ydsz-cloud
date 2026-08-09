package com.njydsz.common.excel.columnar;

import java.util.Objects;

/**
 * Parquet 写入/读取配置。
 *
 * <p>在 {@link ColumnarConfig} 基础上扩展 Parquet 特有参数：
 * 行组大小（{@code rowGroupSize}）、页大小（{@code pageSize}）、写入模式（{@code writeMode}）。
 *
 * <h2>默认值</h2>
 * <ul>
 *   <li>rowGroupSize：128MB（Parquet 官方推荐）</li>
 *   <li>pageSize：1MB</li>
 *   <li>writeMode：CREATE（文件不存在则创建，存在则抛异常；用 OVERWRITE 覆盖）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ParquetConfig extends ColumnarConfig {

    /** Parquet 写入模式。 */
    public enum WriteMode {
        /** 仅当文件不存在时创建，存在则抛异常（Parquet 默认） */
        CREATE,
        /** 始终覆盖已存在的文件 */
        OVERWRITE
    }

    private long rowGroupSize = DEFAULT_ROW_GROUP_SIZE;
    private int pageSize = DEFAULT_PAGE_SIZE;
    private WriteMode writeMode = WriteMode.CREATE;

    public long getRowGroupSize() {
        return rowGroupSize;
    }

    /**
     * 设置行组大小。
     *
     * <p>行组是 Parquet 的读取并行单元，默认 128MB 以对齐 HDFS 块大小、避免跨块读放大。
     * 写入期整个行组需在堆内缓冲后才刷盘，调大将显著抬高写入内存峰值。
     *
     * @param rowGroupSize 行组字节数，必须 &gt;= 1024
     * @throws IllegalArgumentException 当 {@code rowGroupSize < 1024} 时抛出
     */
    public void setRowGroupSize(long rowGroupSize) {
        if (rowGroupSize < 1024) {
            throw new IllegalArgumentException(
                    "rowGroupSize must be >= 1024 bytes, got " + rowGroupSize);
        }
        this.rowGroupSize = rowGroupSize;
    }

    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置页大小。
     *
     * <p>页是 Parquet 压缩与编码的最小单元，也是解压时的最小读取粒度。
     * 调小有利于点查跳读，但会增加页头元数据占比并降低整体压缩率。
     *
     * @param pageSize 页字节数，必须 &gt;= 1024
     * @throws IllegalArgumentException 当 {@code pageSize < 1024} 时抛出
     */
    public void setPageSize(int pageSize) {
        if (pageSize < 1024) {
            throw new IllegalArgumentException(
                    "pageSize must be >= 1024 bytes, got " + pageSize);
        }
        this.pageSize = pageSize;
    }

    public WriteMode getWriteMode() {
        return writeMode;
    }

    /**
     * 设置写入模式。
     *
     * <p>默认 {@link WriteMode#CREATE}，同名文件已存在时直接失败以防误删既有数据；
     * 仅在可重跑的幂等任务中才应改用 {@link WriteMode#OVERWRITE}。
     *
     * @param writeMode 写入模式，不可为 {@code null}
     * @throws NullPointerException 当 {@code writeMode} 为 {@code null} 时抛出
     */
    public void setWriteMode(WriteMode writeMode) {
        this.writeMode = Objects.requireNonNull(writeMode, "writeMode must not be null");
    }

    /**
     * 创建一份采用 Parquet 官方推荐值的默认配置。
     *
     * @return 全新的可变配置实例，每次调用互不共享
     */
    public static ParquetConfig defaults() {
        return new ParquetConfig();
    }

    /**
     * 创建 Parquet 配置构建器。
     *
     * @return 全新的构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "ParquetConfig{rowGroupSize=" + rowGroupSize
                + ", pageSize=" + pageSize
                + ", writeMode=" + writeMode
                + ", compression=" + getCompression()
                + ", dictionary=" + isEnableDictionary()
                + ", batchSize=" + getBatchSize()
                + "}";
    }

    /**
     * Parquet 专用 Builder。
     */
    public static final class Builder extends ColumnarConfig.Builder {
        private long rowGroupSize = DEFAULT_ROW_GROUP_SIZE;
        private int pageSize = DEFAULT_PAGE_SIZE;
        private WriteMode writeMode = WriteMode.CREATE;

        /**
         * 设置行组大小，即 Parquet 的读取并行单元。
         *
         * <p>整个行组需在堆内缓冲后才刷盘，调大将显著抬高写入内存峰值。
         *
         * @param rowGroupSize 行组字节数，必须 &gt;= 1024
         * @return 当前构建器，便于链式调用
         * @throws IllegalArgumentException 当 {@code rowGroupSize < 1024} 时抛出
         */
        public Builder rowGroupSize(long rowGroupSize) {
            if (rowGroupSize < 1024) {
                throw new IllegalArgumentException(
                        "rowGroupSize must be >= 1024 bytes, got " + rowGroupSize);
            }
            this.rowGroupSize = rowGroupSize;
            return this;
        }

        /**
         * 设置页大小，即压缩编码与解压读取的最小单元。
         *
         * @param pageSize 页字节数，必须 &gt;= 1024
         * @return 当前构建器，便于链式调用
         * @throws IllegalArgumentException 当 {@code pageSize < 1024} 时抛出
         */
        public Builder pageSize(int pageSize) {
            if (pageSize < 1024) {
                throw new IllegalArgumentException(
                        "pageSize must be >= 1024 bytes, got " + pageSize);
            }
            this.pageSize = pageSize;
            return this;
        }

        /**
         * 设置写入模式，默认 {@link WriteMode#CREATE} 以防覆盖既有数据。
         *
         * @param writeMode 写入模式，不可为 {@code null}
         * @return 当前构建器，便于链式调用
         * @throws NullPointerException 当 {@code writeMode} 为 {@code null} 时抛出
         */
        public Builder writeMode(WriteMode writeMode) {
            this.writeMode = Objects.requireNonNull(writeMode, "writeMode must not be null");
            return this;
        }

        /**
         * 构建 Parquet 配置，同时拷贝父类的通用参数与 Parquet 特有参数。
         *
         * @return 全新的配置实例，与构建器解耦，后续修改构建器不影响已构建对象
         */
        @Override
        public ParquetConfig build() {
            ParquetConfig cfg = new ParquetConfig();
            cfg.batchSize = this.batchSize;
            cfg.compression = this.compression;
            cfg.enableDictionary = this.enableDictionary;
            cfg.withHeader = this.withHeader;
            cfg.rowGroupSize = this.rowGroupSize;
            cfg.pageSize = this.pageSize;
            cfg.writeMode = this.writeMode;
            return cfg;
        }
    }
}
