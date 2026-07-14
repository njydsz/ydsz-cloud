package com.njydsz.pmis.common.domain.entity;

/**
 * 逻辑删除标识接口（软删除。?
 *
 * <p>具备此接口的实体在执。?delete 时改。?{@code SET deleted = 1}。?
 * 查询时自动追。?{@code WHERE deleted = 0}。?
 * 配合 MyBatis-Plus {@code @TableLogic} 注解使用。?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface SoftDeletable {

    /**
     * 获取逻辑删除标识
     *
     * @return 0=未删除，1=已删除，null=未设。?
     */
    Integer getDeleted();

    /**
     * 设置逻辑删除标识
     *
     * @param deleted 删除标识。?=未删除，1=已删除）
     */
    void setDeleted(Integer deleted);

    /**
     * 判断是否已被逻辑删除
     *
     * @return 已删除返。?true，否则返。?false
     */
    default boolean isDeleted() {
        Integer d = getDeleted();
        return d != null && d.intValue() == 1;
    }
}
