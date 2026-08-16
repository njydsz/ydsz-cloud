package com.njydsz.common.tenant.annotation;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@code @TenantColumn} 注解扫描器。
 *
 * <p>在应用启动时扫描 classpath 中标注了 {@link TenantColumn} 注解的实体类， 结合 MyBatis-Plus 的 {@code @TableName}
 * 注解解析表名 → 租户列名映射， 返回扫描结果供配置层合并到 {@link
 * com.njydsz.common.tenant.config.TenantProperties#getTableColumnMapping()}。
 *
 * <p>业务 DO 无需在 YAML 中配置 per-table 列名覆盖：
 *
 * <pre>
 * &#64;TenantColumn("org_id")
 * &#64;TableName("ydsz_file_node")
 * public class FileNodeDO extends MpBaseEntity&lt;String&gt; { ... }
 * </pre>
 *
 * <p><b>扫描范围：</b>默认扫描 {@code com.njydsz} 基包。 可通过 {@code ydsz.tenant.scan-packages} 配置扩展（预留，v1.10
 * 暂未开放）。
 *
 * <p><b>命名回退：</b>未标注 {@code @TableName} 时，使用类名驼峰 → 下划线命名策略推导表名。
 *
 * <p><b>优先级：</b>注解扫描结果作为默认值，YAML 的 {@code table-column-mapping} 可覆盖注解声明。
 *
 * @author ydsz-team
 * @since 1.10.0
 */
public class TenantColumnScanner {

  private static final String BASE_PACKAGE = "com.njydsz";

  /**
   * 扫描 classpath 并返回表名 → 租户列名映射。
   *
   * @return 表名 → 租户列名映射（key 已小写规范化）
   */
  public Map<String, String> scanTableColumnMappings() {
    Map<String, String> scannedMappings = new LinkedHashMap<>();
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(TenantColumn.class));

    for (String pkg : resolveScanPackages()) {
      scanner
          .findCandidateComponents(pkg)
          .forEach(
              beanDef -> {
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
   * <p>优先从 MyBatis-Plus 的 {@code @TableName} 注解获取； 未标注时使用类名驼峰 → 下划线命名策略作为回退。
   */
  @SuppressWarnings("unchecked")
  private String resolveTableName(Class<?> clazz) {
    try {
      Class<?> tableNameClass =
          ClassUtils.forName("com.baomidou.mybatisplus.annotation.TableName", null);
      Annotation annotation = clazz.getAnnotation((Class<? extends Annotation>) tableNameClass);
      if (annotation != null) {
        String value = (String) annotation.getClass().getMethod("value").invoke(annotation);
        if (StringUtils.hasText(value)) {
          return value;
        }
      }
    } catch (Exception e) {
      // MyBatis-Plus 不在 classpath 时使用命名回退
    }
    return deriveTableNameFromClassName(clazz.getSimpleName());
  }

  /** 从类名推导表名（移除 DO/Entity/Po 后缀 + 驼峰 → 下划线）。 */
  private String deriveTableNameFromClassName(String simpleName) {
    String name = simpleName;
    if (name.endsWith("DO")) {
      name = name.substring(0, name.length() - 2);
    } else if (name.endsWith("Entity")) {
      name = name.substring(0, name.length() - 6);
    } else if (name.endsWith("Po")) {
      name = name.substring(0, name.length() - 2);
    }
    return camelToUnderscore(name);
  }

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

  private Set<String> resolveScanPackages() {
    return Set.of(BASE_PACKAGE);
  }
}
