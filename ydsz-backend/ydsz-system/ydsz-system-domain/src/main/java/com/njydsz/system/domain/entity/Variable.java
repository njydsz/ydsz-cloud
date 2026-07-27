package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统变量 DO
 *
 * <p>对应数据库表 {@code ydsz_variable}，存储系统级动态变量，
 * 与 Config 的区别：变量更偏向运行时可变的参数（如开关、阈值），
 * 配置更偏向静态的系统参数。支持 Redis 缓存与 TTL 过期。
 *
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_variable")
public class Variable extends MpBaseEntity<String> {

    /** 变量键（唯一标识） */
    private String variableKey;
    /** 变量值 */
    private String variableValue;
    /** 值类型（STRING/NUMBER/BOOLEAN/JSON） */
    private String valueType;
    /** 变量描述 */
    private String description;

}
