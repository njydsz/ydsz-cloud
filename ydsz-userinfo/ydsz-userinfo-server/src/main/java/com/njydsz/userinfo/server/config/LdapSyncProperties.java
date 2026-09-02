package com.njydsz.userinfo.server.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LDAP/AD 组织架构同步配置属性。
 *
 * <p>控制从 LDAP/AD 自动同步部门与用户数据的行为，包括定时任务触发周期、搜索基准 DN、
 * 属性映射、层级同步策略等。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.ldap.sync}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     ldap:
 *       sync:
 *         enabled: true
 *         cron: "0 0 2 * * ?"
 *         base-dn: dc=ydszsoft,dc=com
 *         group-dn: ou=Groups,dc=ydszsoft,dc=com
 *         user-search-filter: "(&amp;(objectClass=person)(uid=*))"
 *         group-search-filter: "(&amp;(objectClass=organizationalUnit)(ou=*))"
 *         user-attributes:
 *           uid: username
 *           displayName: realName
 *           mail: email
 *           department: departmentName
 *         department-hierarchy-enabled: true
 *         delete-orphaned-users: false
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.ldap.sync")
public class LdapSyncProperties {

  /** 默认 cron 表达式：每天凌晨 2 点执行。 */
  private static final String DEFAULT_CRON = "0 0 2 * * ?";

  /** 默认 LDAP 用户搜索过滤器。 */
  private static final String DEFAULT_USER_SEARCH_FILTER = "(&(objectClass=person)(uid=*))";

  /** 默认 LDAP 部门搜索过滤器。 */
  private static final String DEFAULT_GROUP_SEARCH_FILTER = "(&(objectClass=organizationalUnit)(ou=*))";

  /** 是否启用 LDAP 同步。 */
  private boolean enabled = false;

  /** 定时同步 cron 表达式，默认每天凌晨 2 点。 */
  private String cron = DEFAULT_CRON;

  /** LDAP 搜索基准 DN（如 dc=ydszsoft,dc=com）。 */
  private String baseDn = "";

  /** 部门组 DN（如 ou=Groups,dc=ydszsoft,dc=com）。 */
  private String groupDn = "";

  /** 用户搜索过滤器。 */
  private String userSearchFilter = DEFAULT_USER_SEARCH_FILTER;

  /** 部门搜索过滤器。 */
  private String groupSearchFilter = DEFAULT_GROUP_SEARCH_FILTER;

  /**
   * LDAP 属性到 ydsz 字段的映射。
   *
   * <p>Key 为 LDAP 属性名（如 uid、displayName、mail），Value 为 ydsz 字段名（如 username、realName、email）。
   */
  private Map<String, String> userAttributes = new HashMap<>(16);

  /** 是否同步部门层级关系（基于 DN 解析父子结构）。 */
  private boolean departmentHierarchyEnabled = true;

  /** 是否删除 LDAP 中已不存在的用户（false 则仅禁用）。 */
  private boolean deleteOrphanedUsers = false;
}
