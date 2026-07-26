package com.njydsz.userinfo.domain.vo;

import lombok.Data;

/**
 * 部门 VO（扁平结构，不含 deleted/createdBy 等内部字段）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DepartmentVO {

    private String id;
    private String parentId;
    private String deptName;
    private String deptCode;
    private String description;
    private Integer sortOrder;
    private String status;
}
