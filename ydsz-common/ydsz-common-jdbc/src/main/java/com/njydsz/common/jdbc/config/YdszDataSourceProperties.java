package com.njydsz.common.jdbc.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一数据源配置属性（ydsz.datasource.*）。
 *
 * <p>Spring Boot 原生数据源配置使用 spring.datasource.* 前缀，
 * 为保持与项目 CONFIG-004 规范（ydzs.* 前缀）一致，
 * 本类读取 ydsz.datasource.* 并委托给 DynamicDataSourceAutoConfiguration 创建 HikariDataSource。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ConfigurationProperties(prefix = "ydsz.datasource")
public class YdszDataSourceProperties {

  /** JDBC 连接 URL */
  private String url;

  /** 数据库用户名 */
  private String username;

  /** 数据库密码 */
  private String password;

  /** JDBC 驱动类名 */
  private String driverClassName;

  /** HikariCP 连接池配置 */
  private Hikari hikari = new Hikari();

  /** 数据源类型 */
  private String type;

  public String getUrl() { return url; }
  public void setUrl(String url) { this.url = url; }

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }

  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }

  public String getDriverClassName() { return driverClassName; }
  public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }

  public Hikari getHikari() { return hikari; }
  public void setHikari(Hikari hikari) { this.hikari = hikari; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  /**
   * HikariCP 连接池参数。
   */
  public static class Hikari {
    private String poolName;
    private int minimumIdle = 5;
    private int maximumPoolSize = 20;
    private long connectionTimeout = 10000;
    private long idleTimeout = 600000;
    private long maxLifetime = 1800000;
    private String connectionTestQuery;
    private long validationTimeout = 5000;
    private long leakDetectionThreshold = 30000;
    private int initialSize = 10;
    private Map<String, String> dataSourceProperties = new HashMap<>();

    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }

    public int getMinimumIdle() { return minimumIdle; }
    public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }

    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }

    public long getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    public long getIdleTimeout() { return idleTimeout; }
    public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }

    public long getMaxLifetime() { return maxLifetime; }
    public void setMaxLifetime(long maxLifetime) { this.maxLifetime = maxLifetime; }

    public String getConnectionTestQuery() { return connectionTestQuery; }
    public void setConnectionTestQuery(String connectionTestQuery) { this.connectionTestQuery = connectionTestQuery; }

    public long getValidationTimeout() { return validationTimeout; }
    public void setValidationTimeout(long validationTimeout) { this.validationTimeout = validationTimeout; }

    public long getLeakDetectionThreshold() { return leakDetectionThreshold; }
    public void setLeakDetectionThreshold(long leakDetectionThreshold) { this.leakDetectionThreshold = leakDetectionThreshold; }

    public int getInitialSize() { return initialSize; }
    public void setInitialSize(int initialSize) { this.initialSize = initialSize; }

    public Map<String, String> getDataSourceProperties() { return dataSourceProperties; }
    public void setDataSourceProperties(Map<String, String> dataSourceProperties) { this.dataSourceProperties = dataSourceProperties; }
  }
}
