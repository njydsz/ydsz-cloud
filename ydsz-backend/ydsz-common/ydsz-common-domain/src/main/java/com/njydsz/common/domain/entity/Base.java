package com.njydsz.common.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据库实体基类（String 主键）
 *
 * <p>继承 {@link BaseEntity}，包含完整审计字段、乐观锁版本和逻辑删除标识（0 表示未删除）
 * 适用于大多数业务实体。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class Base extends BaseEntity<String> {

    private static final long serialVersionUID = 1L;
}
