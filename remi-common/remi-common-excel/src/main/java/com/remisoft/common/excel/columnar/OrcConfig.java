package com.remisoft.common.excel.columnar;

import java.util.Objects;

/**
 * ORC 写入/读取配置。
 *
 * <p>在 {@link ColumnarConfig} 基础上扩展 ORC 特有参数：
 * Stripe 大小（{@code stripeSize}）、索引粒度（{@code indexStride}）、写入策略（{@code writeStrategy}）。
 *
 * <h2>默认值</h2>
 * <ul>
 *   <li>stripeSize：64MB（ORC 官方推荐）</li>
 *   <li>indexStride：10000（每 10000 行建一次索引）</li>
 *   <li>writeStrategy：COMPRESSION（ORC 默认；COMPRESSION 优先压缩， SPEED 优先速度）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class OrcConfig extends ColumnarConfig {

    /** ORC 写入策略。 */
    public enum WriteStrategy {
        /** 优先压缩率（ORC 默认） */
        COMPRESSION,
        /** 优先写入速度（用于流式日志等场景） */
        SPEED
    }

    private long stripeSize = DEFAULT_STRIPE_SIZE;
    private int indexStride = 10_000;
    private WriteStrategy writeStrategy = WriteStrategy.COMPRESSION;
    private boolean overwrite = false;

    public long getStripeSize() {
        return stripeSize;
    }

    /**
     * 设置 Stripe 大小。
     *
     * <p>Stripe 是 ORC 的刷盘与读取并行单元：调大提升压缩率、减少元数据开销，
     * 但写入期缓冲占用同步升高；调小便于谓词下推跳读，代价是文件元数据膨胀。
     *
     * @param stripeSize Stripe 字节数，必须 &gt;= 1024
     * @throws IllegalArgumentException 当 {@code stripeSize < 1024} 时抛出
     */
    public void setStripeSize(long stripeSize) {
        if (stripeSize < 1024) {
            throw new IllegalArgumentException(
                    "stripeSize must be >= 1024 bytes, got " + stripeSize);
        }
        this.stripeSize = stripeSize;
    }

    public int getIndexStride() {
        return indexStride;
    }

    /**
     * 设置行索引粒度。
     *
     * <p>每累计该行数生成一组 min/max 统计索引，决定谓词下推的跳读精度：
     * 调小可跳过更多无关行但索引体积增大，调大则相反。
     *
     * @param indexStride 建索引的行间隔，必须 &gt;= 1
     * @throws IllegalArgumentException 当 {@code indexStride < 1} 时抛出
     */
    public void setIndexStride(int indexStride) {
        if (indexStride < 1) {
            throw new IllegalArgumentException(
                    "indexStride must be >= 1, got " + indexStride);
        }
        this.indexStride = indexStride;
    }

    public WriteStrategy getWriteStrategy() {
        return writeStrategy;
    }

    /**
     * 设置写入策略，在压缩率与写入吞吐之间取舍。
     *
     * @param writeStrategy 写入策略，不可为 {@code null}
     * @throws NullPointerException 当 {@code writeStrategy} 为 {@code null} 时抛出
     */
    public void setWriteStrategy(WriteStrategy writeStrategy) {
        this.writeStrategy = Objects.requireNonNull(writeStrategy, "writeStrategy must not be null");
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    /**
     * 设置目标文件已存在时是否覆盖。
     *
     * <p>默认 {@code false}，即遇到同名文件直接失败以防误删既有数据；
     * 仅在可重跑的幂等任务中才应置为 {@code true}。
     *
     * @param overwrite {@code true} 表示允许覆盖已存在文件
     */
    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    /**
     * 创建一份采用 ORC 官方推荐值的默认配置。
     *
     * @return 全新的可变配置实例，每次调用互不共享
     */
    public static OrcConfig defaults() {
        return new OrcConfig();
    }

    /**
     * 创建 ORC 配置构建器。
     *
     * @return 全新的构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "OrcConfig{stripeSize=" + stripeSize
                + ", indexStride=" + indexStride
                + ", strategy=" + writeStrategy
                + ", overwrite=" + overwrite
                + ", compression=" + getCompression()
                + ", dictionary=" + isEnableDictionary()
                + ", batchSize=" + getBatchSize()
                + "}";
    }

    /**
     * ORC 专用 Builder。
     */
    public static final class Builder extends ColumnarConfig.Builder {
        private long stripeSize = DEFAULT_STRIPE_SIZE;
        private int indexStride = 10_000;
        private WriteStrategy writeStrategy = WriteStrategy.COMPRESSION;
        private boolean overwrite = false;

        /**
         * 设置 Stripe 大小，即 ORC 的刷盘与读取并行单元。
         *
         * @param stripeSize Stripe 字节数，必须 &gt;= 1024
         * @return 当前构建器，便于链式调用
         * @throws IllegalArgumentException 当 {@code stripeSize < 1024} 时抛出
         */
        public Builder stripeSize(long stripeSize) {
            if (stripeSize < 1024) {
                throw new IllegalArgumentException(
                        "stripeSize must be >= 1024 bytes, got " + stripeSize);
            }
            this.stripeSize = stripeSize;
            return this;
        }

        /**
         * 设置行索引粒度，决定谓词下推的跳读精度。
         *
         * @param indexStride 建索引的行间隔，必须 &gt;= 1
         * @return 当前构建器，便于链式调用
         * @throws IllegalArgumentException 当 {@code indexStride < 1} 时抛出
         */
        public Builder indexStride(int indexStride) {
            if (indexStride < 1) {
                throw new IllegalArgumentException(
                        "indexStride must be >= 1, got " + indexStride);
            }
            this.indexStride = indexStride;
            return this;
        }

        /**
         * 设置写入策略，在压缩率与写入吞吐之间取舍。
         *
         * @param writeStrategy 写入策略，不可为 {@code null}
         * @return 当前构建器，便于链式调用
         * @throws NullPointerException 当 {@code writeStrategy} 为 {@code null} 时抛出
         */
        public Builder writeStrategy(WriteStrategy writeStrategy) {
            this.writeStrategy = Objects.requireNonNull(writeStrategy, "writeStrategy must not be null");
            return this;
        }

        /**
         * 设置目标文件已存在时是否覆盖，默认 {@code false} 以防误删既有数据。
         *
         * @param overwrite {@code true} 表示允许覆盖已存在文件
         * @return 当前构建器，便于链式调用
         */
        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        /**
         * 构建 ORC 配置，同时拷贝父类的通用参数与 ORC 特有参数。
         *
         * @return 全新的配置实例，与构建器解耦，后续修改构建器不影响已构建对象
         */
        @Override
        public OrcConfig build() {
            OrcConfig cfg = new OrcConfig();
            cfg.batchSize = this.batchSize;
            cfg.compression = this.compression;
            cfg.enableDictionary = this.enableDictionary;
            cfg.withHeader = this.withHeader;
            cfg.stripeSize = this.stripeSize;
            cfg.indexStride = this.indexStride;
            cfg.writeStrategy = this.writeStrategy;
            cfg.overwrite = this.overwrite;
            return cfg;
        }
    }
}
