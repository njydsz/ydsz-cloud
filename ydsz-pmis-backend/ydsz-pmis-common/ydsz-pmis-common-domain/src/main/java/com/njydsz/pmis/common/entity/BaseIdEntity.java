package com.njydsz.pmis.common.entity;

import java.io.Serializable;

/**
 * 主键基础实体类
 *
 * <p>仅包含主键ID的实体基类，适用于不需要审计字段的简单场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class BaseIdEntity<ID extends Serializable> extends BaseEntity<ID> {

    private static final long serialVersionUID = 1L;
}
