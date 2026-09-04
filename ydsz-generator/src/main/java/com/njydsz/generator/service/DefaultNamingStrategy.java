package com.njydsz.generator.service;

import com.njydsz.generator.config.GeneratorProperties.ModuleGroupConfig;

/**
 * 默认命名策略 — 遵循云顶编码规范（YDIZ-NAME-001/002）。
 *
 * <p>规则说明：
 * <ul>
 *   <li>实体类名：去前缀后取最后一段，转 PascalCase，不加 DO/Entity 后缀</li>
 *   <li>其它名称：实体类名 + 角色后缀（Repository/Service/ServiceImpl/Controller/DTO/VO/PageQuery/Mapper）</li>
 *   <li>API 路径：{@code /api/v1/模块/业务名（短横线分隔）}</li>
 *   <li>权限前缀：{@code 模块名:业务名（短横线分隔）}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.04
 * @see NamingStrategy
 */
public class DefaultNamingStrategy implements NamingStrategy {

  private static final String[] SYSTEM_PREFIXES = {"sys", "acct", "sec"};

  @Override
  public String toEntityName(String rawTableName, ModuleGroupConfig config) {
    String cleaned = rawTableName.replace('-', '_');
    String[] parts = cleaned.split("_");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.length() > 1 && sb.isEmpty() && isSystemPrefix(part)) {
        continue;
      }
      sb.append(capitalize(part.toLowerCase()));
    }
    // 跨模块保留完整语义以避免重名（如 sys_order + acct_order 不冲突）
    if (sb.isEmpty()) {
      sb.append(capitalize(rawTableName.toLowerCase()));
    }
    return sb.toString();
  }

  @Override
  public String toRepositoryName(String entityName) {
    return entityName + "Repository";
  }

  @Override
  public String toServiceName(String entityName) {
    return entityName + "Service";
  }

  @Override
  public String toServiceImplName(String entityName) {
    return entityName + "ServiceImpl";
  }

  @Override
  public String toControllerName(String entityName) {
    return entityName + "Controller";
  }

  @Override
  public String toDtoName(String entityName) {
    return entityName + "DTO";
  }

  @Override
  public String toVoName(String entityName) {
    return entityName + "VO";
  }

  @Override
  public String toQueryName(String entityName) {
    return entityName + "PageQuery";
  }

  @Override
  public String toMapperName(String entityName) {
    return entityName + "Mapper";
  }

  @Override
  public String toConverterName(String moduleName) {
    return capitalize(moduleName) + "Converter";
  }

  @Override
  public String toFeignClientName(String entityName) {
    return entityName + "FeignClient";
  }

  @Override
  public String toApiPath(String rawTableName, String moduleName) {
    String moduleRaw = rawTableName;
    if (moduleRaw.startsWith(moduleName + "_")) {
      moduleRaw = moduleRaw.substring(moduleName.length() + 1);
    }
    return "/api/v1/" + moduleRaw.replace('_', '-');
  }

  @Override
  public String toPermissionPrefix(String moduleName, String rawTableName) {
    String moduleRaw = rawTableName;
    if (moduleRaw.startsWith(moduleName + "_")) {
      moduleRaw = moduleRaw.substring(moduleName.length() + 1);
    }
    return moduleName + ":" + moduleRaw.replace('_', '-');
  }

  // -----------------------------------------------------------------------

  private boolean isSystemPrefix(String part) {
    for (String prefix : SYSTEM_PREFIXES) {
      if (prefix.equalsIgnoreCase(part)) {
        return true;
      }
    }
    return false;
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }
}
