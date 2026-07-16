package com.njydsz.common.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 可版本化数据库实体基类（已废弃）
 *
 * <p>继承 {@link BaseDO}，但 {@code BaseDO} 已经继承了 {@link BaseEntity}，
 * 而 {@link BaseEntity} 已包含乐观锁版本字段（{@code revision}）并实现了 {@link Versionable} 接口。
 * 因此此类无任何额外字段或方法，仅为历史兼容保留。
 *
 * <p><b>建议：</b>新代码直接继承 {@link BaseDO} 或 {@link BaseLongDO}，不要使用此类。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated {@link BaseDO} 已包含乐观锁版本，直接使用 {@link BaseDO} 即可
 */
@Deprecated
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class VersionableDO extends BaseDO {

    private static final long serialVersionUID = 1L;
}
