package com.njydsz.pmis.common.entity;

import java.io.Serializable;

/**
 * DDD 值对象基类 —— 无唯一标识的不可变领域对象。
 * <p>
 * 对标 remi-comm ValueObject，值对象的相等性基于所有属性值，
 * 而非标识。子类应为不可变类，所有字段 final。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public abstract class ValueObject implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 值对象相等性 —— 基于类型和所有属性值。
     * 子类应重写此方法并比较所有字段。
     */
    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();
}
