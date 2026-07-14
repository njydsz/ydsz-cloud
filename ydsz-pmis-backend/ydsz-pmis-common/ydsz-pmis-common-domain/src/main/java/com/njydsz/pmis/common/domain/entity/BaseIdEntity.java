package com.njydsz.pmis.common.domain.entity;

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 主键基础实体
 *
 * <p>仅包含主键ID的实体基类，适用于不需要审计字段和版本控制的简单场景。
 * 这是实体继承层次结构中的第二层（第一层是 {@link Persistable} 接口）。
 *
 * <p><b>注意：</b>此类仅实现 {@link Persistable} 而非 {@link RootEntity}。
 * 因为它不包含乐观锁版本（{@code revision}）和逻辑删除标识（{@code deleted}）。
 * 需要这些能力的实体应继承 {@link BaseEntity}。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>最小化实体基类，仅包含最必要的字段</li>
 *   <li>使用 MyBatis-Plus 的 ASSIGN_ID 雪花算法生成19位Long类型ID</li>
 *   <li>支持泛型主键类型，可适配 Long、String、UUID 等</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>字典表、配置表等简单实体</li>
 *   <li>不需要追踪创建人和创建时间的场景</li>
 *   <li>历史数据表、日志流水表</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class SysConfig extends BaseIdEntity<Long> {
 *     private String configKey;
 *     private String configValue;
 *     private String description;
 * }
 *
 * // 或使用 String 类型主键
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class SysDict extends BaseIdEntity<String> {
 *     private String dictCode;
 *     private String dictName;
 * }
 * }</pre>
 *
 * <p><b>数据库表结构：</b>
 * <pre>{@code
 * CREATE TABLE sys_config (
 *     id BIGINT PRIMARY KEY,
 *     config_key VARCHAR(100),
 *     config_value VARCHAR(500),
 *     description VARCHAR(255)
 * );
 * }</pre>
 *
 * @param <T> 主键ID类型，支持 Long、String、UUID 等
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see RootEntity
 * @see BaseAuditEntity
 * @see BaseEntity
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseIdEntity<T extends Serializable> implements Persistable<T> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private T id;

}