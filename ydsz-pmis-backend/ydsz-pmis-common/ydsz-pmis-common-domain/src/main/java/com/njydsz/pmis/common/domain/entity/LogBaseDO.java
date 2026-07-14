package com.njydsz.pmis.common.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 日志型实体基类（兼容 com.njydsz.pmis.common.entity.LogBaseDO）
 *
 * <p>继承 {@link BaseAuditEntity}，仅包含审计字段（createdBy/createdAt/updatedBy/updatedAt），
 * 不含乐观锁版本和逻辑删除标识，适用于日志表、操作记录表等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class LogBaseDO extends BaseAuditEntity<String> {

    private static final long serialVersionUID = 1L;
}
