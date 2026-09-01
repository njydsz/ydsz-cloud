package com.njydsz.literule.server.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.server.spi.RuleConfigProvider;

/**
 * 规则细粒度权限校验器（P2-4 按目录授权）
 *
 * <p>扩展现有 {@code @AuthApiPermission} 注解的权限模型，支持按规则分类路径（categoryPath） 授权，实现"仅对 finance
 * 目录下的规则有保存权限"这类细粒度控制。
 *
 * <p><b>权限编码格式</b>：
 *
 * <ul>
 *   <li>{@code execution:rule:save} - 无路径段，表示全目录权限（向后兼容）
 *   <li>{@code execution:rule:save:finance} - 仅 finance 一级目录
 *   <li>{@code execution:rule:save:finance/*} - finance 下所有一级子目录
 *   <li>{@code execution:rule:save:finance/**} - finance 下全部子目录（多级递归）
 *   <li>{@code execution:rule:save:finance/credit} - 精确到 finance/credit 路径
 * </ul>
 *
 * <p><b>路径匹配规则</b>：
 *
 * <ul>
 *   <li>{@code *} 匹配单级目录（不含 {@code /}）
 *   <li>{@code **} 匹配多级目录（含 {@code /}，可跨多层）
 *   <li>无路径通配符时按前缀匹配（含子目录）
 * </ul>
 *
 * <p><b>使用示例</b>：
 *
 * <pre>
 * // 注入 RuleConfigProvider（用于查询规则的 categoryPath）
 * RulePermissionChecker checker = new RulePermissionChecker(configProvider);
 *
 * // 校验 operator 对 finance 目录下规则的保存权限
 * boolean ok = checker.hasPermission("execution:rule:save", "finance/credit/loan", "zhangsan");
 *
 * // 校验对特定 ruleCode 的权限（自动查询其 categoryPath）
 * boolean ok2 = checker.hasPermissionForRule("execution:rule:save", "RISK_001", "zhangsan");
 * </pre>
 *
 * <p>消费方（如 RuleAdminService.save / toggle）可选注入本接口，在变更前校验权限。 未注入时跳过校验（向后兼容）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class RulePermissionChecker {

  /** 权限编码段分隔符 */
  private static final String SEGMENT_SEPARATOR = ":";

  /** 分类路径分隔符 */
  private static final String PATH_SEPARATOR = "/";

  /** 单级通配符 */
  private static final String SINGLE_WILDCARD = "*";

  /** 多级通配符 */
  private static final String DOUBLE_WILDCARD = "**";

  /** 全权限：无路径段时表示对所有目录生效（向后兼容） */
  private static final int GLOBAL_PERMISSION_SEGMENT_COUNT = 3;

  /** 规则配置提供者（用于按 ruleCode 查询 categoryPath） */
  private final RuleConfigProvider configProvider;

  /**
   * 构造权限校验器
   *
   * @param configProvider 规则配置提供者，用于按 ruleCode 查询 categoryPath； 为 null 时 hasPermissionForRule
   *     无法解析规则路径
   */
  public RulePermissionChecker(RuleConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  /**
   * 校验权限
   *
   * <p>权限编码格式：{@code namespace:action[:categoryPathPattern]}
   *
   * <ul>
   *   <li>无 categoryPath 段（如 {@code execution:rule:save}）：全目录权限，返回 true
   *   <li>含 categoryPath 段：按通配符匹配规则所属的 categoryPath
   * </ul>
   *
   * @param permission 权限编码，如 {@code execution:rule:save:finance/*}
   * @param categoryPath 规则的分类路径（如 {@code finance/credit/loan}），可为 null/空
   * @param operator 操作人（当前实现未使用，预留给后续接入用户权限服务）
   * @return true=有权限；false=无权限
   */
  public boolean hasPermission(String permission, String categoryPath, String operator) {
    if (permission == null || permission.isBlank()) {
      return false;
    }

    String[] segments = permission.split(SEGMENT_SEPARATOR, -1);
    // 标准 3 段格式（namespace:action:resource）表示全目录权限
    if (segments.length <= GLOBAL_PERMISSION_SEGMENT_COUNT) {
      // 无 categoryPath 段，全目录权限（向后兼容）
      return true;
    }

    // 提取 categoryPath 模式（第 4 段起，用 : 重新连接，因为路径中可能含 /）
    // 实际上权限格式为 namespace:action:resource:categoryPath，categoryPath 为第 4 段
    String pattern = segments[GLOBAL_PERMISSION_SEGMENT_COUNT];
    if (pattern == null || pattern.isBlank()) {
      return true;
    }

    return matchesPath(pattern, categoryPath);
  }

  /**
   * 校验对特定规则的权限
   *
   * <p>自动从 {@link RuleConfigProvider} 查询规则的 categoryPath，再调用 {@link #hasPermission(String, String,
   * String)}。
   *
   * @param permission 权限编码
   * @param ruleCode 规则编码
   * @param operator 操作人
   * @return true=有权限；false=无权限或规则不存在
   */
  public boolean hasPermissionForRule(String permission, String ruleCode, String operator) {
    if (configProvider == null || ruleCode == null || ruleCode.isBlank()) {
      // 无 configProvider 时降级为全目录权限校验
      return hasPermission(permission, null, operator);
    }
    RuleDefinitionDTO def = configProvider.findByCode(ruleCode);
    if (def == null) {
      // 规则不存在，按全目录权限校验（新建规则场景）
      return hasPermission(permission, null, operator);
    }
    String categoryPath = def.getCategoryPath();
    // categoryPath 为空时回退到 category 字段
    if (categoryPath == null || categoryPath.isBlank()) {
      categoryPath = def.getCategory();
    }
    return hasPermission(permission, categoryPath, operator);
  }

  /**
   * 批量校验权限
   *
   * <p>对多条规则编码逐条校验，返回无权限的规则编码列表。
   *
   * @param permission 权限编码
   * @param ruleCodes 规则编码列表
   * @param operator 操作人
   * @return 无权限的规则编码列表（空列表表示全部有权限）
   */
  public List<String> filterUnauthorized(
      String permission, Collection<String> ruleCodes, String operator) {
    if (ruleCodes == null || ruleCodes.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> unauthorized = new ArrayList<>();
    for (String code : ruleCodes) {
      if (!hasPermissionForRule(permission, code, operator)) {
        unauthorized.add(code);
      }
    }
    return unauthorized;
  }

  /**
   * 收集操作人拥有的全部权限编码中匹配指定 namespace + action 的 categoryPath 模式集合
   *
   * <p>用于在 Controller 层判断"对哪些目录有权限"，从而过滤可见规则。
   *
   * @param permissions 操作人拥有的全部权限编码
   * @param namespace 命名空间（如 execution）
   * @param action 动作（如 rule:save）
   * @return 匹配的 categoryPath 模式集合；含空字符串表示全目录权限
   */
  public Set<String> collectMatchingPatterns(
      Collection<String> permissions, String namespace, String action) {
    Set<String> patterns = new LinkedHashSet<>();
    if (permissions == null || permissions.isEmpty()) {
      return patterns;
    }
    String exactGlobal = namespace + SEGMENT_SEPARATOR + action;
    String prefix = exactGlobal + SEGMENT_SEPARATOR;
    for (String perm : permissions) {
      if (perm == null || perm.isBlank()) {
        continue;
      }
      // 精确匹配 namespace:action（无路径段，全目录权限）
      if (perm.equals(exactGlobal)) {
        patterns.add("");
        continue;
      }
      // 前缀匹配 namespace:action:categoryPath
      if (!perm.startsWith(prefix)) {
        continue;
      }
      String rest = perm.substring(prefix.length());
      if (rest.isBlank()) {
        // namespace:action: 格式（第 4 段为空），全目录权限
        patterns.add("");
      } else {
        patterns.add(rest);
      }
    }
    return patterns;
  }

  // ============ 内部路径匹配逻辑 ============

  /**
   * 路径模式匹配
   *
   * <p>支持 Ant 风格通配符：
   *
   * <ul>
   *   <li>{@code *} 匹配单级目录（不含 /）
   *   <li>{@code **} 匹配多级目录（含 /）
   *   <li>无通配符时按前缀匹配（pattern 是 path 的前缀，或 path 是 pattern 的前缀）
   * </ul>
   *
   * @param pattern 路径模式（如 {@code finance/*}）
   * @param path 实际路径（如 {@code finance/credit}），null/空视为根路径
   * @return true=匹配
   */
  boolean matchesPath(String pattern, String path) {
    if (pattern == null || pattern.isBlank()) {
      return true;
    }
    // 规范化路径：null/空视为根（空字符串）
    String normalizedPath = path == null ? "" : path.trim();
    String normalizedPattern = pattern.trim();

    // 含通配符的模式匹配
    if (normalizedPattern.contains(SINGLE_WILDCARD)) {
      return matchWildcard(normalizedPattern, normalizedPath);
    }

    // 无通配符：前缀匹配（pattern 是 path 的前缀，或 path 是 pattern 的前缀，或完全相等）
    return matchPrefix(normalizedPattern, normalizedPath);
  }

  /**
   * 通配符路径匹配（Ant 风格）
   *
   * <p>实现思路：将 pattern 与 path 都按 / 切段，逐段匹配：
   *
   * <ul>
   *   <li>{@code **} 段：贪婪匹配，尝试剩余 path 段的全部可能位置
   *   <li>{@code *} 段：匹配单段（非空）
   *   <li>普通段：精确匹配
   * </ul>
   */
  private boolean matchWildcard(String pattern, String path) {
    String[] patternSegs = pattern.split(PATH_SEPARATOR, -1);
    String[] pathSegs = path.isEmpty() ? new String[0] : path.split(PATH_SEPARATOR, -1);
    return matchSegments(patternSegs, 0, pathSegs, 0);
  }

  /** 递归匹配 pattern 段与 path 段 */
  private boolean matchSegments(String[] patternSegs, int pi, String[] pathSegs, int ti) {
    // pattern 已耗尽：path 也必须耗尽
    while (pi < patternSegs.length) {
      String seg = patternSegs[pi];
      if (DOUBLE_WILDCARD.equals(seg)) {
        // ** 匹配 0 个或多个段
        // 跳过连续的 **
        while (pi < patternSegs.length && DOUBLE_WILDCARD.equals(patternSegs[pi])) {
          pi++;
        }
        if (pi >= patternSegs.length) {
          // pattern 以 ** 结尾，匹配剩余全部 path
          return true;
        }
        // 尝试在 path 的每个位置匹配剩余 pattern
        for (int skip = ti; skip <= pathSegs.length; skip++) {
          if (matchSegments(patternSegs, pi, pathSegs, skip)) {
            return true;
          }
        }
        return false;
      }
      // 非 ** 段：path 必须还有对应段
      if (ti >= pathSegs.length) {
        return false;
      }
      if (!matchSegment(seg, pathSegs[ti])) {
        return false;
      }
      pi++;
      ti++;
    }
    // pattern 耗尽，path 也必须耗尽
    return ti == pathSegs.length;
  }

  /** 单段匹配 */
  private boolean matchSegment(String patternSeg, String pathSeg) {
    if (SINGLE_WILDCARD.equals(patternSeg)) {
      // * 匹配非空单段
      return pathSeg != null && !pathSeg.isEmpty();
    }
    if (DOUBLE_WILDCARD.equals(patternSeg)) {
      // 单独的 ** 段在此不应出现（已在 matchSegments 中处理）
      return true;
    }
    return patternSeg.equals(pathSeg);
  }

  /**
   * 前缀匹配（无通配符场景）
   *
   * <p>规则：pattern 匹配 path 当且仅当 path 等于 pattern，或 path 是 pattern 的子目录。 例如：
   *
   * <ul>
   *   <li>pattern="finance", path="finance/credit" -> true（path 是 pattern 的子目录）
   *   <li>pattern="finance/credit", path="finance/credit" -> true（完全相等）
   *   <li>pattern="finance/credit", path="finance" -> false（path 是 pattern 的父目录，无权限）
   *   <li>pattern="finance", path="" -> false（规则无分类，类别特定权限不匹配）
   * </ul>
   */
  private boolean matchPrefix(String pattern, String path) {
    if (pattern.equals(path)) {
      return true;
    }
    // 规则无分类路径时，类别特定权限不匹配
    if (path.isEmpty()) {
      return false;
    }
    // path 以 pattern + / 开头（path 是 pattern 的子目录）
    return path.startsWith(pattern + PATH_SEPARATOR);
  }
}
