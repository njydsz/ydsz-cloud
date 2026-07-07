package com.njydsz.pmis.common.util;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus 主键生成器（雪花算法字符串版）
 *
 * <p>大厂规范：所有主键由应用层雪花算法生成（{@link SnowflakeIdGenerator}），
 * 数据库不做自增。雪花 ID 转十进制 19 位字符串，存储到 VARCHAR(20) 列。
 *
 * <p>用法：在 MyBatis-Plus 全局配置中注册为 IdentifierGenerator Bean，
 * 配合实体上的 {@code @TableId(type = IdType.ASSIGN_ID)} 即可。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Component
public class PmisSnowflakeIdentifierGenerator implements IdentifierGenerator {

    /**
     * 生成下一个主键（雪花算法 19 位十进制字符串）
     *
     * @param entity 实体（可能为 null）
     * @return 19 位雪花 ID 字符串
     */
    @Override
    public String nextId(Object entity) {
        return SnowflakeIdGenerator.nextIdStr();
    }
}
