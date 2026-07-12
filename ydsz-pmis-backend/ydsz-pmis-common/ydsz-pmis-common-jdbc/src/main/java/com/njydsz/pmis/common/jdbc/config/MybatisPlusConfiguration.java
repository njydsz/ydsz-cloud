package com.njydsz.pmis.common.jdbc.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.njydsz.pmis.common.jdbc.handler.CreatedAtHandler;
import com.njydsz.pmis.common.jdbc.handler.CreatedByHandler;
import com.njydsz.pmis.common.jdbc.handler.FieldFillHandler;
import com.njydsz.pmis.common.jdbc.handler.UpdatedAtHandler;
import com.njydsz.pmis.common.jdbc.handler.UpdatedByHandler;
import com.njydsz.pmis.common.jdbc.interceptor.CombinedFieldFillInterceptor;
import com.njydsz.pmis.common.jdbc.interceptor.ColPermissionInnerInterceptor;
import com.njydsz.pmis.common.jdbc.interceptor.LogicalDeleteInterceptor;
import com.njydsz.pmis.common.jdbc.interceptor.OptimisticLockInterceptor;
import com.njydsz.pmis.common.jdbc.interceptor.RowPermissionInnerInterceptor;
import com.njydsz.pmis.common.jdbc.permission.DataPermissionContext;
import com.njydsz.pmis.common.jdbc.permission.DataPermissionContextResolver;
import com.njydsz.pmis.common.jdbc.permission.DataScopeIdExpander;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis Plus 配置类
 *
 * <p>配置 MyBatis Plus 的各种插件和拦截器，包括：
 * <ul>
 *   <li>乐观锁拦截器（自定义实现）：自动追加 revision 版本号条件，替代 @Version 注解</li>
 *   <li>逻辑删除拦截器（自定义实现）：自动追加 deleted 过滤条件，替代 @TableLogic 注解</li>
 *   <li>字段填充拦截器：自动填充 createdBy、createdAt、updatedBy、updatedAt</li>
 *   <li>数据权限拦截器：实现行级和列级数据权限控制</li>
 *   <li>分页拦截器：支持多数据库类型的分页查询</li>
 * </ul>
 *
 * <p>拦截器执行顺序（按添加顺序）：
 * <ol>
 *   <li>OptimisticLockInterceptor - 乐观锁（INSERT/UPDATE）</li>
 *   <li>LogicalDeleteInterceptor - 逻辑删除（SELECT/DELETE）</li>
 *   <li>FieldFillInterceptor - 字段填充</li>
 *   <li>DataPermissionInnerInterceptor - 数据权限</li>
 *   <li>PaginationInnerInterceptor - 分页</li>
 * </ol>
 *
 * <p><b>优化建议：</b>
 * <ul>
 *   <li>分页拦截器建议指定数据库类型，避免自动检测性能开销</li>
 *   <li>字段填充建议统一使用实体类注解方式，减少拦截器开销</li>
 *   <li>数据权限拦截器建议在复杂场景下添加缓存</li>
 *   <li>乐观锁和逻辑删除拦截器与 @Version/@TableLogic 注解互斥，启用后请移除实体类注解</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see MybatisPlusInterceptor
 * @see com.njydsz.pmis.common.jdbc.interceptor.OptimisticLockInterceptor
 * @see com.njydsz.pmis.common.jdbc.interceptor.LogicalDeleteInterceptor
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({
    FieldFillConfiguration.class,
    DataPermissionConfiguration.class,
    OptimisticLockConfiguration.class,
    LogicalDeleteConfiguration.class,
    TenantIsolationProperties.class,
    PaginationProperties.class
})
@ConditionalOnProperty(prefix = "remi.jdbc", name = "enabled", matchIfMissing = true)
public class MybatisPlusConfiguration {

