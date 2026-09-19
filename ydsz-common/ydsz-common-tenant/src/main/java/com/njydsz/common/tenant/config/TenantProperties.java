package com.njydsz.common.tenant.config;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.domain.constant.DataPermissionHeaderConstants;

/**
 * 多租户配置属性。
 *
 * <p>配置前缀：{@code ydsz.tenant}
 *
 * <h3>模式说明</h3>
 *
 * <ul>
 *   <li>{@link TenantMode#SINGLE}：只取 {@code tenant-fields} 的第一个字段注入 SQL
 *   <li>{@link TenantMode#MULTI}：取 {@code tenant-fields} 的全部字段注入 SQL（AND 连接）
 *   <li>{@link TenantMode#ISOLATE_DB}：独立数据源模式，每租户使用独立数据库
 *   <li>{@link TenantMode#SCHEMA}：Schema 隔离模式，每租户使用独立 PostgreSQL Schema
 * </ul>
 *
 * <h3>配置示例 — 单租户</h3>
 *
 * <pre>
 * ydsz:
 *   tenant:
 *     enabled: true
 *     mode: SINGLE
 *     tenant-column: tenant_id
 *     ignore-tables: [ydsz_sys_tenant, ydsz_sys_tenant_plan]
 *     anon-urls: [/auth/login, /auth/register]
 * </pre>
 *
 * <h3>配置示例 — 多字段组合（集团+公司+部门+项目）</h3>
 *
 * <pre>
 * ydsz:
 *   tenant:
 *     enabled: true
 *     mode: MULTI
 *     tenant-fields:
 *       - column: tenant_id
 *         claim: tenantId
 *         header: X-Tenant-Id
 *       - column: company_id
 *         claim: companyId
 *         header: X-Company-Ids
 *       - column: dept_id
 *         claim: deptId
 *         header: X-Dept-Ids
 *         multi-value: true       # 多值 → WHERE dept_id IN (...)
 *       - column: project_id
 *         claim: projectId
 *         header: X-Project-Ids
 *         multi-value: true
 * </pre>
 *
 * <h3>配置示例 — per-table 列名覆盖</h3>
 *
 * <pre>
 * ydsz:
 *   tenant:
 *     table-column-mapping:
 *       ydsz_wiki_file_node: org_id
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.tenant")
public class TenantProperties {

  /** 是否启用多租户（默认 false，不启用）。 */
  private boolean isEnabled = false;

  /** 租户隔离模式（默认 SINGLE）。 */
  @NotNull private TenantMode mode = TenantMode.SINGLE;

  /**
   * 默认租户列名（默认 tenant_id）。
   *
   * <p>当 {@link #tenantFields} 为空时使用此字段。
   */
  @NotBlank private String tenantColumn = "tenant_id";

  /**
   * 默认 JWT claim 名（默认 tenantId）。
   *
   * <p>当 {@link #tenantFields} 为空时，从此 claim 获取值。
   */
  private String defaultClaim = "tenantId";

  /**
   * 默认 HTTP header 名（默认 X-Tenant-Id）。
   *
   * <p>当 {@link #tenantFields} 为空时，从此 header 获取值。
   */
  private String defaultHeader = DataPermissionHeaderConstants.X_TENANT_ID;

  /** 超级管理员租户 ID（默认 "0"）。 */
  @NotBlank private String superTenantId = "0";

  /** 系统租户 ID（默认 "0"）。 */
  @NotBlank private String systemTenantId = "0";

  /**
   * 租户字段配置列表（动态组合，任意字段任意组合）。
   *
   * <p>为空时回退到 {@link #tenantColumn} + {@link #defaultClaim} + {@link #defaultHeader} + SINGLE 模式。
   *
   * <p>非空时，每个字段定义：
   *
   * <ul>
   *   <li>{@code column} — 数据库列名（必填）
   *   <li>{@code claim} — JWT claim 名（可选，不填则不从 JWT 取值）
   *   <li>{@code header} — HTTP header 名（可选，不填则不从 header 取值）
   *   <li>{@code multiValue} — 是否多值（默认 false，单值用 {@code = ?}，多值用 {@code IN (...)}）
   * </ul>
   */
  private List<TenantField> tenantFields = new ArrayList<>(4);

  /**
   * per-table 列名覆盖映射。
   *
   * <p>key=表名（小写），value=列名。
   *
   * <p><b>注意：</b>当配置了 {@code tenant-fields} 时，此映射覆盖的是 <b>第一个字段</b>的列名（用于 per-table 不同列名场景）。
   */
  private Map<String, String> tableColumnMapping = new HashMap<>(16);

  /** 忽略租户隔离的表列表（忽略大小写）。 */
  private Set<String> ignoreTables = new LinkedHashSet<>();

  /** URL 级白名单，跳过租户隔离的请求路径（前缀匹配）。 */
  private Set<String> anonUrls = new LinkedHashSet<>();

  /**
   * 跨租户共享映射。
   *
   * <p>key=租户 ID，value=该租户可访问的源租户 ID 列表。
   */
  private Map<String, List<String>> tenantSharing = new HashMap<>(8);

  /**
   * per-tenant 数据源映射（ISOLATE_DB 模式）。
   *
   * <p>key=租户 ID，value=数据源 key（对应 Spring 的 Bean 名）。
   */
  private Map<String, String> datasourceMapping = new HashMap<>(8);

  /**
   * per-tenant Schema 名称映射（SCHEMA 模式）。
   *
   * <p>key=租户 ID，value=PostgreSQL schema 名称。未命中时使用默认规则 {@code "tenant_" + tenantId}。
   *
   * <p>示例：将租户 A 映射到 {@code schema_grp1} 而非 {@code tenant_A}。
   *
   * @since 26.09.19
   */
  private Map<String, String> schemaMapping = new HashMap<>(8);

  /**
   * 是否启用 PostgreSQL search_path 自动设置（SCHEMA 模式）。
   *
   * <p>启用后，在获取连接时自动执行 {@code SET search_path TO {schema},public}，
   * 使得不带 schema 前缀的表名自动解析到租户 schema，
   * 减少 SQL 改写开销。默认关闭（使用 JSqlParser 改写表名前缀方式）。
   *
   * @since 26.09.19
   */
  private boolean schemaSearchPathEnabled = false;

  /** SQL 改写缓存配置（默认关闭）。 */
  private SqlCache sqlCache = new SqlCache();

  // -----------------------------------------------------------------------
  // 内部类型定义
  // -----------------------------------------------------------------------

  /**
   * SQL 改写缓存配置。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  @Data
  public static class SqlCache {
    /** 是否启用（默认 false）。 */
    private boolean isEnabled = false;
    /** 最大缓存条目数（默认 2000）。 */
    private long maxSize = 2000L;
    /** 未访问过期时间（分钟，默认 10）。 */
    private long expireMinutes = 10L;

    public SqlCache() {}

    public boolean isEnabled() {
      return isEnabled;
    }

    public long getMaxSize() {
      return maxSize;
    }

    public long getExpireMinutes() {
      return expireMinutes;
    }
  }

  /**
   * 租户隔离模式枚举。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  public enum TenantMode {
    /** 单字段模式：只取第一个租户字段注入 SQL。 */
    SINGLE,
    /** 多字段组合模式：取全部租户字段注入 SQL（AND 连接）。 */
    MULTI,
    /** 独立数据源模式：每租户使用独立数据库。 */
    ISOLATE_DB,
    /** Schema 隔离模式：每租户使用独立 PostgreSQL Schema。 */
    SCHEMA
  }

  /**
   * 单个租户字段配置。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  public static class TenantField {
    /** 数据库列名（必填）。 */
    private String column;
    /** JWT claim 名（可选）。 */
    private String claim;
    /** HTTP header 名（可选，Feign 跨服务恢复用）。 */
    private String header;
    /** 是否多值（默认 false，true → WHERE column IN (...)）。 */
    private boolean isMultiValue = false;

    /** 默认构造器（用于反序列化）。 */
    public TenantField() {}

    /**
     * 构造指定列名的租户字段（claim/header 均为空，multiValue=false）。
     *
     * @param column 数据库列名
     */
    public TenantField(String column) {
      this.column = column;
    }

    public String getColumn() {
      return column;
    }

    public void setColumn(String column) {
      this.column = column;
    }

    public String getClaim() {
      return claim;
    }

    public void setClaim(String claim) {
      this.claim = claim;
    }

    public String getHeader() {
      return header;
    }

    public void setHeader(String header) {
      this.header = header;
    }

    public boolean isMultiValue() {
      return isMultiValue;
    }

    public void setMultiValue(boolean multiValue) {
      this.isMultiValue = multiValue;
    }
  }

  // -----------------------------------------------------------------------
  // 派生 getter
  // -----------------------------------------------------------------------

  /**
   * 获取激活的租户字段列表。
   *
   * <p>当 {@link #tenantFields} 非空时直接返回；否则回退到单字段模式（使用 tenantColumn + defaultClaim + defaultHeader）。
   *
   * @return 激活的租户字段列表（非空）
   */
  public List<TenantField> getActiveTenantFields() {
    if (tenantFields != null && !tenantFields.isEmpty()) {
      return tenantFields;
    }
    TenantField fallback = new TenantField(tenantColumn);
    fallback.setClaim(defaultClaim);
    fallback.setHeader(defaultHeader);
    return List.of(fallback);
  }

  /**
   * 获取归一化的匿名 URL 集合。
   *
   * <p>去除空白项，确保返回非空集合。
   *
   * @return 归一化后的 URL 前缀集合
   */
  public Set<String> getNormalizedAnonUrls() {
    if (anonUrls == null || anonUrls.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> normalized = new LinkedHashSet<>(anonUrls.size());
    for (String url : anonUrls) {
      if (url != null && !url.isBlank()) {
        normalized.add(url.strip());
      }
    }
    return normalized;
  }

  /**
   * 获取归一化的忽略表集合（全部小写）。
   *
   * @return 归一化后的表名集合（小写）
   */
  public Set<String> getNormalizedIgnoreTables() {
    if (ignoreTables == null || ignoreTables.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> normalized = new LinkedHashSet<>(ignoreTables.size());
    for (String table : ignoreTables) {
      if (table != null && !table.isBlank()) {
        normalized.add(table.strip().toLowerCase());
      }
    }
    return normalized;
  }

  /**
   * 解析指定表使用的租户列名（per-table 覆盖优先，回退到缺省列名）。
   *
   * @param tableName 表名
   * @return 列名，无覆盖时返回 null
   */
  public String resolveColumn(String tableName) {
    if (tableColumnMapping == null || tableName == null) {
      return null;
    }
    // 尝试精确匹配
    String column = tableColumnMapping.get(tableName.toLowerCase());
    if (column != null && !column.isBlank()) {
      return column.strip();
    }
    // 兼容带 schema 前缀的表名
    int dotIndex = tableName.lastIndexOf('.');
    if (dotIndex > 0 && dotIndex < tableName.length() - 1) {
      column = tableColumnMapping.get(tableName.substring(dotIndex + 1).toLowerCase());
      if (column != null && !column.isBlank()) {
        return column.strip();
      }
    }
    return null;
  }

  /**
   * 获取跨租户共享映射。
   *
   * @return 跨租户共享映射（key=租户 ID，value=可访问源租户 ID 列表）
   */
  public Map<String, List<String>> getTenantSharing() {
    return tenantSharing != null ? tenantSharing : Collections.emptyMap();
  }

  /**
   * 获取 per-tenant 数据源映射（ISOLATE_DB 模式）。
   *
   * @return 数据源 key 映射
   */
  public Map<String, String> getDatasourceMapping() {
    return datasourceMapping != null ? datasourceMapping : Collections.emptyMap();
  }

  /**
   * 获取 per-tenant Schema 名称映射（SCHEMA 模式）。
   *
   * @return Schema 名称映射（key=租户 ID，value=schema 名称）
   * @since 26.09.19
   */
  public Map<String, String> getSchemaMapping() {
    return schemaMapping != null ? schemaMapping : Collections.emptyMap();
  }

  /**
   * 解析指定租户对应的 Schema 名称。
   *
   * <p>优先从 {@link #getSchemaMapping()} 配置读取；未命中时使用默认规则 {@code "tenant_" + tenantId}。
   *
   * @param tenantId 租户 ID（非空）
   * @return Schema 名称，不会为 null
   * @since 26.09.19
   */
  public String resolveSchemaName(String tenantId) {
    if (tenantId == null || tenantId.isEmpty()) {
      return "public";
    }
    if (schemaMapping != null && !schemaMapping.isEmpty()) {
      String mapped = schemaMapping.get(tenantId);
      if (mapped != null && !mapped.isBlank()) {
        return mapped.strip();
      }
    }
    return "tenant_" + tenantId;
  }

  /**
   * 当前是否为 SCHEMA 模式。
   *
   * @return true=SCHEMA 模式
   * @since 26.09.19
   */
  public boolean isSchemaMode() {
    return mode == TenantMode.SCHEMA;
  }

  /**
   * 获取 SQL 改写缓存配置。
   *
   * @return 缓存配置
   */
  public SqlCache getSqlCache() {
    return sqlCache != null ? sqlCache : new SqlCache();
  }
}
