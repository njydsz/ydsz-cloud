package com.njydsz.common.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Long 主键数据库实体基类
 *
 * <p>继承 {@link BaseEntity}，主键类型固定为 {@link Long}，
 * 适用于使用雪花算法（Snowflake）或自增 BIGINT 主键的数据库表
 *
 * <p>与 {@link Base}（String 主键）的区别：
 * <ul>
 *   <li>{@code Base} — 主键类型为 String，适用于 UUID/字符串主键</li>
 *   <li>{@code BaseLong} — 主键类型为 Long，适用于数值型主键</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see Base
 * @see BaseEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class BaseLong extends BaseEntity<Long> {

    private static final long serialVersionUID = 1L;
}
