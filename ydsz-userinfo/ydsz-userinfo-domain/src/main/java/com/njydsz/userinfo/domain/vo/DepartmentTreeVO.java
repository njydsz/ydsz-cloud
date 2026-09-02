package com.njydsz.userinfo.domain.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 部门树形结构 VO，用于前端组织架构树渲染。
 *
 * <p>由 {@code DepartmentServiceImpl.buildDeptTree()} 构建递归树， 包含部门路径 {@code deptPath} 用于快速查询子树。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class DepartmentTreeVO {

  /** 部门唯一标识 */
  private String id;

  /** 父部门 ID */
  private String parentId;

  /** 部门编码 */
  private String deptCode;

  /** 部门名称 */
  private String deptName;

  /** 部门全路径，如 /1/2/3/，用于快速查询子树 */
  private String deptPath;

  /** 排序序号 */
  private Integer sortOrder;

  /** 状态：ENABLE-启用、DISABLE-禁用 */
  private String status;

  /** 子部门列表 */
  private List<DepartmentTreeVO> children = new ArrayList<>(4);
}
