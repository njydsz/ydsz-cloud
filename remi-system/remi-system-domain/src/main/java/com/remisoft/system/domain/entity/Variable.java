package com.remisoft.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统变量实体
 *
 * <p>对应数据库表 {@code remi_variable}，存储系统级动态变量。
 * 与 {@link Config} 的区别：
 * <ul>
 *   <li>Variable 面向业务侧（前端/ISV 通过 Feign 调用）</li>
 *   <li>Config 面向后端模块（按 group 消费）</li>
 *   <li>Variable 强调「按 key 高频查询」（缓存命中优先）</li>
 * </ul>
 *
 * <p><b>典型使用场景：</b>
 * <ul>
 *   <li>业务开关（动态启用/禁用某功能）</li>
 *   <li>限流阈值（运行时调整 QPS 阈值）</li>
 *   <li>运行时日期（当前会计年度、最近结算月份）</li>
 *   <li>白名单/黑名单（IP 白名单、用户黑名单）</li>
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_variable_key}（{@code variable_key}），
 * 加速按 key 查询与唯一性校验。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see Config 系统配置实体（面向后端）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("remi_variable")
public class Variable extends MpBaseEntity<String> {

    /** 变量键（唯一标识，全局唯一） */
    private String variableKey;

    /** 变量值（按 valueType 反序列化为 String/Number/Boolean/JSON） */
    private String variableValue;

    /** 值类型（STRING/NUMBER/BOOLEAN/JSON，参见 {@link com.remisoft.system.domain.enums.ConfigValueType}） */
    private String valueType;

    /** 变量描述（业务含义说明） */
    private String description;

}
