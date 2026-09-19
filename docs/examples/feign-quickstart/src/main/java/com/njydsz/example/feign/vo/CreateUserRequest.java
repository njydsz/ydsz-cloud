package com.njydsz.example.feign.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 创建用户请求（Quickstart 示例）。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Data
public class CreateUserRequest implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户名称 */
  private String name;

  /** 部门 ID */
  private Long deptId;

  /** 角色编码 */
  private String roleCode;
}
