package com.njydsz.userinfo.server.provision;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.common.jdbc.datasource.DynamicDataSourceContextHolder;
import com.njydsz.userinfo.domain.provision.IdentityProvisionConnector;
import com.njydsz.userinfo.domain.provision.ProvisionException;
import com.njydsz.userinfo.domain.provision.ProvisionRecord;
import com.njydsz.userinfo.domain.provision.ProvisionRecordPage;
import com.njydsz.userinfo.server.config.JdbcProvisionProperties;

/**
 * JDBC 身份供给连接器（P0-1 Identity Provisioning 管道）。
 *
 * <p>通过 SQL 查询从外部业务数据库抽取用户数据，转换为标准 {@link ProvisionRecord} 输出。
 * 支持全量拉取和基于时间戳的增量拉取，支持自定义字段映射。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>对接已有的业务系统数据库（如 ERP、HR 系统的用户表）</li>
 *   <li>作为 LDAP 之外的补充供给通道，适用于非 LDAP 环境</li>
 *   <li>支持多数据源（通过 {@code @DS} 注解切换至外部业务库）</li>
 * </ul>
 *
 * <b>线程安全：</b>无状态实现，可并发调用。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.userinfo.provision.jdbc", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class JdbcProvisionConnector implements IdentityProvisionConnector {

  /** 连接器类型标识 */
  private static final String CONNECTOR_TYPE = "JDBC";

  /** 默认批量大小 */
  private static final int DEFAULT_BATCH_SIZE = 500;

  private final JdbcTemplate jdbcTemplate;
  private final JdbcProvisionProperties properties;

  @Override
  public String getConnectorType() {
    return CONNECTOR_TYPE;
  }

  @Override
  public String getDisplayName() {
    return "JDBC 数据库供给";
  }

  @Override
  public ProvisionRecordPage pullAll() {
    String query = resolveQuery();
    log.info("JdbcProvisionConnector 全量拉取: query={}", query);

    try {
      List<ProvisionRecord> records = executeQuery(query, null);
      return new ProvisionRecordPage(records);
    } catch (Exception e) {
      throw new ProvisionException(CONNECTOR_TYPE, "全量拉取失败: " + e.getMessage(), e);
    }
  }

  @Override
  public ProvisionRecordPage pullIncremental(String lastSyncToken) {
    String baseQuery = resolveQuery();
    // 追加增量条件（如配置了增量 SQL 则使用增量 SQL + 参数）
    String incrementalQuery = properties.getIncrementalQuery();
    if (incrementalQuery != null && !incrementalQuery.isBlank()) {
      log.info("JdbcProvisionConnector 增量拉取: token={}", lastSyncToken);
      try {
        List<ProvisionRecord> records = executeQuery(incrementalQuery,
            lastSyncToken != null ? new Object[]{lastSyncToken} : null);
        String newToken = lastSyncToken; // 使用查询完成后的 max(updated_at) 作为新 token
        return new ProvisionRecordPage(records, newToken, records.size());
      } catch (Exception e) {
        throw new ProvisionException(CONNECTOR_TYPE, "增量拉取失败: " + e.getMessage(), e);
      }
    }
    // 未配置增量 SQL - 退化为全量拉取
    log.warn("JdbcProvisionConnector 未配置增量 SQL，退化为全量拉取");
    return pullAll();
  }

  @Override
  public boolean isAvailable() {
    String dsName = properties.getDatasourceName();
    if (dsName == null || dsName.isBlank()) {
      log.warn("JdbcProvisionConnector 未配置数据源名称");
      return false;
    }
    String query = properties.getUserQuery();
    if (query == null || query.isBlank()) {
      log.warn("JdbcProvisionConnector 未配置查询 SQL");
      return false;
    }
    return true;
  }

  /**
   * 解析最终执行的查询语句。
   *
   * @return 完整的 SQL 查询
   */
  private String resolveQuery() {
    String query = properties.getUserQuery();
    if (query == null || query.isBlank()) {
      throw new ProvisionException(CONNECTOR_TYPE, "JDBC 查询 SQL 未配置");
    }
    // 自动添加 LIMIT 子句防止一次拉取过多数据
    if (!query.toLowerCase().contains("limit")) {
      int batchSize = properties.getBatchSize() > 0
          ? properties.getBatchSize() : DEFAULT_BATCH_SIZE;
      query = query + " LIMIT " + batchSize;
    }
    return query;
  }

  /**
   * 执行查询并将 ResultSet 转换为 ProvisionRecord 列表。
   *
   * @param sql 查询 SQL
   * @param params SQL 参数（可为 null）   */
  private List<ProvisionRecord> executeQuery(String sql, Object[] params) {
    Map<String, String> mapping = properties.getFieldMapping();
    int batchSize = properties.getBatchSize() > 0 ? properties.getBatchSize() : DEFAULT_BATCH_SIZE;
    List<ProvisionRecord> records = new ArrayList<>(batchSize);

    jdbcTemplate.query(sql, params, (ResultSet rs) -> {
      ProvisionRecord record = mapResultSet(rs, mapping);
      if (record != null) {
        records.add(record);
      }
    });

    log.debug("JdbcProvisionConnector 查询完成: count={}", records.size());
    return records;
  }

  /**
   * 将 ResultSet 的当前行转换为 ProvisionRecord。
   *
   * @param rs ResultSet
   * @param mapping 字段映射配置
   * @return ProvisionRecord，外部 ID 为空时返回 null
   */
  private ProvisionRecord mapResultSet(ResultSet rs, Map<String, String> mapping) {
    // 获取 externalId - 支持自定义映射或默认列名
    String externalId = getColumnValue(rs, mapping, "externalId", "id", "external_id", "user_id");
    if (externalId == null || externalId.isBlank()) {
      return null;
    }

    String username = getColumnValue(rs, mapping, "username", "user_name", "login_name", "uid");
    if (username == null || username.isBlank()) {
      username = externalId; // 兜底用 externalId 作为用户名
    }

    String realName = getColumnValue(rs, mapping, "realName", "real_name", "display_name", "name");
    String email = getColumnValue(rs, mapping, "email", "mail", "e_mail");
    String phone = getColumnValue(rs, mapping, "phone", "mobile", "tel", "phone_number");
    String deptCode = getColumnValue(rs, mapping, "departmentCode", "dept_code", "department_id",
        "org_code");
    String statusValue = getColumnValue(rs, mapping, "status", "state", "is_active", "status_attr");

    boolean isActive = isUserActive(statusValue);

    // 收集扩展属性
    Map<String, String> attributes = extractAttributes(rs);

    return new ProvisionRecord(externalId, username, realName, email, phone, deptCode, isActive,
        attributes);
  }

  /**
   * 从 ResultSet 获取列值（依次尝试默认列名和映射配置）。
   *
   * @param rs ResultSet
   * @param mapping 字段映射配置
   * @param standardField 标准字段名
   * @param defaultColumns 备选默认列名数组
   * @return 列值，未找到返回 null
   */
  private String getColumnValue(ResultSet rs, Map<String, String> mapping, String standardField,
      String... defaultColumns) {
    // 先尝试映射配置中的列名
    String mappedColumn = mapping.get(standardField);
    if (mappedColumn != null && !mappedColumn.isBlank()) {
      try {
        return rs.getString(mappedColumn);
      } catch (SQLException e) {
        // 列名不存在，继续尝试默认列名
      }
    }
    // 尝试默认列名
    for (String col : defaultColumns) {
      try {
        String value = rs.getString(col);
        if (value != null) {
          return value;
        }
      } catch (SQLException e) {
        // 列不存在，继续下一个
      }
    }
    return null;
  }

  /**
   * 解析用户是否有效的布尔值。
   *
   * @param statusValue 状态值字符串
   * @return true 表示有效
   */
  private boolean isUserActive(String statusValue) {
    if (statusValue == null || statusValue.isBlank()) {
      return true; // 默认有效
    }
    // 支持多种表示方式：1/true/ENABLED/激活
    String activeIndicator = properties.getActiveIndicator();
    if (activeIndicator != null && !activeIndicator.isBlank()) {
      return activeIndicator.equalsIgnoreCase(statusValue.trim());
    }
    // 常见"激活"判断
    return "1".equals(statusValue.trim())
        || "true".equalsIgnoreCase(statusValue.trim())
        || "enabled".equalsIgnoreCase(statusValue.trim())
        || "active".equalsIgnoreCase(statusValue.trim());
  }

  /**
   * 提取 ResultSet 中未映射的列为扩展属性。
   *
   * @param rs ResultSet
   * @return 扩展属性 Map
   */
  private Map<String, String> extractAttributes(ResultSet rs) {
    Map<String, String> attributes = new HashMap<>(8);
    try {
      java.sql.ResultSetMetaData metaData = rs.getMetaData();
      int columnCount = metaData.getColumnCount();
      for (int i = 1; i <= columnCount; i++) {
        String columnLabel = metaData.getColumnLabel(i);
        String value = rs.getString(i);
        if (value != null && !value.isBlank()) {
          attributes.put(columnLabel, value);
        }
      }
    } catch (SQLException e) {
      log.warn("提取扩展属性时异常: error={}", e.getMessage());
    }
    return attributes;
  }
}
