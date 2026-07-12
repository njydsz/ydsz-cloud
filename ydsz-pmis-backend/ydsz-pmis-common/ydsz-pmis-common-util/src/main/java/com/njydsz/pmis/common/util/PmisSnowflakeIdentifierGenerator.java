package com.njydsz.pmis.common.util;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;

/**
 * MyBatis-Plus 主键生成器（雪花算法字符串版）
 *
 * <p>大厂规范：所有主键由应用层雪花算法生成（{@link SnowflakeIdGenerator}），
 * 数据库不做自增（暴露业务量、不可跨库、不可水平扩展），不用 UUID（B-tree 索引页分裂严重）。
 *
 * <p>MyBatis-Plus {@link IdentifierGenerator#nextId(Object)} 接口契约要求返回 {@link Number}；
 * MyBatis-Plus 在注入主键时会做 {@code Number → String} 自动转换（数字 toString 后写入 VARCHAR(20)），
 * 因此本实现返回 {@code Long} 即可，由 MP 自动以「19 位十进制字符串」形式写入 {@code String id} 字段。
 *
 * <p>用法：
 * <ul>
 *   <li>实体标注 {@code @TableId(type = IdType.ASSIGN_ID)} + {@code private String id;}</li>
 *   <li>Spring 容器注入本 Bean（{@code @Component} 自动注册）</li>
 *   <li>调用 {@code mapper.insert(entity)} 时 MP 自动生成雪花 ID</li>
 * </ul>
 *
 * <p>对于需要显式获取雪花字符串 ID 的业务场景（例如生成业务 ID / 外部追踪号），
 * 直接调用 {@link SnowflakeIdGenerator#nextIdStr()}。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class PmisSnowflakeIdentifierGenerator implements IdentifierGenerator {

    /**
     * 生成下一个主键（雪花算法 19 位十进制 Long）。
     *
     * <p>MyBatis-Plus 在装配 {@code @TableId(type = IdType.ASSIGN_ID)} 字段时：
     * <ol>
     *   <li>调用本方法获取 {@code Long}；</li>
     *   <li>若实体主键类型为 {@code String}，执行 {@code Long.toString(id)} 注入；</li>
     *   <li>若实体主键类型为 {@code Long/Integer}，直接注入数值。</li>
     * </ol>
     * 因此本方法返回 {@link Long} 即可，无需手动转字符串。
     *
     * @param entity 实体（可能为 null）
     * @return 19 位雪花 ID 数值
     */
    @Override
    public Long nextId(Object entity) {
        return SnowflakeIdGenerator.nextId();
    }
}
