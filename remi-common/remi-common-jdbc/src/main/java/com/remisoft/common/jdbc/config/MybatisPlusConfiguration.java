package com.remisoft.common.jdbc.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.remisoft.common.jdbc.handler.CreatedAtHandler;
import com.remisoft.common.jdbc.handler.CreatedByHandler;
import com.remisoft.common.jdbc.handler.FieldFillHandler;
import com.remisoft.common.jdbc.handler.MyMetaObjectHandler;
import com.remisoft.common.jdbc.handler.UpdatedAtHandler;
import com.remisoft.common.jdbc.handler.UpdatedByHandler;
import com.remisoft.common.jdbc.interceptor.ColPermissionInnerInterceptor;
import com.remisoft.common.jdbc.interceptor.CombinedFieldFillInterceptor;
import com.remisoft.common.jdbc.interceptor.LogicalDeleteInterceptor;
import com.remisoft.common.jdbc.interceptor.OptimisticLockInterceptor;
import com.remisoft.common.jdbc.interceptor.RowPermissionInnerInterceptor;
import com.remisoft.common.jdbc.interceptor.SqlFirewallInnerInterceptor;
import com.remisoft.common.jdbc.permission.DataPermissionContextResolver;
import com.remisoft.common.jdbc.permission.DataScopeIdExpander;
import com.remisoft.common.jdbc.spi.InnerInterceptorProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis Plus 配置类
 *
 * <p>配置 MyBatis Plus 的各种插件和拦截器，包括：
 * <ul>
 *   <li>乐观锁拦截器：二选一——自定义实现（BaseEntity + revision 列）或内置 OptimisticLockerInnerInterceptor（@Version 注解）</li>
 *   <li>逻辑删除拦截器（自定义实现）：自动追加 deleted 过滤条件，替代 @TableLogic 注解</li>
 *   <li>字段填充拦截器：自动填充 createdBy、createdAt、updatedBy、updatedAt</li>
 *   <li>SPI 拦截器：外部模块通过 {@link InnerInterceptorProvider} 注入（如 common-tenant 的租户隔离）</li>
 *   <li>数据权限拦截器：实现行级和列级数据权限控制</li>
 *   <li>分页拦截器：支持多数据库类型的分页查询</li>
 * </ul>
 *
 * <p>拦截器执行顺序（按添加顺序）：
 * <ol>
 *   <li>OptimisticLocker - 乐观锁（自定义 或 内置，二选一）</li>
 *   <li>LogicalDeleteInterceptor - 逻辑删除（SELECT/DELETE）</li>
 *   <li>FieldFillInterceptor - 字段填充</li>
 *   <li>SPI Interceptors - 外部模块通过 {@link InnerInterceptorProvider} SPI 注入（按 order 排序）</li>
 *   <li>DataPermissionInnerInterceptor - 数据权限（行级+列级）</li>
 *   <li>PaginationInnerInterceptor - 分页</li>
 * </ol>
 *
 * <p><b>SPI 扩展机制：</b>外部公共模块（如 common-tenant）通过实现
 * {@link InnerInterceptorProvider} 接口并注册为 Spring Bean，
 * 即可自动将拦截器插入链中，common-jdbc 无需硬依赖外部模块。
 *
 * @author remi-team
 * @since 1.0.0
 * @see MybatisPlusInterceptor
 * @see OptimisticLockInterceptor
 * @see LogicalDeleteInterceptor
 * @see InnerInterceptorProvider
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({
    FieldFillConfiguration.class,
    DataPermissionConfiguration.class,
    OptimisticLockConfiguration.class,
    LogicalDeleteConfiguration.class,
    PaginationProperties.class,
    SqlFirewallProperties.class,
    ReadWriteSplittingProperties.class,
    CircuitBreakerProperties.class
})
@ConditionalOnProperty(prefix = "remi.jdbc", name = "enabled", matchIfMissing = true)
public class MybatisPlusConfiguration {

    private final FieldFillConfiguration fieldFillConfiguration;
    private final DataPermissionConfiguration dataPermissionConfiguration;
    private final ObjectProvider<DataScopeIdExpander> dataScopeIdExpanderProvider;
    private final OptimisticLockConfiguration optimisticLockConfiguration;
    private final LogicalDeleteConfiguration logicalDeleteConfiguration;
    private final PaginationProperties paginationProperties;
    private final SqlFirewallProperties sqlFirewallProperties;
    private final ReadWriteSplittingProperties readWriteSplittingProperties;
    private final CircuitBreakerProperties circuitBreakerProperties;
    private final ObjectProvider<List<InnerInterceptorProvider>> spiInterceptorProviders;

