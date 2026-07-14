package com.njydsz.pmis.common.util.id;

/**
 * 分布式 ID 生成器抽象 SPI（已废弃）
 *
 * <p>当前项目统一使用 {@link SnowflakeUtils}，此 SPI 层无外部实现和引用。
 * 保留仅用于未来扩展预留。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated 此 SPI 层无实际使用，请直接使用 {@link SnowflakeUtils}
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public interface IdGenerator {

    /**
     * 生成分布式 ID
     *
     * @return ID（String 形式）
     */
    String nextId();

    /**
     * 生成分布式 ID（long 形式）
     *
     * @return ID（long 形式，可能溢出为负数）
     */
    default long nextLongId() {
        return Long.parseUnsignedLong(nextId());
    }

    /**
     * 解析 ID 元信息
     *
     * @param id ID 字符串
     * @return ID 元信息（时间戳、节点 ID 等）
     */
    IdMeta parse(String id);

    /**
     * 生成器类型
     */
    default String type() {
        return getClass().getSimpleName();
    }
}
