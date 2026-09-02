package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分配用户角色请求 DTO。
 *
 * <p>用于 {@code Post /api/v1/user/{userId}/roles} 接口，为指定用户分配角色。 采用<b>全量覆盖</b>策略：传入的角色 ID
 * 列表将完全替换用户原有角色关联。
 *
 * <p><b>注意事项：</b>
 *
 * <ul>
 *   <li>传入空列表表示清除用户所有角色
 *   <li>角色 ID 必须为系统中已存在的有效角色
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AssignRolesDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 角色 ID 列表（全量覆盖，空列表表示清除所有角色） */
  @Size(max = 50, message = "单次分配角色数量不能超过 50 个")
  private List<String> roleIds;
}
