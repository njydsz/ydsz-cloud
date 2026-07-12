package com.njydsz.pmis.common.domain.entity;

import java.io.Serializable;

/**
 * 组织维度标记接口
 *
 * <p>标识该实体支持组织维度数据隔离。业务实体可通过实现此接口替代继承 {@link GroupEntity}。
 *
 * <p>接口定义了公司ID和部门ID的 getter/setter，与 {@link GroupEntity} 的字段对齐；
 * {@link #getGroupId()} / {@link #setGroupId(Long)} 作为便捷方法，默认返回 / 设置 companyId。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface GroupAware extends Serializable {

    /**
     * 获取公司ID
     */
    Long getCompanyId();

    /**
     * 设置公司ID
     */
    void setCompanyId(Long companyId);

    /**
     * 获取部门ID
     */
    Long getDeptId();

    /**
     * 设置部门ID
     */
    void setDeptId(Long deptId);

    /**
     * 便捷方法：获取组ID，默认返回 companyId
     *
     * <p>实现类可按需覆写此方法以返回组合值。
     */
    default Long getGroupId() {
        return getCompanyId();
    }

    /**
     * 便捷方法：设置组ID，默认设置 companyId
     *
     * <p>实现类可按需覆写此方法。
     */
    default void setGroupId(Long groupId) {
        setCompanyId(groupId);
    }
}
