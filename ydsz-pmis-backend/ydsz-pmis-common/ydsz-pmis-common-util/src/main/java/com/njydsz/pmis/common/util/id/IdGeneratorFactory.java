package com.njydsz.pmis.common.util.id;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IdGenerator 工厂
 *
 * <p>业务方可通过 {@link #setDefault(IdGenerator)} 注入 ID 生成器。
 * 内部默认使用 {@link SnowflakeUtils} 单例。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.5.0
 */
public final class IdGeneratorFactory {

    private static final AtomicReference<IdGenerator> DEFAULT = new AtomicReference<>(
            new SnowflakeUtilsIdAdapter(SnowflakeUtils.getInstance()));

    private IdGeneratorFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static IdGenerator getDefault() {
        return DEFAULT.get();
    }

    public static void setDefault(IdGenerator generator) {
        Objects.requireNonNull(generator, "IdGenerator must not be null");
        DEFAULT.set(generator);
    }

    /**
     * SnowflakeUtils 到 IdGenerator 的适配器
     */
    private static final class SnowflakeUtilsIdAdapter implements IdGenerator {
        private final SnowflakeUtils snowflake;

        SnowflakeUtilsIdAdapter(SnowflakeUtils snowflake) {
            this.snowflake = snowflake;
        }

        @Override
        public String nextId() {
            return String.valueOf(snowflake.nextId());
        }

        @Override
        public long nextLongId() {
            return snowflake.nextId();
        }

        @Override
        public IdMeta parse(String id) {
            long longId = Long.parseUnsignedLong(id);
            return new IdMeta(
                    SnowflakeUtils.parseTimestamp(longId),
                    SnowflakeUtils.parseWorkerId(longId),
                    SnowflakeUtils.parseDatacenterId(longId),
                    SnowflakeUtils.parseSequence(longId));
        }

        @Override
        public String type() {
            return "Snowflake";
        }
    }
}
