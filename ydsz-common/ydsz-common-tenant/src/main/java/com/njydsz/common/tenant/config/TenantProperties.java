package com.njydsz.common.tenant.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;

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
  private boolean enabled = false;

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
  private List<TenantField> tenantFields = new ArrayList<>();

  /**
   * per-table 列名覆盖映射。
   *
   * <p>key=表名（小写），value=列名。
   *
   * <p><b>注意：</b>当配置了 {@code tenant-fields} 时，此映射覆盖的是 <b>第一个字段</b>的列名（用于 per-table 不同列名场景）。
   */
  private Map<String, String> tableColumnMapping = new HashMap<>();

  /** 忽略租户隔离的表列表（忽略大小写）。 */
  private Set<String> ignoreTables = new HashSet<>();

  /** URL 级白名单（跳过租户隔离的请求路径）。 */
  private Set<String> anonUrls = new HashSet<>();

  /**
   * 跨租户数据共享配置。
   *
   * <p>允许指定租户访问其他租户的数据（如集团查看子公司）。 key=当前租户 ID，value=可访问的源租户 ID 列表。
   *
   * <pre>
   * ydsz:
   *   tenant:
   *     sharing:
   *       group_acme: ["acme_shanghai", "acme_beijing"]
   * </pre>
   */
  private Map<String, List<String>> tenantSharing = new HashMap<>();

  /**
   * ISOLATE_DB 模式下租户 → 数据源 Key 的映射。
   *
   * <p>未配置时使用命名约定（tenant_{tenantId}）。
   *
   * <pre>
   * ydsz:
   *   tenant:
   *     datasource:
   *       mapping:
   *         acme: "tenant_acme"
   *         globex: "ds_globex_read"
   * </pre>
   */
  private Map<String, String> datasourceMapping = new HashMap<>();

  /**
   * SQL 改写缓存配置。
   *
   * <p>开启后使用 ydsz-common-cache 缓存「原始 SQL + 租户字段签名」→ 改写结果， 减少 JSqlParser 重复解析开销。仅在热点 SQL 重复度高时有效，
   * MULTI 模式下因缓存 Key 包含全字段签名，命中率可能较低。
   *
   * <pre>
   * ydsz:
   *   tenant:
   *     sql-cache:
   *       enabled: true
   *       max-size: 2000
   *       expire-minutes: 10
   * </pre>
   *
   * @since 26.09.01
   */
  private SqlCacheConfig sqlCache = new SqlCacheConfig();

  /** SQL 改写缓存配置（内部类）。 */
  @Data
  public static class SqlCacheConfig {

    /** 是否启用 SQL 改写缓存（默认 false）。 */
    private boolean enabled = false;

    /** 缓存最大容量（默认 2000）。 */
    private int maxSize = 2000;

    /** 缓存访问后过期时间（分钟，默认 10）。 */
    private int expireMinutes = 10;
  }

  /**
   * 获取生效的租户字段列表。
   *
   * <p>SINGLE 模式只取第一个字段；MULTI 模式取全部字段。
   *
   * @return 生效的租户字段列表（不可变）
   */
  public List<TenantField> getActiveTenantFields() {
    if (tenantFields == null || tenantFields.isEmpty()) {
      TenantField defaultField = new TenantField();
      defaultField.setColumn(tenantColumn);
      defaultField.setClaim(defaultClaim);
      defaultField.setHeader(defaultHeader);
      return List.of(defaultField);
    }
    if (mode == TenantMode.SINGLE) {
      return List.of(tenantFields.get(0));
    }
    return Collections.unmodifiableList(tenantFields);
  }

  /**
   * 解析表对应的租户列名（per-table 覆盖第一个字段）。
   *
   * @param tableName 表名
   * @return 列名，无映射返回 null
   */
  public String resolveColumn(String tableName) {
    if (tableName == null || tableColumnMapping == null || tableColumnMapping.isEmpty()) {
      return null;
    }
    return tableColumnMapping.get(tableName.toLowerCase());
  }

  /**
   * 获取规范化后的忽略表集合（小写化）。
   *
   * @return 忽略表小写集合（空时为空集合）
   */
  public Set<String> getNormalizedIgnoreTables() {
    if (ignoreTables == null || ignoreTables.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> normalized = new HashSet<>(ignoreTables.size());
    for (String table : ignoreTables) {
      if (table != null) {
        normalized.add(table.trim().toLowerCase());
      }
    }
    return normalized;
  }

  /**
   * 获取规范化后的白名单 URL 集合（去空白）。
   *
   * @return 白名单 URL 去空白集合（空时为空集合）
   */
  public Set<String> getNormalizedAnonUrls() {
    if (anonUrls == null || anonUrls.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> normalized = new HashSet<>(anonUrls.size());
    for (String url : anonUrls) {
      if (url != null) {
        normalized.add(url.trim());
      }
    }
    return normalized;
  }

  /** 租户隔离模式。 */
  public enum TenantMode {
    /** 单租户模式：只取第一个字段 */
    SINGLE,
    /** 多级租户模式：取全部字段 */
    MULTI,
    /** 数据库隔离模式：每个租户使用独立数据源 */
    ISOLATE_DB,
    /** Schema 隔离模式：每个租户使用独立 PostgreSQL Schema（search_path） */
    SCHEMA
  }

  /**
   * 租户字段配置（完全动态，不依赖固定枚举）。
   *
   * <p>每个字段定义：数据库列名 + 值来源（JWT claim / HTTP header）。
   *
   * <p>多个字段组合形成多维度租户隔离。
   */
  @Data
  public static class TenantField {

    /**
     * 数据库列名（必填）。
     *
     * <p>例如：tenant_id / company_id / dept_id / project_id / region_id / 任意自定义列名。
     */
    @NotBlank private String column;

    /**
     * JWT claim 名（可选）。
     *
     * <p>从 JWT Token 中获取此 claim 的值作为该字段的值。
     *
     * <p>例如：tenantId / companyId / deptId / projectId / regionId / 任意自定义 claim。
     */
    private String claim;

    /**
     * HTTP header 名（可选，Feign 跨服务恢复用）。
     *
     * <p>当 JWT 不可用时，从此 header 获取值。
     *
     * <p>例如：X-Tenant-Id / X-Company-Ids / X-Dept-Ids / X-Project-Ids。
     */
    private String header;

    /**
     * 是否多值（默认 false）。
     *
     * <p>false → SQL: {@code WHERE column = ?}
     *
     * <p>true → SQL: {@code WHERE column IN (?, ?, ...)}
     *
     * <p>多值时，header/claim 的值用逗号分隔，如 "dept_001,dept_002"。
     */
    private boolean multiValue = false;

    public TenantField() {}

    public TenantField(String column) {
      this.column = column;
    }

    public TenantField(String column, String claim, String header) {
      this.column = column;
      this.claim = claim;
      this.header = header;
    }

    public TenantField(String column, String claim, String header, boolean multiValue) {
      this.column = column;
      this.claim = claim;
      this.header = header;
      this.multiValue = multiValue;
    }
  }
}
