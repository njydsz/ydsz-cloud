package com.njydsz.common.tenant.config;

import java.util.Map;

import com.njydsz.common.tenant.annotation.TenantColumnScanner;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 注解扫描结果回填器。
 *
 * <p>在 {@link TenantProperties} 实例创建后自动将其与
 * {@link TenantColumnScanner} 的扫描结果合并：
 * <ul>
 *   <li>先读取 classpath 中 {@code @TenantColumn} 注解声明的 per-table 列名映射</li>
 *   <li>将扫描结果回填到 {@link TenantProperties#getTableColumnMapping()} Map</li>
 *   <li>YAML 显式配置的映射优先级更高（不覆盖已有 entry）</li>
 * </ul>
 *
 * <p>实现为 {@link BeanPostProcessor} 而非 {@code @PostConstruct}，
 * 因为需要拿到完整装配后的 {@link TenantColumnScanner} 实例，
 * 且必须在 {@code TenantIsolationInterceptor} 创建前生效。
 *
 * @author ydsz-team
 * @since 1.10.0
 */
public class TenantPropertiesAnnotationPopulator implements BeanPostProcessor {

    private final TenantColumnScanner scanner;

    public TenantPropertiesAnnotationPopulator(TenantColumnScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof TenantProperties properties) {
            Map<String, String> scannedMappings = scanner.scanTableColumnMappings();
            scannedMappings.forEach((table, column) -> {
                // YAML 显式配置优先，不覆盖
                properties.getTableColumnMapping()
                        .putIfAbsent(table.toLowerCase(), column);
            });
        }
        return bean;
    }
}
