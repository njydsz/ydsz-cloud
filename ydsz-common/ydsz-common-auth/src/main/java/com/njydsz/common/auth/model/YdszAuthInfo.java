package com.njydsz.common.auth.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Nonnull;
import lombok.Data;

/**
 * ydsz系统统一认证上下文信息抽象基类
 *
 * <p>承载请求维度的全量身份与权限数据，在 WebAuthFilter / AppAuthFilter 解析请求头后写入 RequestContext 供下游链路使用。
 *
 * <h3>设计说明</h3>
 *
 * <ul>
 *   <li>实现 {@link AuthInfo}（继承 {@link com.njydsz.common.core.model.CurrentUser}）
 *   <li>身份类型固定为字符串 {@code "company"}（公司级），不支持继承扩展
 *   <li>服务类型由子类通过 {@link #getServiceTypeCode()} 实现区分（webService / appService）
 *   <li>所有集合类型字段使用不可变空集合初始化，防止 NPE
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public abstract class YdszAuthInfo implements AuthInfo {

  /** 用户系统语言 */
  private String userLanguage;

  /** 用户唯一标识 */
  private String uniqueId;

  /** 用户鉴权 Token */
  private String accessToken;

  /** 数据权限范围类型 */
  private String dataScope;

  /** 有权限访问的公司 ID 集合 */
  private Set<String> hasPermissionCompanyIds;

  /** 有权限访问的部门 ID 集合 */
  private Set<String> hasPermissionDeptIds;

  /** 有权限访问的项目 ID 集合 */
  private Set<String> hasPermissionProjectIds;

  /** 有权限访问的区域 ID 集合 */
  private Set<String> hasPermissionRegionIds;

  /** 租户唯一标识 */
  private String tenantId;

  /** 设备唯一标识 */
  private String distinctId;

  /** 请求来源标识 */
  private String requestSource;

  /** 表级列可见规则 */
  @Nonnull private Map<String, Set<String>> visibleColumnsByTable = Collections.emptyMap();

  /** 表级列可编辑规则 */
  @Nonnull private Map<String, Set<String>> editableColumnsByTable = Collections.emptyMap();

  /**
   * 返回身份类型为公司用户
   *
   * @return 字符串 {@code "company"}
   */
  @Override
  public String getIdentityType() {
    return "company";
  }

  /**
   * 返回服务类型码，由子类实现
   *
   * @return 服务类型码，非空字符串
   */
  @Override
  public abstract String getServiceTypeCode();

  /**
   * 获取表级列可见规则
   *
   * @return 表名→列集合的映射
   */
  @Override
  public Map<String, Set<String>> getVisibleColumnsByTable() {
    return visibleColumnsByTable;
  }

  /**
   * 获取表级列可编辑规则
   *
   * @return 表名→列集合的映射
   */
  @Override
  public Map<String, Set<String>> getEditableColumnsByTable() {
    return editableColumnsByTable;
  }
}
