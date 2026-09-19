package com.njydsz.example.feign.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户视图对象（Quickstart 示例）。
 *
 * <p>Feign 接口返回的自动解包业务类型。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Data
public class UserVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID */
  private Long id;

  /** 用户名称 */
  private String name;

  /** 部门名称 */
  private String deptName;

  /** 角色编码 */
  private String roleCode;
}
