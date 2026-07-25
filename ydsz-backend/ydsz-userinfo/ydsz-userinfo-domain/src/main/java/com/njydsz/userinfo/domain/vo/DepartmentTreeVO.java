package com.njydsz.userinfo.domain.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 部门树形结构 VO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DepartmentTreeVO {

    private String id;
    private String parentId;
    private String deptCode;
    private String deptName;
    private String deptPath;
    private Integer sortOrder;
    private String status;
    private List<DepartmentTreeVO> children = new ArrayList<>();
}
