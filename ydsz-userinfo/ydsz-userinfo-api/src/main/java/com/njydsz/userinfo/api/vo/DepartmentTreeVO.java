package com.njydsz.userinfo.api.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 部门树形结构 VO（API 契约层）。
 *
 * <p>定义 Feign 客户端接口的返回类型，供跨服务调用方引用。
 *
 * @author ydsz-team
 * @since 1.0.0
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
  private List<DepartmentTreeVO> children = new ArrayList<>();
}
