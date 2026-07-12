package com.njydsz.pmis.common.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 可版本化数据库实体基类（兼容旧 com.njydsz.pmis.common.entity.VersionableDO）。
 *
 * <p>继承 {@link BaseDO}，自带乐观锁版本号字段（revision），
 * 配合 MyBatis-Plus {@code @Version} 注解使用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class VersionableDO extends BaseDO {

    private static final long serialVersionUID = 1L;
}
