package com.njydsz.common.jdbc.config;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.SmartLifecycle;

import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;

/**
 * 多数据源连接池参数定制器
 *
 * <p>当项目中使用了 {@link DynamicRoutingDataSource} 时， 此 {@link SmartLifecycle} Bean
 * 在所有数据源初始化完成后，遍历所有目标数据源， 对每个 {@link HikariDataSource} 执行连接池参数定制。
 *
 * <p>基础连接池配置由 Spring Boot 原生自动配置处理， 本配置仅服务于多数据源场景下的差异化定制需求。
 *
 * <p>业务方可以通过实现 {@link HikariCPPoolConfigurer} 接口来为特定数据源定制连接池参数：
 *
 * <pre>{@code
 * &#64;Bean
 * public HikariCPPoolConfigurer hikariCPPoolConfigurer() {
 *     return (dsName, config) -> {
 *         if ("slave".equals(dsName)) {
 *             config.setMaximumPoolSize(10);
 *         }
 *     };
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see HikariCPPoolConfigurer
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({DynamicRoutingDataSource.class, HikariDataSource.class})
@ConditionalOnBean(DynamicRoutingDataSource.class)
public class MultiDataSourcePoolCustomizer implements SmartLifecycle {

  private final DynamicRoutingDataSource dynamicRoutingDataSource;
  private final ObjectProvider<List<HikariCPPoolConfigurer>> poolConfigurerProvider;
  private volatile boolean running = false;

  public MultiDataSourcePoolCustomizer(
      DynamicRoutingDataSource dynamicRoutingDataSource,
      ObjectProvider<List<HikariCPPoolConfigurer>> poolConfigurerProvider) {
    this.dynamicRoutingDataSource = dynamicRoutingDataSource;
    this.poolConfigurerProvider = poolConfigurerProvider;
  }

  @Override
  public void start() {
    Map<Object, DataSource> targetDataSources = dynamicRoutingDataSource.getDataSources();
    if (targetDataSources == null || targetDataSources.isEmpty()) {
      log.warn("多数据源路由中未找到目标数据源，跳过连接池定制");
      this.running = true;
      return;
    }

    List<HikariCPPoolConfigurer> configurers = poolConfigurerProvider.getIfAvailable();
    if (configurers == null || configurers.isEmpty()) {
      log.debug("未找到 HikariCPPoolConfigurer 实现，跳过多数据源连接池定制");
      this.running = true;
      return;
    }

    int customized = 0;
    int skipped = 0;

    for (Map.Entry<Object, DataSource> entry : targetDataSources.entrySet()) {
      String dsName = entry.getKey().toString();
      DataSource ds = entry.getValue();

      if (ds instanceof HikariDataSource) {
        applyPoolConfig(dsName, (HikariDataSource) ds, configurers);
        customized++;
      } else {
        log.warn("数据源 [{}] 非 HikariDataSource 类型，跳过连接池定制: {}", dsName, ds.getClass().getName());
        skipped++;
      }
    }

    log.info("多数据源连接池定制完成: 已定制 {} 个，跳过 {} 个", customized, skipped);
    this.running = true;
  }

  @Override
  public void stop() {
    this.running = false;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  @Override
  public int getPhase() {
    // 在大多数生命周期 bean 之后执行，确保所有数据源已初始化
    return Integer.MAX_VALUE - 100;
  }

  /**
   * 为指定 HikariCP 数据源应用连接池配置
   *
   * <p>从 {@link HikariDataSource} 的 {@link HikariConfigMXBean} 读取当前配置快照， 依次调用所有 {@link
   * HikariCPPoolConfigurer}，最后将修改后的配置通过 MXBean 热更新。
   *
   * @param dsName 数据源名称
   * @param hikariDs HikariCP 数据源
   * @param configurers 连接池定制器列表
   */
  private void applyPoolConfig(
      String dsName, HikariDataSource hikariDs, List<HikariCPPoolConfigurer> configurers) {
    HikariConfigMXBean mxBean = hikariDs.getHikariConfigMXBean();

    // 创建配置快照供业务定制
    HikariConfig snapshot = new HikariConfig();
    snapshot.setMinimumIdle(mxBean.getMinimumIdle());
    snapshot.setMaximumPoolSize(mxBean.getMaximumPoolSize());
    snapshot.setConnectionTimeout(mxBean.getConnectionTimeout());
    snapshot.setIdleTimeout(mxBean.getIdleTimeout());
    snapshot.setMaxLifetime(mxBean.getMaxLifetime());
    snapshot.setValidationTimeout(mxBean.getValidationTimeout());
    snapshot.setLeakDetectionThreshold(mxBean.getLeakDetectionThreshold());
    snapshot.setPoolName(mxBean.getPoolName());

    // 依次调用所有配置器
    for (HikariCPPoolConfigurer configurer : configurers) {
      try {
        configurer.configure(dsName, snapshot);
      } catch (Exception e) {
        log.warn("HikariCPPoolConfigurer 执行异常，数据源: {}, 错误: {}", dsName, e.getMessage());
      }
    }

    // 将修改后的配置通过 MXBean 热更新到运行中的连接池（仅应用有变化的值）
    if (snapshot.getMinimumIdle() != mxBean.getMinimumIdle()) {
      mxBean.setMinimumIdle(snapshot.getMinimumIdle());
    }
    if (snapshot.getMaximumPoolSize() != mxBean.getMaximumPoolSize()) {
      mxBean.setMaximumPoolSize(snapshot.getMaximumPoolSize());
    }
    if (snapshot.getConnectionTimeout() != mxBean.getConnectionTimeout()) {
      mxBean.setConnectionTimeout(snapshot.getConnectionTimeout());
    }
    if (snapshot.getIdleTimeout() != mxBean.getIdleTimeout()) {
      mxBean.setIdleTimeout(snapshot.getIdleTimeout());
    }
    if (snapshot.getMaxLifetime() != mxBean.getMaxLifetime()) {
      mxBean.setMaxLifetime(snapshot.getMaxLifetime());
    }
    if (snapshot.getValidationTimeout() != mxBean.getValidationTimeout()) {
      mxBean.setValidationTimeout(snapshot.getValidationTimeout());
    }
    if (snapshot.getLeakDetectionThreshold() != mxBean.getLeakDetectionThreshold()) {
      mxBean.setLeakDetectionThreshold(snapshot.getLeakDetectionThreshold());
    }

    log.info(
        "数据源 [{}] 连接池已定制: poolName={}, maxPoolSize={}, minIdle={}, connectionTimeout={}ms",
        dsName,
        mxBean.getPoolName(),
        mxBean.getMaximumPoolSize(),
        mxBean.getMinimumIdle(),
        mxBean.getConnectionTimeout());
  }
}
