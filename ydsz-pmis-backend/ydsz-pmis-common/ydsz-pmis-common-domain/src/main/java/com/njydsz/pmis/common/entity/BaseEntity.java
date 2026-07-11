package com.njydsz.pmis.common.entity;

import java.io.Serializable;

/**
 * DDD 实体基类 —— 具有唯一标识的领域对象。
 * <p>
 * 对标 remi-comm BaseEntity，继承 RootEntity 的版本控制和审计字段，
 * 用于聚合内部的非根实体。
 * </p>
 *
 * @param <ID> 实体标识类型
 * @author njydsz
 * @since 1.0.0
 */
public abstract class BaseEntity<ID extends Serializable> extends RootEntity<ID> {

    private static final long serialVersionUID = 1L;
}
