package com.njydsz.userinfo.api.vo;

import lombok.Data;

/**
 * 部门 VO（API 契约层）。
 *
 * <p>定义 Feign 客户端接口的返回类型，供跨服务调用方引用。
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

  /** 部门负责人用户 ID */
  private String leaderId;
}
