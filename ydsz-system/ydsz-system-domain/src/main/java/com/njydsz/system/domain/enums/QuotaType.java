package com.njydsz.system.domain.enums;

import lombok.Getter;

/**
 * 租户配额类型枚举
 *
 * <p>定义 SaaS 多租户体系下支持的配额维度。每个配额维度对应一个资源上限， 由 {@link com.njydsz.system.domain.entity.TenantPlan#getQuotaJson()} 存储，
 * 运行时由 {@link com.njydsz.system.server.service.TenantQuotaService} 校验。
 *
 * <p><b>配额维度：</b>
 *
 * <ul>
 *   <li>{@link #MAX_USERS} — 最大用户数（包含管理员和普通用户）
 *   <li>{@link #MAX_PROJECTS} — 最大项目数
 *   <li>{@link #MAX_CONFIGS} — 最大配置项数
 *   <li>{@link #MAX_DICT_TYPES} — 最大字典类型数
 *   <li>{@link #MAX_VARIABLES} — 最大变量数
 *   <li>{@link #STORAGE_GB} — 存储空间（GB）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
public enum QuotaType {

  /** 最大用户数 */
  /** MAX_USERS */
  MAX_USERS("maxUsers", "最大用户数", Integer.class),

  /** 最大项目数 */
  /** MAX_PROJECTS */
  MAX_PROJECTS("maxProjects", "最大项目数", Integer.class),

  /** 最大配置项数 */
  /** MAX_CONFIGS */
  MAX_CONFIGS("maxConfigs", "最大配置项数", Integer.class),

  /** 最大字典类型数 */
  /** MAX_DICT_TYPES */
  MAX_DICT_TYPES("maxDictTypes", "最大字典类型数", Integer.class),

  /** 最大变量数 */
  /** MAX_VARIABLES */
  MAX_VARIABLES("maxVariables", "最大变量数", Integer.class),

  /** 存储空间（GB） */
  /** STORAGE_GB */
  STORAGE_GB("storageGb", "存储空间(GB)", Double.class);

  /** JSON 键名 */
  private final String jsonKey;

  /** 显示名称 */
  private final String displayName;

  /** 值类型 */
  private final Class<?> valueType;

  QuotaType(String jsonKey, String displayName, Class<?> valueType) {
    this.jsonKey = jsonKey;
    this.displayName = displayName;
    this.valueType = valueType;
  }

  /**
   * 根据 JSON 键名查找配额类型。
   *
   * @param jsonKey JSON 键名
   * @return 配额类型；未找到返回 null
   */
  public static QuotaType fromJsonKey(String jsonKey) {
    for (QuotaType type : values()) {
      if (type.jsonKey.equals(jsonKey)) {
        return type;
      }
    }
    return null;
  }
}