    public MybatisPlusConfiguration(FieldFillConfiguration fieldFillConfiguration,
                                     DataPermissionConfiguration dataPermissionConfiguration,
                                     ObjectProvider<DataScopeIdExpander> dataScopeIdExpanderProvider,
                                     OptimisticLockConfiguration optimisticLockConfiguration,
                                     LogicalDeleteConfiguration logicalDeleteConfiguration,
                                     PaginationProperties paginationProperties,
                                     SqlFirewallProperties sqlFirewallProperties,
                                     ReadWriteSplittingProperties readWriteSplittingProperties,
                                     CircuitBreakerProperties circuitBreakerProperties,
                                     ObjectProvider<List<InnerInterceptorProvider>> spiInterceptorProviders) {
        this.fieldFillConfiguration = fieldFillConfiguration;
        this.dataPermissionConfiguration = dataPermissionConfiguration;
        this.dataScopeIdExpanderProvider = dataScopeIdExpanderProvider;
        this.optimisticLockConfiguration = optimisticLockConfiguration;
        this.logicalDeleteConfiguration = logicalDeleteConfiguration;
        this.paginationProperties = paginationProperties;
        this.sqlFirewallProperties = sqlFirewallProperties;
        this.readWriteSplittingProperties = readWriteSplittingProperties;
        this.circuitBreakerProperties = circuitBreakerProperties;
        this.spiInterceptorProviders = spiInterceptorProviders;
    }

