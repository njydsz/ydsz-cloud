package com.njydsz.userinfo.server.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JDBC 身份供给连接器配置属性（P0-1 Identity Provisioning 管道）。
 *
 * <p>控制从外部业务数据库抽取用户数据的行为，包括数据源、查询 SQL、字段映射等。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.provision.jdbc}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     provision:
 *       jdbc:
 *         enabled: true
 *         datasource-name: biz_db
 *         user-query: "SELECT id, username, real_name, email, phone, dept_code, status, updated_at FROM biz_user WHERE deleted = 0"
 *         field-mapping:
 *           id: externalId
 *           username: username
 *           real_name: realName
 *           email: email
 *           phone: phone
 *           dept_code: departmentCode
 *           status: statusAttr
 *         cron: "0 0 3 * * ?"
 *         delete-orphaned: false
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.provision.jdbc")
public class JdbcProvisionProperties {

  /** 集合初始容量 */
  private static final int CAPACITY = 16;


  /** 是否启用 JDBC 供给连接器。 */
  private boolean enabled = false;

  /**
   * 数据源名称（对应 dynamic-datasource 配置中的数据源名）。
   *
   * <p>如 {@code biz_db}，需要在 dynamic-datasource 中配置该数据源连接信息。
   */
  private String datasourceName = "";

  /**
   * 用户查询 SQL。
   *
   * <p>必须返回至少包含以下字段的 ResultSet：
   * externalId（必填）、username（必填）、以及可选的 realName、email、phone、departmentCode 等。
   */
  private String userQuery = "";

  /**
   * 增量查询 SQL（可选）。
   *
   * <p>如配置了增量 SQL，将在 {@code WHERE} 条件中追加参数实现增量拉取。
   * 示例：{@code SELECT ... FROM biz_user WHERE deleted = 0 AND updated_at > ?}
   */
  private String incrementalQuery = "";

  /**
   * ResultSet 列名到 {@link com.njydsz.userinfo.domain.provision.ProvisionRecord} 字段的映射。
   *
   * <p>Key 为 ResultSet 列名（如 {@code real_name}），Value 为标准字段名
   * （{@code externalId / username / realName / email / phone / departmentCode / status}）。
   */
  private Map<String, String> fieldMapping = new HashMap<>(CAPACITY);

  /**
   * 定时同步 cron 表达式，默认每天凌晨 3 点。
   */
  private String cron = "0 0 3 * * ?";

  /** 是否删除外部源已不存在的本地用户（false 则仅停用）。 */
  private boolean deleteOrphaned = false;

  /** 是否-active 属性值映射（1/true/ENABLED 视为有效）。 */
  private String activeIndicator = "1";

  /** 分页大小（全量拉取时每次查询的 LIMIT）。 */
  private int batchSize = 500;
}
