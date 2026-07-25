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

    public void setWriteMode(WriteMode writeMode) {
        this.writeMode = Objects.requireNonNull(writeMode, "writeMode must not be null");
    }

    public static ParquetConfig defaults() {
        return new ParquetConfig();
    }

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

        public Builder rowGroupSize(long rowGroupSize) {
            if (rowGroupSize < 1024) {
                throw new IllegalArgumentException(
                        "rowGroupSize must be >= 1024 bytes, got " + rowGroupSize);
            }
            this.rowGroupSize = rowGroupSize;
            return this;
        }

        public Builder pageSize(int pageSize) {
            if (pageSize < 1024) {
                throw new IllegalArgumentException(
                        "pageSize must be >= 1024 bytes, got " + pageSize);
            }
            this.pageSize = pageSize;
            return this;
        }

        public Builder writeMode(WriteMode writeMode) {
            this.writeMode = Objects.requireNonNull(writeMode, "writeMode must not be null");
            return this;
        }

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
