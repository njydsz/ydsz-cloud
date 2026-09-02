package com.njydsz.common.auth.model;

import java.util.Map;
import java.util.Set;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.njydsz.common.core.model.CurrentUser;

/**
 * 认证信息完整接口
 *
 * <p>定义了跨模块传递用户身份与权限上下文的完整契约，继承 {@link CurrentUser} 基础身份能力， 扩展列级权限、令牌等认证业务特性。
 *
 * <p>本接口定义于 common-auth 模块（L5 服务层）。如需仅读取基础身份（userId/tenantId）， 请使用 {@link CurrentUser} 接口（core
 * 模块），避免上层对 auth 模块的直接依赖。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CurrentUser
 * @see YdszAuthInfo
 */
public interface AuthInfo extends CurrentUser {

  /**
   * 获取用户系统语言
   *
   * @return 语言码，如 {@code zh-CN}、{@code en-US}
   */
  @Nullable
  String getUserLanguage();

  /**
   * 获取服务类型编码
   *
   * @return 服务类型码，如 "webService"、"appService"
   */
  @Nullable
  String getServiceTypeCode();

  /**
   * 获取访问令牌
   *
   * @return AccessToken
   */
  @Nullable
  String getAccessToken();

  /**
   * 获取用户设备唯一标识
   *
   * @return 设备唯一ID
   */
  @Nullable
  String getDistinctId();

  /**
   * 获取请求来源标识
   *
   * @return 请求来源
   */
  @Nullable
  String getRequestSource();

  /**
   * 获取列级权限：表级可见列规则
   *
   * <p>格式：{@code table_name -> Set<column_name>}，key 为表名（小写），value 为允许查看的列名集合（小写）。
   *
   * @return 表名到可见列集合的映射；不允许返回 null，无规则时返回空 Map
   */
  @Nonnull
  Map<String, Set<String>> getVisibleColumnsByTable();

  /**
   * 获取列级权限：表级可编辑列规则
   *
   * <p>格式：{@code table_name -> Set<column_name>}，key 为表名（小写），value 为允许写入的列名集合（小写）。
   *
   * @return 表名到可编辑列集合的映射；不允许返回 null，无规则时返回空 Map
   */
  @Nonnull
  Map<String, Set<String>> getEditableColumnsByTable();

  /**
   * 获取数据权限维度 ID 集合（类型安全扩展点，替代反射调用）。
   *
   * <p>覆盖 {@link CurrentUser#getPermissionIds(String)}，支持以下维度：
   *
   * <ul>
   *   <li>{@code companyIds} → {@link #getHasPermissionCompanyIds()}
   *   <li>{@code deptIds} → {@link #getHasPermissionDeptIds()}
   *   <li>其他维度返回 null
   * </ul>
   *
   * @param claim 维度标识
   * @return 权限 ID 集合；不支持或不存在返回 null
   * @since 26.09.01
   */
  @Override
  @Nullable
  default Set<String> getPermissionIds(String claim) {
    if ("companyIds".equals(claim)) {
      return getHasPermissionCompanyIds();
    }
    if ("deptIds".equals(claim)) {
      return getHasPermissionDeptIds();
    }
    return null;
  }

  /**
   * 获取有权限的公司 ID 集合。
   *
   * @return 公司 ID 集合；无权限返回空集合
   */
  @Nonnull
  Set<String> getHasPermissionCompanyIds();

  /**
   * 获取有权限的部门 ID 集合。
   *
   * @return 部门 ID 集合；无权限返回空集合
   */
  @Nonnull
  Set<String> getHasPermissionDeptIds();
}