    /**
     * 配置 MyBatis Plus 拦截器链
     *
     * <p>按顺序添加以下拦截器：
     * <ol>
     *   <li>乐观锁拦截器（自定义实现，替代@Version）</li>
     *   <li>逻辑删除拦截器（自定义实现，替代@TableLogic）</li>
     *   <li>字段填充拦截器（针对非实体类的更新操作）</li>
     *   <li>SPI 拦截器（外部模块通过 {@link InnerInterceptorProvider} 注入，按 order 排序）</li>
     *   <li>数据权限拦截器（行级+列级）</li>
     *   <li>分页拦截器（动态适配数据库类型）</li>
     * </ol>
     *
     * @return MybatisPlusInterceptor 实例
     * @see OptimisticLockInterceptor
     * @see LogicalDeleteInterceptor
     * @see InnerInterceptorProvider
     * @see PaginationInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 乐观锁拦截器
        if (Boolean.TRUE.equals(optimisticLockConfiguration.isEnable())) {
            OptimisticLockInterceptor optimisticLockInterceptor = new OptimisticLockInterceptor();
            optimisticLockInterceptor.setRevisionColumn(optimisticLockConfiguration.getRevisionColumn());
            optimisticLockInterceptor.setDefaultRevisionValue(optimisticLockConfiguration.getDefaultRevisionValue());
            interceptor.addInnerInterceptor(optimisticLockInterceptor);
            log.debug("MyBatis Plus: OptimisticLock interceptor (custom) enabled (revisionColumn={})",
                    optimisticLockConfiguration.getRevisionColumn());
        } else {
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            log.debug("MyBatis Plus: OptimisticLockerInnerInterceptor (built-in) enabled for @Version entities");
        }

        // 2. 逻辑删除拦截器（自定义实现）
        if (Boolean.TRUE.equals(logicalDeleteConfiguration.isEnable())) {
            LogicalDeleteInterceptor logicalDeleteInterceptor = new LogicalDeleteInterceptor();
            logicalDeleteInterceptor.setDeletedColumn(logicalDeleteConfiguration.getDeletedColumn());
            logicalDeleteInterceptor.setDeletedValue(logicalDeleteConfiguration.getDeletedValue());
            logicalDeleteInterceptor.setNormalValue(logicalDeleteConfiguration.getNormalValue());
            logicalDeleteInterceptor.setIgnoreTables(logicalDeleteConfiguration.getNormalizedIgnoreTables());
            interceptor.addInnerInterceptor(logicalDeleteInterceptor);
            log.debug("MyBatis Plus: LogicalDelete interceptor enabled (deletedColumn={}, ignoreTables={})",
                    logicalDeleteConfiguration.getDeletedColumn(),
                    logicalDeleteConfiguration.getNormalizedIgnoreTables());
        }

        // 3. 字段填充拦截器（合并多 Handler，单次 SQL 解析完成所有字段填充）
        configureFieldFillInterceptors(interceptor);

        // 4. SPI 拦截器（外部模块通过 InnerInterceptorProvider 注入，按 order 排序）
        //    常见用途：common-tenant 的 TenantIsolationInterceptor（order=400）
        //    在数据权限之前注入，确保 tenant_id 条件优先追加
        List<InnerInterceptorProvider> providers = spiInterceptorProviders.getIfAvailable(Collections::emptyList);
        if (providers != null && !providers.isEmpty()) {
            providers.stream()
                .sorted(Comparator.comparingInt(InnerInterceptorProvider::getOrder))
                .forEach(provider -> {
                    interceptor.addInnerInterceptor(provider.createInterceptor());
                    log.info("MyBatis Plus: SPI interceptor loaded [{}] order={}",
                        provider.createInterceptor().getClass().getSimpleName(),
                        provider.getOrder());
                });
        }

        // 5. 数据权限拦截器（行级+列级）
        configureDataPermissionInterceptor(interceptor);

        // 6. 分页拦截器（支持显式指定 DbType + maxLimit 安全加固）
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

        // 7. SQL 防火墙拦截器（置于拦截器链末端，在所有 SQL 改写完成后做安全校验）
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

        // 读写分离状态日志
        if (readWriteSplittingProperties != null && readWriteSplittingProperties.isEnabled()) {
            log.info("MyBatis Plus: ReadWriteSplitting configured (master={}, slaves={})",
                    readWriteSplittingProperties.getMasterDs(), readWriteSplittingProperties.getSlaveDsList());
        }

        // 数据库熔断器状态日志
        if (circuitBreakerProperties != null && circuitBreakerProperties.isEnabled()) {
            log.info("MyBatis Plus: DatabaseCircuitBreaker configured (threshold={}, openDuration={}ms)",
                    circuitBreakerProperties.getFailureThreshold(), circuitBreakerProperties.getOpenDurationMillis());
        }

        return interceptor;
    }

    /**
     * 注册 MyBatis-Plus MetaObjectHandler，自动填充审计字段
     *
     * @return MyMetaObjectHandler 实例
     */
    @Bean
    @ConditionalOnMissingBean(MyMetaObjectHandler.class)
    public MyMetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
    }

    /**
     * 配置字段填充拦截器
     */
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
        interceptor.addInnerInterceptor(new CombinedFieldFillInterceptor(enabledHandlers));
        log.debug("MyBatis Plus: CombinedFieldFill interceptor enabled with {} handlers.",
                enabledHandlers.size());
    }

    /**
     * 配置数据权限拦截器
     *
     * <p>当启用数据权限时，同时注册行级和列级权限拦截器。
     * 数据权限拦截器会根据当前用户的权限范围自动改写 SQL，
     * 实现行级和列级的数据访问控制。
     *
     * @param interceptor MyBatis Plus 拦截器链
     */
    private void configureDataPermissionInterceptor(MybatisPlusInterceptor interceptor) {
        if (Boolean.TRUE.equals(dataPermissionConfiguration.getEnabled())) {
            DataScopeIdExpander expander = dataScopeIdExpanderProvider == null ? null : dataScopeIdExpanderProvider.getIfAvailable();
            DataPermissionContextResolver resolver = new DataPermissionContextResolver(expander);

            interceptor.addInnerInterceptor(new RowPermissionInnerInterceptor(dataPermissionConfiguration, resolver));
            interceptor.addInnerInterceptor(new ColPermissionInnerInterceptor(dataPermissionConfiguration, resolver));
            log.debug("MyBatis Plus: RowPermission + ColPermission interceptors enabled.");
        }
    }

    /**
     * 启动期校验：检查 OptimisticLockInterceptor 与 @Version 注解冲突
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateOptimisticLockConflict(ApplicationReadyEvent event) {
        if (!Boolean.TRUE.equals(optimisticLockConfiguration.isEnable())) {
            return;
        }

        List<String> entitiesWithVersion = new ArrayList<>();

        try {
            ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);

            String scanPattern = "classpath*:com/remisoft/**/*.class";
            Resource[] resources = resourcePatternResolver.getResources(scanPattern);

            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                    String className = metadataReader.getClassMetadata().getClassName();

                    try {
                        Class<?> entityClass = ClassUtils.forName(className, getClass().getClassLoader());

                        if (entityClass.isAnnotationPresent(TableName.class)) {
                            if (hasVersionAnnotation(entityClass)) {
                                entitiesWithVersion.add(entityClass.getName());
                            }
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        // 忽略无法加载的类
                    }
                }
            }
        } catch (Exception e) {
            log.warn("【MyBatis Plus 配置警告】扫描实体类时发生异常", e);
        }

        if (!entitiesWithVersion.isEmpty()) {
            log.warn("【MyBatis Plus 配置警告】OptimisticLockInterceptor 已启用，但以下实体类使用了 @Version 注解：{}。" +
                     "建议移除 @Version 注解，避免与自定义乐观锁拦截器冲突。", entitiesWithVersion);
        }
    }

    private boolean hasVersionAnnotation(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (AnnotationUtils.findAnnotation(field, Version.class) != null) {
                return true;
            }
        }
        return false;
    }
}
