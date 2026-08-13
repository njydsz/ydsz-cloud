package com.njydsz.common.jdbc.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import com.njydsz.common.jdbc.handler.CreatedAtHandler;
import com.njydsz.common.jdbc.handler.CreatedByHandler;
import com.njydsz.common.jdbc.handler.FieldFillHandler;
import com.njydsz.common.jdbc.handler.MyMetaObjectHandler;
import com.njydsz.common.jdbc.handler.UpdatedAtHandler;
import com.njydsz.common.jdbc.handler.UpdatedByHandler;
import com.njydsz.common.jdbc.interceptor.ColPermissionInnerInterceptor;
import com.njydsz.common.jdbc.interceptor.CombinedFieldFillInterceptor;
import com.njydsz.common.jdbc.interceptor.LogicalDeleteInterceptor;
import com.njydsz.common.jdbc.interceptor.RowPermissionInnerInterceptor;
import com.njydsz.common.jdbc.interceptor.SqlFirewallInnerInterceptor;
import com.njydsz.common.jdbc.interceptor.SqlTraceInnerInterceptor;
import com.njydsz.common.jdbc.permission.DataPermissionContextResolver;
import com.njydsz.common.jdbc.permission.DataScopeIdExpander;
import com.njydsz.common.jdbc.spi.InnerInterceptorProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis Plus 配置类
 *
 * <p>配置 MyBatis Plus 的各种插件和拦截器，包括：
 * <ul>
 *   <li>乐观锁拦截器：MP 内置 {@code OptimisticLockerInnerInterceptor}（配合实体 {@code @Version} 注解）</li>
 *   <li>逻辑删除拦截器（自定义实现）：自动追加 deleted 过滤条件，替代 @TableLogic 注解</li>
 *   <li>字段填充拦截器：自动填充 createdBy、createdAt、updatedBy、updatedAt</li>
 *   <li>SPI 拦截器：外部模块通过 {@link InnerInterceptorProvider} 注入（如 common-tenant 的租户隔离）</li>
 *   <li>数据权限拦截器：实现行级和列级数据权限控制</li>
 *   <li>分页拦截器：支持多数据库类型的分页查询</li>
 * </ul>
 *
 * <p>拦截器执行顺序（按添加顺序）：
 * <ol>
 *   <li>OptimisticLocker - 乐观锁（内置 @Version）</li>
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
 * @author ydsz-team
 * @since 1.0.0
 * @see MybatisPlusInterceptor
 * @see LogicalDeleteInterceptor
 * @see InnerInterceptorProvider
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({
    FieldFillConfiguration.class,
    DataPermissionConfiguration.class,
    LogicalDeleteConfiguration.class,
    PaginationProperties.class,
    SqlFirewallProperties.class,
    ReadWriteSplittingProperties.class,
    CircuitBreakerProperties.class
})
@ConditionalOnProperty(prefix = "ydsz.jdbc", name = "enabled", matchIfMissing = true)
public class MybatisPlusConfiguration {

    private final FieldFillConfiguration fieldFillConfiguration;
    private final DataPermissionConfiguration dataPermissionConfiguration;
    private final ObjectProvider<DataScopeIdExpander> dataScopeIdExpanderProvider;
    private final LogicalDeleteConfiguration logicalDeleteConfiguration;
    private final PaginationProperties paginationProperties;
    private final SqlFirewallProperties sqlFirewallProperties;
    private final ReadWriteSplittingProperties readWriteSplittingProperties;
    private final CircuitBreakerProperties circuitBreakerProperties;
    private final ObjectProvider<List<InnerInterceptorProvider>> spiInterceptorProviders;

