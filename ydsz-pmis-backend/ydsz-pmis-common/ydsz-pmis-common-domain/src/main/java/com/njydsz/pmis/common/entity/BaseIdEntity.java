package com.njydsz.pmis.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 主键基础实体类
 *
 * <p>仅包含主键ID的实体基类，适用于不需要审计字段和版本控制的简单场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class BaseIdEntity<ID extends Serializable> extends BaseEntity<ID> {

    @java.io.Serial
    private static final long serialVersionUID = 1L;
}
