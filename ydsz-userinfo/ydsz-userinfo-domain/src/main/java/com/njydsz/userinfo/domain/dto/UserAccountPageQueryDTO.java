package com.njydsz.userinfo.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;

/**
 * 用户分页查询参数 DTO。
 *
 * <p>用于 {@code GET /api/v1/user/page} 接口，支持多条件组合筛选用户列表。 继承 {@link PageQuery} 获取分页参数（{@code pageNum}
 * / {@code pageSize}）。
 *
 * <p><b>筛选条件：</b>所有字段均为可选，未传则不作为筛选条件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserAccountPageQueryDTO extends PageQuery {

  /** 登录用户名（模糊查询） */
  private String username;

  /** 真实姓名（模糊查询） */
  private String realName;

  /** 手机号（模糊查询） */
  private String phone;

  /** 邮箱（模糊查询） */
  private String email;

  /** 账号状态（{@code "ENABLED"}=启用 / {@code "DISABLED"}=禁用，继承自 BaseQuery） */
  private String status;

  /** 用户类型（精确匹配，如 PLATFORM/TENANT_ADMIN/REGULAR） */
  private String userType;

  /** 所属公司 ID（精确匹配） */
  private String companyId;

  /** 所属部门 ID（精确匹配） */
  private String deptId;
}