    public MybatisPlusConfiguration(FieldFillConfiguration fieldFillConfiguration,
                                     DataPermissionConfiguration dataPermissionConfiguration,
                                     ObjectProvider<DataScopeIdExpander> dataScopeIdExpanderProvider,
                                     LogicalDeleteConfiguration logicalDeleteConfiguration,
                                     PaginationProperties paginationProperties,
                                     SqlFirewallProperties sqlFirewallProperties,
                                     ReadWriteSplittingProperties readWriteSplittingProperties,
                                     CircuitBreakerProperties circuitBreakerProperties,
                                     ObjectProvider<List<InnerInterceptorProvider>> spiInterceptorProviders) {
        this.fieldFillConfiguration = fieldFillConfiguration;
        this.dataPermissionConfiguration = dataPermissionConfiguration;
        this.dataScopeIdExpanderProvider = dataScopeIdExpanderProvider;
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
     *   <li>乐观锁拦截器（MP 内置，配合实体 @Version 注解）</li>
     *   <li>逻辑删除拦截器（自定义实现，替代@TableLogic）</li>
     *   <li>字段填充拦截器（针对非实体类的更新操作）</li>
     *   <li>SPI 拦截器（外部模块通过 {@link InnerInterceptorProvider} 注入，按 order 排序）</li>
     *   <li>数据权限拦截器（行级+列级）</li>
     *   <li>分页拦截器（动态适配数据库类型）</li>
     * </ol>
     *
     * @return MybatisPlusInterceptor 实例
     * @see LogicalDeleteInterceptor
     * @see InnerInterceptorProvider
     * @see PaginationInnerInterceptor
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 乐观锁拦截器（MP 内置，处理实体 @Version 字段的参数映射与版本递增）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        log.debug("MyBatis Plus: OptimisticLockerInnerInterceptor (built-in) enabled for @Version entities");

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

    // ====================================================================
    // 启动期 Banner 打印
    // ====================================================================

    /**
     * 启动完成后打印能力概览 Banner
     *
     * <p>通过 {@link ApplicationReadyEvent} 确保在 Spring 容器完全就绪后执行，
     * 此时所有 Bean 均已初始化完毕。ASCII 形式输出便于开发者快速确认：
     * <ul>
     *     <li>哪些拦截器已启用（基于运行时配置，非硬编码）</li>
     *     <li>数据源数量（通过 {@link DynamicRoutingDataSource} 探测）</li>
     *     <li>读写分离、熔断器等附属功能状态</li>
     * </ul>
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void printCapabilityBanner(ApplicationReadyEvent event) {
        ApplicationContext ctx = event.getApplicationContext();

        List<FeatureLine> features = new ArrayList<>();

        // 1. 乐观锁（MP 内置，配合实体 @Version 注解）
        features.add(new FeatureLine("Optimistic Lock", true, "Built-in @Version"));

        // 2. 逻辑删除
        boolean logicalDelete = Boolean.TRUE.equals(logicalDeleteConfiguration.isEnable());
        features.add(new FeatureLine("Logical Delete", logicalDelete, null));

        // 3. 字段填充
        boolean fieldFill = fieldFillConfiguration.getCreatedByIntercept().getEnabled()
                || fieldFillConfiguration.getUpdateByIntercept().getEnabled()
                || fieldFillConfiguration.getCreateAtIntercept().getEnabled()
                || fieldFillConfiguration.getUpdateAtIntercept().getEnabled();
        features.add(new FeatureLine("Field Fill", fieldFill, null));

        // 4. 数据权限
        boolean dataPermission = Boolean.TRUE.equals(dataPermissionConfiguration.getEnabled());
        features.add(new FeatureLine("Data Permission", dataPermission, null));

        // 5. SQL 防火墙
        boolean sqlFirewall = sqlFirewallProperties != null && sqlFirewallProperties.isEnabled();
        features.add(new FeatureLine("SQL Firewall", sqlFirewall, null));

        // 6. Sql Trace（慢 SQL + 审计一体化）
        boolean sqlTrace = isSqlTraceEnabled(ctx);
        features.add(new FeatureLine("Sql Trace", sqlTrace, null));

        // 7. 分页（始终启用）
        features.add(new FeatureLine("Pagination", true,
                paginationProperties.getDbType() != null ? paginationProperties.getDbType() : "auto"));

        // 8. 读写分离
        boolean rwSplitting = readWriteSplittingProperties != null && readWriteSplittingProperties.isEnabled();
        features.add(new FeatureLine("RW Splitting", rwSplitting,
                rwSplitting ? readWriteSplittingProperties.getLoadBalanceStrategy() : null));

        // 9. 熔断器
        boolean circuitBreaker = circuitBreakerProperties != null && circuitBreakerProperties.isEnabled();
        features.add(new FeatureLine("Circuit Breaker", circuitBreaker, null));

        // 10. SPI 拦截器
        int spiCount = spiInterceptorProviders.getIfAvailable(Collections::emptyList).size();
        features.add(new FeatureLine("SPI Extensions", spiCount > 0,
                spiCount > 0 ? spiCount + " loaded" : null));

        // 数据源数量
        String dataSourceInfo = buildDataSourceInfo(ctx);

        // 打印 Banner
        StringBuilder banner = new StringBuilder();
        banner.append(System.lineSeparator());

        // Box 宽度
        int boxWidth = 52;
        String border = "═".repeat(boxWidth - 2);

        // 标题行
        banner.append("╔").append(border).append("╗").append(System.lineSeparator());
        String title = "ydsz-common-jdbc Capability Overview";
        banner.append(formatBannerLine(title, boxWidth, true)).append(System.lineSeparator());

        // 分割线
        banner.append("╠").append(border).append("╣").append(System.lineSeparator());

        // 拦截器区标题
        banner.append(formatBannerLine("Interceptors:", boxWidth, false)).append(System.lineSeparator());

        // 功能列表（两列布局）
        appendFeatureRows(features, banner, boxWidth);

        // 数据源信息
        banner.append("╠").append(border).append("╣").append(System.lineSeparator());
        banner.append(formatBannerLine("DataSources: " + dataSourceInfo, boxWidth, false))
                .append(System.lineSeparator());

        // 底部
        banner.append("╚").append(border).append("╝");

        log.info(banner.toString());
    }

    /**
     * 探测 SqlTraceInnerInterceptor 是否已注册到拦截器链
     */
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

    /**
     * 构建数据源信息描述字符串
     */
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

    /**
     * 格式化单行内容（居中对齐用于标题，左对齐用于正文）
     */
    private String formatBannerLine(String text, int boxWidth, boolean center) {
        int innerWidth = boxWidth - 4; // 两侧各留 "║ " 和 " ║"
        if (center) {
            int padding = innerWidth - text.length();
            int leftPad = padding / 2;
            int rightPad = padding - leftPad;
            return "║ " + " ".repeat(Math.max(0, leftPad)) + text
                    + " ".repeat(Math.max(0, rightPad)) + " ║";
        } else {
            return "║ " + text
                    + " ".repeat(Math.max(0, innerWidth - text.length())) + " ║";
        }
    }

    /**
     * 将功能列表按两列布局追加到 Banner
     *
     * <p>每行两个项目，启用项使用 {@code ✓} 符号，禁用项使用 {@code ✗} 符号。
     * 奇数项目时右侧留空。
     */
    private void appendFeatureRows(List<FeatureLine> features, StringBuilder banner, int boxWidth) {
        int innerWidth = boxWidth - 4;
        int colWidth = (innerWidth - 2) / 2; // 两列之间的间距

        for (int i = 0; i < features.size(); i += 2) {
            FeatureLine left = features.get(i);
            FeatureLine right = (i + 1 < features.size()) ? features.get(i + 1) : null;

            String leftStr = "  " + (left.enabled ? "✓ " : "✗ ") + left.label;
            if (left.detail != null) {
                leftStr += " (" + left.detail + ")";
            }
            leftStr = truncateToWidth(leftStr, colWidth);

            String rightStr = "";
            if (right != null) {
                rightStr = "  " + (right.enabled ? "✓ " : "✗ ") + right.label;
                if (right.detail != null) {
                    rightStr += " (" + right.detail + ")";
                }
                rightStr = truncateToWidth(rightStr, colWidth);
            }

            // 构建行内容
            String row = leftStr + " ".repeat(Math.max(0, colWidth - displayWidth(leftStr))) + "  " + rightStr;
            row += " ".repeat(Math.max(0, innerWidth - displayWidth(row)));

            banner.append("║ ").append(row).append(" ║").append(System.lineSeparator());
        }
    }

    /**
     * 截断字符串到指定显示宽度（处理中英文混合）
     */
    private String truncateToWidth(String str, int maxWidth) {
        if (displayWidth(str) <= maxWidth) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int charWidth = (c > 127) ? 2 : 1;
            if (width + charWidth > maxWidth - 1) {
                sb.append('…');
                break;
            }
            sb.append(c);
            width += charWidth;
        }
        return sb.toString();
    }

    /**
     * 计算字符串的显示宽度（ASCII 字符计 1，CJK 及其他全角字符计 2）
     */
    private int displayWidth(String str) {
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            width += (str.charAt(i) > 127) ? 2 : 1;
        }
        return width;
    }

    /**
     * Banner 行内部数据结构
     */
    private static final class FeatureLine {
        final String label;
        final boolean enabled;
        final String detail;

        FeatureLine(String label, boolean enabled, String detail) {
            this.label = label;
            this.enabled = enabled;
            this.detail = detail;
        }
    }
}
