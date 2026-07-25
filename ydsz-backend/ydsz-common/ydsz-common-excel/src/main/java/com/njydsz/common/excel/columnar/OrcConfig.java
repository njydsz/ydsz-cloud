package com.njydsz.common.excel.columnar;

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
 * @author ydsz-team
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

    public void setWriteStrategy(WriteStrategy writeStrategy) {
        this.writeStrategy = Objects.requireNonNull(writeStrategy, "writeStrategy must not be null");
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public static OrcConfig defaults() {
        return new OrcConfig();
    }

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

        public Builder stripeSize(long stripeSize) {
            if (stripeSize < 1024) {
                throw new IllegalArgumentException(
                        "stripeSize must be >= 1024 bytes, got " + stripeSize);
            }
            this.stripeSize = stripeSize;
            return this;
        }

        public Builder indexStride(int indexStride) {
            if (indexStride < 1) {
                throw new IllegalArgumentException(
                        "indexStride must be >= 1, got " + indexStride);
            }
            this.indexStride = indexStride;
            return this;
        }

        public Builder writeStrategy(WriteStrategy writeStrategy) {
            this.writeStrategy = Objects.requireNonNull(writeStrategy, "writeStrategy must not be null");
            return this;
        }

        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

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