    private final FieldFillConfiguration fieldFillConfiguration;
    private final DataPermissionConfiguration dataPermissionConfiguration;
    private final ObjectProvider<DataScopeIdExpander> dataScopeIdExpanderProvider;
    private final OptimisticLockConfiguration optimisticLockConfiguration;
    private final LogicalDeleteConfiguration logicalDeleteConfiguration;
    private final TenantIsolationProperties tenantIsolationProperties;
    private final PaginationProperties paginationProperties;

    public MybatisPlusConfiguration(FieldFillConfiguration fieldFillConfiguration,
                                     DataPermissionConfiguration dataPermissionConfiguration,
                                     ObjectProvider<DataScopeIdExpander> dataScopeIdExpanderProvider,
                                     OptimisticLockConfiguration optimisticLockConfiguration,
                                     LogicalDeleteConfiguration logicalDeleteConfiguration,
                                     TenantIsolationProperties tenantIsolationProperties,
                                     PaginationProperties paginationProperties) {
        this.fieldFillConfiguration = fieldFillConfiguration;
        this.dataPermissionConfiguration = dataPermissionConfiguration;
        this.dataScopeIdExpanderProvider = dataScopeIdExpanderProvider;
        this.optimisticLockConfiguration = optimisticLockConfiguration;
        this.logicalDeleteConfiguration = logicalDeleteConfiguration;
        this.tenantIsolationProperties = tenantIsolationProperties;
        this.paginationProperties = paginationProperties;
    }

    /**
     * 配置 MyBatis Plus 拦截器链
     *
     * <p>按顺序添加以下拦截器：
     * <ol>
     *   <li>乐观锁拦截器（自定义实现，替代@Version）</li>
     *   <li>逻辑删除拦截器（自定义实现，替代@TableLogic）</li>
     *   <li>字段填充拦截器（针对非实体类的更新操作）</li>
     *   <li>数据权限拦截器（行级+列级）</li>
     *   <li>分页拦截器（动态适配数据库类型）</li>
     * </ol>
     *
     * @return MybatisPlusInterceptor 实例
     * @see OptimisticLockInterceptor
     * @see LogicalDeleteInterceptor
     * @see PaginationInnerInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 乐观锁拦截器（自定义实现）
        if (Boolean.TRUE.equals(optimisticLockConfiguration.isEnable())) {
            OptimisticLockInterceptor optimisticLockInterceptor = new OptimisticLockInterceptor();
            optimisticLockInterceptor.setRevisionColumn(optimisticLockConfiguration.getRevisionColumn());
            optimisticLockInterceptor.setDefaultRevisionValue(optimisticLockConfiguration.getDefaultRevisionValue());
            interceptor.addInnerInterceptor(optimisticLockInterceptor);
            log.debug("MyBatis Plus: OptimisticLock interceptor enabled (revisionColumn={})",
                    optimisticLockConfiguration.getRevisionColumn());
        }

        // 2. 逻辑删除拦截器（自定义实现）
        if (Boolean.TRUE.equals(logicalDeleteConfiguration.isEnable())) {
            LogicalDeleteInterceptor logicalDeleteInterceptor = new LogicalDeleteInterceptor();
            logicalDeleteInterceptor.setDeletedColumn(logicalDeleteConfiguration.getDeletedColumn());
            logicalDeleteInterceptor.setDeletedValue(logicalDeleteConfiguration.getDeletedValue());
            logicalDeleteInterceptor.setNormalValue(logicalDeleteConfiguration.getNormalValue());
            interceptor.addInnerInterceptor(logicalDeleteInterceptor);
            log.debug("MyBatis Plus: LogicalDelete interceptor enabled (deletedColumn={})",
                    logicalDeleteConfiguration.getDeletedColumn());
        }

        // 3. 字段填充拦截器（合并多 Handler，单次 SQL 解析完成所有字段填充）
        configureFieldFillInterceptors(interceptor);

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

        return interceptor;
    }

    /**
     * 配置字段填充拦截器
     *
     * <p>根据配置决定是否启用以下字段填充拦截器：
     * <ul>
     *   <li>CreatedByHandler: 插入时填充创建人ID</li>
     *   <li>UpdatedByHandler: 更新时填充更新人ID</li>
     *   <li>CreatedAtHandler: 插入时填充创建时间</li>
     *   <li>UpdatedAtHandler: 更新时填充更新时间</li>
     * </ul>
     *
     * <p>每个填充处理器都需要在配置文件中设置 enable=true 才会生效：
     * <pre>
     * remi:
     *   jdbc:
     *     field-fill:
     *       created-by-intercept:
     *         enabled: true
     *       update-by-intercept:
     *         enabled: true
     *       create-at-intercept:
     *         enabled: true
     *       update-at-intercept:
     *         enabled: true
     * </pre>
     *
     * @param interceptor MyBatis Plus 拦截器链
     * @see CombinedFieldFillInterceptor
     * @see FieldFillHandler
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
     * <p>数据权限配置示例：
     * <pre>
     * remi:
     *   sql-intercept:
     *     data-permission:
     *       enabled: true
     * </pre>
     *
     * @param interceptor MyBatis Plus 拦截器链
     * @see DataPermissionInnerInterceptor
     * @see RowPermissionInnerInterceptor
     * @see ColPermissionInnerInterceptor
     * @see DataPermissionConfiguration
     */
    private void configureDataPermissionInterceptor(MybatisPlusInterceptor interceptor) {
        if (Boolean.TRUE.equals(dataPermissionConfiguration.getEnabled())) {
            DataScopeIdExpander expander = dataScopeIdExpanderProvider == null ? null : dataScopeIdExpanderProvider.getIfAvailable();
            DataPermissionContextResolver resolver = new DataPermissionContextResolver(expander);
            // 初始化租户隔离开关 Supplier
            DataPermissionContext.initTenantIsolationEnabledSupplier(() ->
                    tenantIsolationProperties != null && tenantIsolationProperties.isEnabled());

            // 注册行级权限拦截器和列级权限拦截器
            interceptor.addInnerInterceptor(new RowPermissionInnerInterceptor(dataPermissionConfiguration, resolver, tenantIsolationProperties));
            interceptor.addInnerInterceptor(new ColPermissionInnerInterceptor(dataPermissionConfiguration, resolver));
            log.debug("MyBatis Plus: RowPermission + ColPermission interceptors enabled (tenantIsolation={}).",
                    tenantIsolationProperties != null ? tenantIsolationProperties.isEnabled() : true);
        }
    }

