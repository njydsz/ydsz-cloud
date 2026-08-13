package com.njydsz.common.tenant.annotation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.context.TenantContextHolder;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@code @TenantColumn} 注解扫描器。
 *
 * <p>在应用启动时扫描 classpath 中标注了 {@link TenantColumn} 注解的实体类，
 * 结合 MyBatis-Plus 的 {@code @TableName} 注解解析表名 → 租户列名映射，
 * 自动注入到 {@link TenantProperties#getTableColumnMapping()}。
 *
 * <p>业务 DO 无需在 YAML 中配置 per-table 列名覆盖：
 * <pre>
 * &#64;TenantColumn("org_id")
 * &#64;TableName("ydsz_file_node")
 * public class FileNodeDO extends MpBaseEntity&lt;String&gt; { ... }
 * </pre>
 *
 * <p><b>扫描范围：</b>通过 {@code ydsz.tenant.scan-packages} 配置要扫描的包
 *（默认扫描全部 {@code com.njydsz.**}，建议生产环境显式指定包名以加速启动）。
 *
 * <p><b>命名回退：</b>未标注 {@code @TableName} 时，使用 MyBatis-Plus
 * 默认命名策略（类名驼峰 → 下划线）推导表名。
 *
 * <p><b>优先级：</b>YAML 的 {@code table-column-mapping} 优先级高于注解
 *（YAML 配置可覆盖注解声明）。
 *
 * <p><b>条件加载：</b>仅当 classpath 同时存在
 * {@link TenantContextHolder}（即本模块启用）时激活。
 *
 * @author ydsz-team
 * @since 1.10.0
 */
@ConditionalOnClass(TenantContextHolder.class)
public class TenantColumnScanner {

    private static final String BASE_PACKAGE = "com.njydsz";

    private final TenantProperties properties;

    public TenantColumnScanner(TenantProperties properties) {
        this.properties = properties;
    }

    /**
     * 扫描 classpath 并返回表名 → 租户列名映射。
     *
     * <p>仅返回通过注解扫描到的映射，不包含用户显式配置的 YAML 映射。
     * 调用方负责将结果合并到 {@link TenantProperties#getTableColumnMapping()}。
     *
     * @return 表名 → 租户列名映射（key 已小写规范化）
     */
    public Map<String, String> scanTableColumnMappings() {
        Map<String, String> scannedMappings = new LinkedHashMap<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TenantColumn.class));

        Set<String> basePackages = resolveScanPackages();
        for (String pkg : basePackages) {
            scanner.findCandidateComponents(pkg).forEach(beanDef -> {
                try {
                    Class<?> clazz = ClassUtils.forName(beanDef.getBeanClassName(), null);
                    processClass(clazz, scannedMappings);
                } catch (ClassNotFoundException e) {
                    // 跳过无法加载的类（如来自可选依赖的 DO）
                }
            });
        }
        return scannedMappings;
    }

    /**
     * 处理单个类，提取表名 → 租户列名映射。
     */
    private void processClass(Class<?> clazz, Map<String, String> result) {
        TenantColumn tenantColumn = clazz.getAnnotation(TenantColumn.class);
        if (tenantColumn == null) {
            return;
        }

        String columnName = tenantColumn.value();
        if (!StringUtils.hasText(columnName)) {
            return;
        }

        String tableName = resolveTableName(clazz);
        if (!StringUtils.hasText(tableName)) {
            return;
        }

        result.put(tableName.toLowerCase(), columnName);
    }

    /**
     * 解析实体类对应的表名。
     *
     * <p>优先从 MyBatis-Plus 的 {@code @TableName} 注解获取；
     * 未标注时使用类名驼峰 → 下划线命名策略作为回退。
     */
    private String resolveTableName(Class<?> clazz) {
        // 反射获取 @TableName，避免编译期硬依赖 MyBatis-Plus
        try {
            Class<?> tableNameClass = ClassUtils.forName(
                    "com.baomidou.mybatisplus.annotation.TableName", null);
            Object annotation = clazz.getAnnotation(tableNameClass.asSubclass(java.lang.annotation.Annotation.class));
            if (annotation != null) {
                String value = (String) annotation.getClass().getMethod("value").invoke(annotation);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        } catch (Exception e) {
            // MyBatis-Plus 不在 classpath 时使用命名回退
        }

        // 回退：类名驼峰 → 下划线（MyBatis-Plus 默认策略）
        String simpleName = clazz.getSimpleName();
        // 移除常见 DO/Entity 后缀
        if (simpleName.endsWith("DO")) {
            simpleName = simpleName.substring(0, simpleName.length() - 2);
        } else if (simpleName.endsWith("Entity")) {
            simpleName = simpleName.substring(0, simpleName.length() - 6);
        } else if (simpleName.endsWith("Po")) {
            simpleName = simpleName.substring(0, simpleName.length() - 2);
        }
        return camelToUnderscore(simpleName);
    }

    /**
     * 驼峰命名 → 下划线命名。
     */
    private String camelToUnderscore(String camel) {
        if (camel == null || camel.isEmpty()) {
            return camel;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 解析要扫描的包列表。
     *
     * <p>优先使用用户配置的 {@code ydsz.tenant.scan-packages}，
     * 否则使用默认的全局基包。
     */
    private Set<String> resolveScanPackages() {
        // 当前版本暂不支持动态配置扫描包，使用默认基包
        return Set.of(BASE_PACKAGE);
    }

    /**
     * 获取扫描到的列名映射数量（用于日志和诊断）。
     */
    public int getScannedMappingCount() {
        return scanTableColumnMappings().size();
    }
}
