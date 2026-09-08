package com.njydsz.userinfo.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 当前登录用户信息 VO
 *
 * <p>供 {@code GET /api/auth/userinfo} 返回，对齐前端 {@code BasicUserInfo}
 * 所需最小字段集：登录后拉取用户资料、渲染头像/昵称、进行角色级路由判断。
 *
 * <p>注意：{@code roles} 为角色编码数组（区别于登录响应 {@code LoginVO.UserInfoVO.roleCode}
 * 的逗号拼接字符串），由前端权限库直接消费。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see com.njydsz.userinfo.server.auth.AuthService#getCurrentUserInfo 构建入口
 * @see LoginVO.UserInfoVO 登录响应中的用户信息
 */
@Data
public class CurrentUserInfoVO {

  /** 用户唯一标识 */
  private String userId;

  /** 登录用户名 */
  private String username;

  /** 用户真实姓名 */
  private String realName;

  /** 用户头像 URL */
  private String avatar;

  /** 用户角色编码列表（用于前端权限路由判断） */
  private List<String> roles;

  /** 租户 ID，多租户场景下标识所属租户 */
  private String tenantId;
}
