package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 部门实体
 *
 * <p>对应数据库表 {@code ydsz_department}，存储组织架构的部门节点，支持无限级树形结构。 部门是组织维度（区别于角色维度的「权限点」），与用户通过 {@link
 * UserDept} 中间表关联。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code parentId}：父部门 ID，支持无限级树形（{@code "0"} = 根部门）
 *   <li>{@code deptCode}：部门编码（业务侧引用，全局唯一）
 *   <li>{@code leaderId}：部门负责人用户 ID，支持 {@code leader:xxx} 审批人展开
 *   <li>{@code sortOrder}：同级排序序号（升序）
 * </ul>
 *
 * <p><b>树形查询：</b>前端通过 {@code /api/v1/department/tree} 接口获取整棵部门树（递归构建）。
 *
 * <p><b>数据权限：</b>{@link Role#dataScope} 通过部门树实现 「本部门及子部门」「仅本部门」「自定义部门」三种隔离范围。
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_dept_code}（{@code dept_code}）， 普通索引 {@code idx_parent_id}（{@code
 * parent_id}）、{@code idx_leader_id}（{@code leader_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UserDept 用户-部门中间表
 * @see UserAccount 用户实体（含 {@code deptId} 字段，支持 {@code dept:xxx} 审批人展开）
 * @see com.njydsz.userinfo.web.controller.DepartmentController 部门 Controller
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_department")
public class Department extends MpBaseEntity<String> {

  /** 父部门 ID，根节点为 {@code "0"}，支持无限级树形结构 */
  private String parentId;

  /** 部门名称（前端展示） */
  private String deptName;

  /** 部门编码（业务侧引用，全局唯一，建议格式 {@code DEPT_XXX}） */
  private String deptCode;

  /** 部门描述（说明部门职责与归属） */
  private String description;

  /** 同级排序序号（升序） */
  private Integer sortOrder;

  /**
   * 启用状态（{@code "ENABLED"} / {@code "DISABLED"}）
   *
   * <p>禁用后，部门下用户无法被分配新角色，但现有角色不受影响。
   */
  private String status;

  /**
   * 部门负责人用户 ID。
   *
   * <p>关联 {@link UserAccount#getId()}，用于：① 部门负责人审批；② 部门负责人主页跳转； ③ 部门工作汇报关系链。
   */
  private String leaderId;
}
