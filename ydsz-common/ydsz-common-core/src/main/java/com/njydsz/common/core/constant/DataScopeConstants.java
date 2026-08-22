package com.njydsz.common.core.constant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限范围类型常量。
 *
 * <p>定义系统中数据权限的维度类型编码及关联的数据库列名，用于行级数据权限控制。
 *
 * <p>替代原枚举 {@code DataScopeType}，改用常量 + String 方案消除跨模块枚举耦合。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class DataScopeConstants {

  private DataScopeConstants() {}

  /** 租户维度编码 */
  public static final String TENANT = "tenant";

  /** 集团维度编码 */
  public static final String GROUP = "group";

  /** 公司维度编码 */
  public static final String COMPANY = "company";

  /** 项目维度编码 */
  public static final String PROJECT = "project";

  /** 部门维度编码 */
  public static final String DEPT = "dept";

  /** 区域维度编码 */
  public static final String REGION = "region";

  /** 用户维度编码 */
  public static final String USER = "user";

  /** 自定义维度编码 */
  public static final String CUSTOM = "custom";

  /** 租户维度列名 */
  public static final String COLUMN_TENANT = "tenant_id";

  /** 集团维度列名 */
  public static final String COLUMN_GROUP = "group_id";

  /** 公司维度列名 */
  public static final String COLUMN_COMPANY = "company_id";

  /** 项目维度列名 */
  public static final String COLUMN_PROJECT = "project_id";

  /** 部门维度列名 */
  public static final String COLUMN_DEPT = "dept_id";

  /** 区域维度列名 */
  public static final String COLUMN_REGION = "region_id";

  /** 用户维度列名 */
  public static final String COLUMN_USER = "user_id";

  /** 所有标准维度编码集合（不含 CUSTOM） */
  private static final Map<String, String> CODE_MAP = new ConcurrentHashMap<>();

  static {
    CODE_MAP.put(TENANT, COLUMN_TENANT);
    CODE_MAP.put(GROUP, COLUMN_GROUP);
    CODE_MAP.put(COMPANY, COLUMN_COMPANY);
    CODE_MAP.put(PROJECT, COLUMN_PROJECT);
    CODE_MAP.put(DEPT, COLUMN_DEPT);
    CODE_MAP.put(REGION, COLUMN_REGION);
    CODE_MAP.put(USER, COLUMN_USER);
    CODE_MAP.put(CUSTOM, null);
  }

  /**
   * 校验并返回编码字符串。
   *
   * @param code 编码值
   * @return 原编码字符串
   * @throws IllegalArgumentException 当编码不存在或为 null/空时抛出
   */
  public static String codeOf(String code) {
    if (code == null || code.trim().isEmpty()) {
      throw new IllegalArgumentException("数据权限编码不能为空");
    }
    String trimmed = code.trim();
    if (!CODE_MAP.containsKey(trimmed)) {
      throw new IllegalArgumentException("未知的数据权限编码: " + code);
    }
    return trimmed;
  }

  /**
   * 判断给定编码是否为有效的数据权限维度编码。
   *
   * @param code 编码值
   * @return 有效返回 true，否则返回 false
   */
  public static boolean isValidCode(String code) {
    return code != null && CODE_MAP.containsKey(code.trim());
  }

  /**
   * 根据编码获取对应的数据库列名。
   *
   * @param code 维度编码
   * @return 数据库列名；CUSTOM 返回 null；未知编码返回 null
   */
  public static String columnByCode(String code) {
    if (code == null) {
      return null;
    }
    return CODE_MAP.get(code.trim());
  }
}
