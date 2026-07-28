package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 部门 VO，扁平结构，用于 Controller 列表返回。
 *
 * <p>不包含 deleted、createdBy 等内部维护字段。
 * 树形结构请使用 {@link DepartmentTreeVO}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DepartmentVO {

    /** 部门唯一标识 */
    private String id;
    /** 父部门 ID，根部门为 0 或 null */
    private String parentId;
    /** 部门名称 */
    private String deptName;
    /** 部门编码，全局唯一 */
    private String deptCode;
    /** 部门描述 */
    private String description;
    /** 排序序号 */
    private Integer sortOrder;
    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
}
