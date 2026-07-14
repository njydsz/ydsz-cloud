package com.njydsz.pmis.common.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据库实体基类（兼容�?com.njydsz.pmis.common.domain.entity.BaseDO）�?
 *
 * <p>继承 {@link BaseEntity}，包含完整审计字段、乐观锁版本号和逻辑删除标识�?
 * 适用于大多数业务实体�?
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class BaseDO extends BaseEntity<String> {

    private static final long serialVersionUID = 1L;
}
