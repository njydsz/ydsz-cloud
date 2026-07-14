package com.njydsz.pmis.common.util.id;

/**
 * 分布式 ID 生成器抽象 SPI
 *
 * <p>支持多种 ID 生成策略：Snowflake（雪花算法）/ Segment（号段模式）/ UUID / NanoId。
 * 业务方通过 {@link IdGeneratorFactory} 选型。</p>
 *
 * <p>大厂实践：
 * <ul>
 *   <li>美团 Leaf：Snowflake + Segment 双模式</li>
 *   <li>百度 UidGenerator：Snowflake 变种</li>
 *   <li>Twitter Snowflake：经典算法</li>
 *   <li>滴滴 TinyID：号段模式</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.5.0
 */
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
