package com.njydsz.common.jdbc.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import com.njydsz.common.jdbc.handler.CreatedAtHandler;
import com.njydsz.common.jdbc.handler.CreatedByHandler;
import com.njydsz.common.jdbc.handler.FieldFillHandler;
import com.njydsz.common.jdbc.handler.UpdatedAtHandler;
import com.njydsz.common.jdbc.handler.UpdatedByHandler;
import com.njydsz.common.jdbc.interceptor.ColPermissionInnerInterceptor;
import com.njydsz.common.jdbc.interceptor.CombinedFieldFillInterceptor;
import com.njydsz.common.jdbc.interceptor.RowPermissionInnerInterceptor;
import com.njydsz.common.jdbc.interceptor.SqlFirewallInnerInterceptor;
import com.njydsz.common.jdbc.interceptor.SqlTraceInnerInterceptor;
import com.njydsz.common.jdbc.monitor.SqlAstCache;
import com.njydsz.common.jdbc.permission.DataPermissionContextResolver;
import com.njydsz.common.jdbc.permission.DataScopeIdExpander;
import com.njydsz.common.jdbc.spi.InnerInterceptorProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

/**
 * MyBatis Plus 配置类
 *
 * <p>配置 MyBatis Plus 的各种插件和拦截器，包括：
 *
 * <ul>
 *   <li>乐观锁拦截器：MP 内置 {@code OptimisticLockerInnerInterceptor}（配合实体 {@code @Version} 注解）
 *   <li>字段填充拦截器：自动填充 createdBy、createdAt、updatedBy、updatedAt
 *   <li>SPI 拦截器：外部模块通过 {@link InnerInterceptorProvider} 注入（如 common-tenant 的租户隔离）
 *   <li>数据权限拦截器：实现行级和列级数据权限控制
 *   <li>分页拦截器：支持多数据库类型的分页查询
 * </ul>
 *
 * <p><b>逻辑删除说明：</b>自 v1.9.0 起，统一采用 MP 原生 {@code @TableLogic} 注解实现逻辑删除， 替代自研的 {@code
 * LogicalDeleteInterceptor}。业务实体只需在 deleted 字段上标注 {@code @TableLogic} 即可。
 *
 * <p>拦截器执行顺序（按添加顺序）：
 *
 * <ol>
 *   <li>OptimisticLocker - 乐观锁（内置 @Version）
 *   <li>FieldFillInterceptor - 字段填充
 *   <li>SPI Interceptors - 外部模块通过 {@link InnerInterceptorProvider} SPI 注入（按 order 排序）
 *   <li>DataPermissionInnerInterceptor - 数据权限（行级+列级）
 *   <li>PaginationInnerInterceptor - 分页
 * </ol>
 *
 * <p><b>SPI 扩展机制：</b>外部公共模块（如 common-tenant）通过实现 {@link InnerInterceptorProvider} 接口并注册为 Spring
 * Bean， 即可自动将拦截器插入链中，common-jdbc 无需硬依赖外部模块。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MybatisPlusInterceptor
 * @see InnerInterceptorProvider
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({
  FieldFillConfiguration.class,
  DataPermissionConfiguration.class,
  PaginationProperties.class,
  SqlFirewallProperties.class
})
@ConditionalOnProperty(prefix = "ydsz.jdbc", name = "enabled", matchIfMissing = true)
public class MybatisPlusConfiguration {

  private final FieldFillConfiguration fieldFillConfiguration;
  private final DataPermissionConfiguration dataPermissionConfiguration;
  private final ObjectProvider<DataScopeIdExpander> dataScopeIdExpanderProvider;
  private final PaginationProperties paginationProperties;
  private final SqlFirewallProperties sqlFirewallProperties;
  private final ObjectProvider<List<InnerInterceptorProvider>> spiInterceptorProviders;
  private final SqlAstCache sqlAstCache;

  public MybatisPlusConfiguration(
      FieldFillConfiguration fieldFillConfiguration,
      DataPermissionConfiguration dataPermissionConfiguration,
      ObjectProvider<DataScopeIdExpander> dataScopeIdExpanderProvider,
      PaginationProperties paginationProperties,
      SqlFirewallProperties sqlFirewallProperties,
      ObjectProvider<List<InnerInterceptorProvider>> spiInterceptorProviders,
      SqlAstCache sqlAstCache) {
    this.fieldFillConfiguration = fieldFillConfiguration;
    this.dataPermissionConfiguration = dataPermissionConfiguration;
    this.dataScopeIdExpanderProvider = dataScopeIdExpanderProvider;
    this.paginationProperties = paginationProperties;
    this.sqlFirewallProperties = sqlFirewallProperties;
    this.spiInterceptorProviders = spiInterceptorProviders;
    this.sqlAstCache = sqlAstCache;
  }

  /**
   * 配置 MyBatis Plus 拦截器链
   *
   * <p>按顺序添加以下拦截器：
   *
   * <ol>
   *   <li>乐观锁拦截器（MP 内置，配合实体 @Version 注解）
   *   <li>字段填充拦截器（针对非实体类的更新操作）
   *   <li>SPI 拦截器（外部模块通过 {@link InnerInterceptorProvider} 注入，按 order 排序）
   *   <li>数据权限拦截器（行级+列级）
   *   <li>分页拦截器（动态适配数据库类型）
   * </ol>
   *
   * <p><b>注意：</b>逻辑删除已迁移至 MP 原生 {@code @TableLogic} 注解，无需拦截器处理。
   *
   * @return MybatisPlusInterceptor 实例
   * @see InnerInterceptorProvider
   * @see PaginationInnerInterceptor
   */
  @Bean
  @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

    // 1. 乐观锁拦截器（MP 内置，处理实体 @Version 字段的参数映射与版本递增）
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    log.debug(
        "MyBatis Plus: OptimisticLockerInnerInterceptor (built-in) enabled for @Version entities");

    // 2. 字段填充拦截器（合并多 Handler，单次 SQL 解析完成所有字段填充）
    configureFieldFillInterceptors(interceptor);

    // 3. SPI 拦截器（外部模块通过 InnerInterceptorProvider 注入，按 order 排序）
    //    常见用途：common-tenant 的 TenantIsolationInterceptor（order=400）
    //    在数据权限之前注入，确保 tenant_id 条件优先追加
    List<InnerInterceptorProvider> providers =
        spiInterceptorProviders.getIfAvailable(Collections::emptyList);
    if (providers != null && !providers.isEmpty()) {
      providers.stream()
          .sorted(Comparator.comparingInt(InnerInterceptorProvider::getOrder))
          .forEach(
              provider -> {
                interceptor.addInnerInterceptor(provider.createInterceptor());
                log.info(
                    "MyBatis Plus: SPI interceptor loaded [{}] order={}",
                    provider.createInterceptor().getClass().getSimpleName(),
                    provider.getOrder());
              });
    }

    // 4. 数据权限拦截器（行级+列级）
    configureDataPermissionInterceptor(interceptor);

    // 5. 分页拦截器（支持显式指定 DbType + maxLimit 安全加固）
    PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor();
    String dbType = paginationProperties.getDbType();
    if (dbType != null && !dbType.isEmpty()) {
      paginationInterceptor.setDbType(DbType.getDbType(dbType));
    }
    if (paginationProperties.getMaxLimit() != null && paginationProperties.getMaxLimit() > 0) {
      paginationInterceptor.setMaxLimit(paginationProperties.getMaxLimit());
    }
    paginationInterceptor.setOverflow(paginationProperties.isOverflow());
    interceptor.addInnerInterceptor(paginationInterceptor);

    // 6. SQL 防火墙拦截器（置于拦截器链末端，在所有 SQL 改写完成后做安全校验）
    if (sqlFirewallProperties != null && sqlFirewallProperties.isEnabled()) {
      SqlFirewallInnerInterceptor firewall = new SqlFirewallInnerInterceptor();
      firewall.setEnabled(true);
      firewall.setBlockDropTable(sqlFirewallProperties.isBlockDropTable());
      firewall.setBlockTruncate(sqlFirewallProperties.isBlockTruncate());
      firewall.setBlockDeleteWithoutWhere(sqlFirewallProperties.isBlockDeleteWithoutWhere());
      firewall.setBlockUpdateWithoutWhere(sqlFirewallProperties.isBlockUpdateWithoutWhere());
      firewall.setBlockMultiStatement(sqlFirewallProperties.isBlockMultiStatement());
      firewall.setBlockPermissionOps(sqlFirewallProperties.isBlockPermissionOps());
      firewall.setAllowTables(sqlFirewallProperties.getAllowTables());
      interceptor.addInnerInterceptor(firewall);
      log.debug("MyBatis Plus: SqlFirewall interceptor enabled");
    }

    return interceptor;
  }

  /** 配置字段填充拦截器 */
  private void configureFieldFillInterceptors(MybatisPlusInterceptor interceptor) {
    List<FieldFillHandler> enabledHandlers = new ArrayList<>(4);
    if (fieldFillConfiguration.getCreatedByIntercept().getEnabled()) {
      enabledHandlers.add(new CreatedByHandler(fieldFillConfiguration));
    }
    if (fieldFillConfiguration.getUpdateByIntercept().getEnabled()) {
      enabledHandlers.add(new UpdatedByHandler(fieldFillConfiguration));
    }
    if (fieldFillConfiguration.getCreateAtIntercept().getEnabled()) {
      enabledHandlers.add(new CreatedAtHandler(fieldFillConfiguration));
    }
    if (fieldFillConfiguration.getUpdateAtIntercept().getEnabled()) {
      enabledHandlers.add(new UpdatedAtHandler(fieldFillConfiguration));
    }
    if (enabledHandlers.isEmpty()) {
      return;
    }
    interceptor.addInnerInterceptor(new CombinedFieldFillInterceptor(sqlAstCache, enabledHandlers));
    log.debug(
        "MyBatis Plus: CombinedFieldFill interceptor enabled with {} handlers.",
        enabledHandlers.size());
  }

  /**
   * 配置数据权限拦截器
   *
   * <p>当启用数据权限时，同时注册行级和列级权限拦截器。 数据权限拦截器会根据当前用户的权限范围自动改写 SQL， 实现行级和列级的数据访问控制。
   *
   * @param interceptor MyBatis Plus 拦截器链
   */
  private void configureDataPermissionInterceptor(MybatisPlusInterceptor interceptor) {
    if (Boolean.TRUE.equals(dataPermissionConfiguration.getEnabled())) {
      DataScopeIdExpander expander =
          dataScopeIdExpanderProvider == null ? null : dataScopeIdExpanderProvider.getIfAvailable();
      DataPermissionContextResolver resolver = new DataPermissionContextResolver(expander);

      interceptor.addInnerInterceptor(
          new RowPermissionInnerInterceptor(sqlAstCache, dataPermissionConfiguration, resolver));
      interceptor.addInnerInterceptor(
          new ColPermissionInnerInterceptor(sqlAstCache, dataPermissionConfiguration, resolver));
      log.debug("MyBatis Plus: RowPermission + ColPermission interceptors enabled.");
    }
  }

  // ====================================================================
  // 启动期 Banner 打印
  // ====================================================================

  /**
   * 启动完成后打印能力概览 Banner
   *
   * <p>通过 {@link ApplicationReadyEvent} 确保在 Spring 容器完全就绪后执行， 此时所有 Bean 均已初始化完毕。结构化单行输出便于 ELK/Loki
   * 采集， 避免多行 ASCII art 在容器日志中产生噪音。
   *
   * @param event 应用就绪事件
   */
  @EventListener(ApplicationReadyEvent.class)
  public void printCapabilityBanner(ApplicationReadyEvent event) {
    ApplicationContext ctx = event.getApplicationContext();

    boolean fieldFill =
        fieldFillConfiguration.getCreatedByIntercept().getEnabled()
            || fieldFillConfiguration.getUpdateByIntercept().getEnabled()
            || fieldFillConfiguration.getCreateAtIntercept().getEnabled()
            || fieldFillConfiguration.getUpdateAtIntercept().getEnabled();
    boolean dataPermission = Boolean.TRUE.equals(dataPermissionConfiguration.getEnabled());
    boolean sqlFirewall = sqlFirewallProperties != null && sqlFirewallProperties.isEnabled();
    boolean sqlTrace = isSqlTraceEnabled(ctx);
    int spiCount = spiInterceptorProviders.getIfAvailable(Collections::emptyList).size();
    String dataSourceInfo = buildDataSourceInfo(ctx);
    String dbType =
        paginationProperties.getDbType() != null ? paginationProperties.getDbType() : "auto";

    // 结构化单行输出，兼容 ELK/Loki 采集
    log.info(
        "[ydsz-common-jdbc] capabilities: "
            + "optimisticLock=true, fieldFill={}, dataPermission={}, "
            + "sqlFirewall={}, sqlTrace={}, pagination={}, "
            + "spiExtensions={}, dataSources={}",
        fieldFill,
        dataPermission,
        sqlFirewall,
        sqlTrace,
        dbType,
        spiCount,
        dataSourceInfo);
  }

  /** 探测 SqlTraceInnerInterceptor 是否已注册到拦截器链 */
  private boolean isSqlTraceEnabled(ApplicationContext ctx) {
    try {
      MybatisPlusInterceptor interceptor = ctx.getBean(MybatisPlusInterceptor.class);
      for (Object inner : interceptor.getInterceptors()) {
        if (inner instanceof SqlTraceInnerInterceptor) {
          return true;
        }
      }
    } catch (NoSuchBeanDefinitionException ignored) {
      // 拦截器链 Bean 不存在
    }
    return false;
  }

  /** 构建数据源信息描述字符串 */
  private String buildDataSourceInfo(ApplicationContext ctx) {
    try {
      DynamicRoutingDataSource routingDs =
          ctx.getBeanProvider(DynamicRoutingDataSource.class).getIfAvailable();
      if (routingDs != null) {
        int count = routingDs.getDataSources().size();
        return count + " registered";
      }
    } catch (NoSuchBeanDefinitionException ignored) {
      // 动态路由数据源不存在
    }
    return "single";
  }
}