    /**
     * 启动期校验：检查 OptimisticLockInterceptor 与 @Version 注解冲突
     *
     * <p>当 OptimisticLockInterceptor 启用时，扫描所有实体类，若发现实体使用了 @Version 注解，
     * 输出 WARN 日志提醒用户移除 @Version 注解，避免与自定义拦截器冲突。
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateOptimisticLockConflict(ApplicationReadyEvent event) {
        if (!Boolean.TRUE.equals(optimisticLockConfiguration.isEnable())) {
            return;
        }

        // 扫描所有标注了 @TableName 的实体类
        List<String> entitiesWithVersion = new ArrayList<>();
        
        try {
            ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);
            
            // 扫描 classpath 下所有 .class 文件
            Resource[] resources = resourcePatternResolver.getResources("classpath*:/**/*.class");
            
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                    String className = metadataReader.getClassMetadata().getClassName();
                    
                    try {
                        Class<?> entityClass = ClassUtils.forName(className, getClass().getClassLoader());
                        
                        // 检查是否有 @TableName 注解
                        if (entityClass.isAnnotationPresent(TableName.class)) {
                            // 检查是否有 @Version 注解的字段
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

    /**
     * 检查实体类是否使用了 @Version 注解
     */
    private boolean hasVersionAnnotation(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (AnnotationUtils.findAnnotation(field, Version.class) != null) {
                return true;
            }
        }
        return false;
    }
}
