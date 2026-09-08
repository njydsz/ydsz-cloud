package com.njydsz.userinfo.domain.provision;

import java.io.Serializable;
import java.util.Map;

/**
 * 供给用户记录（P0-1 Identity Provisioning 管道）。
 *
 * <p>标准化的外部用户信息载体，连接器将不同外部源（LDAP/JDBC/SCIM）的数据
 * 转换为统一格式后返回，由上层编排器写入本地数据库。
 *
 * <p><b>字段规范：</b>
 *
 * <ul>
 *   <li>{@code externalId} — 外部源唯一标识（如 LDAP DN、JDBC 主键）</li>
 *   <li>{@code username} — 用户名（登录账号）</li>
 *   <li>{@code realName} — 真实姓名（可选）</li>
 *   <li>{@code email} — 邮箱（可选）</li>
 *   <li>{@code phone} — 手机号（可选）</li>
 *   <li>{@code departmentCode} — 部门编码（可选，用于关联 ydsz 部门）</li>
 *   <li>{@code isActive} — 是否有效（YDIZ-OOP-006 布尔前缀规则）</li>
 *   <li>{@code attributes} — 扩展属性（平台特有字段，如 LDAP UUID、职位、办公地址等）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 * @param externalId 外部源唯一标识（如 LDAP DN、JDBC 主键）
 * @param username 用户名（登录账号）
 * @param realName 真实姓名（可选）
 * @param email 邮箱（可选）
 * @param phone 手机号（可选）
 * @param departmentCode 部门编码（可选，用于关联 ydsz 部门）
 * @param isActive 是否有效（YDIZ-OOP-006 布尔前缀规则）
 * @param attributes 扩展属性（平台特有字段，如 LDAP UUID、职位、办公地址等）
 */
public record ProvisionRecord(
    String externalId,
    String username,
    String realName,
    String email,
    String phone,
    String departmentCode,
    boolean isActive,
    Map<String, String> attributes)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 构造供给记录（仅必填字段）。
   *
   * @param externalId 外部源唯一标识
   * @param username 用户名
   * @param isActive 是否有效
   */
  public ProvisionRecord(String externalId, String username, boolean isActive) {
    this(externalId, username, null, null, null, null, isActive, Map.of());
  }

  /**
   * 获取扩展属性值。
   *
   * @param key 属性键
   * @return 属性值；不存在返回 null
   */
  public String getAttribute(String key) {
    return attributes != null ? attributes.get(key) : null;
  }
}
