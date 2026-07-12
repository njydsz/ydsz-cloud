package com.njydsz.pmis.common.auth.util;

import com.njydsz.pmis.common.util.string.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 权限校验工具类。
 *
 * <p>提供权限校验的通用方法，消除 {@code RbacPermissionEvaluator}、
 * {@code AbstractPermissionStrategy}、{@code PermissionPreChecker} 等类中的重复代码。
 *
 * <p>包含以下通用能力：
 * <ul>
 *   <li>通配符权限匹配（支持 * 和 ** 通配符）</li>
 *   <li>正则模式编译与缓存（LRU 淘汰策略）</li>
 *   <li>超管角色判断</li>
 *   <li>CSV 字符串拆分</li>
 *   <li>用户角色解析</li>
 *   <li>多角色权限合并</li>
 * </ul>
 *
 * <p>线程安全说明：本类使用 {@code Collections.synchronizedMap} 包装的 {@link LinkedHashMap}（LRU 淘汰策略）缓存编译后的正则模式，
 * 所有方法均为无状态方法，可安全在多线程环境下使用。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class PermissionUtils {

    /**
     * 正则模式缓存最大容量
     */
    private static final int MAX_PATTERN_CACHE_SIZE = 1024;

    /**
     * 正则模式缓存，使用 LRU 淘汰策略
     */
    private static final Map<String, Pattern> PATTERN_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Pattern>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                    return size() > MAX_PATTERN_CACHE_SIZE;
                }
            });

    private PermissionUtils() {
    }

    /**
     * 判断用户是否拥有指定权限。
     *
     * <p>遍历已授权权限集合，逐一与所需权限进行匹配。
     * 支持通配符匹配（需启用通配符功能）。
     *
     * @param granted  已授权权限集合
     * @param required 所需权限
     * @return 拥有权限返回 true，否则返回 false
     */
    public static boolean hasPermission(Set<String> granted, String required) {
        return hasPermission(granted, required, true);
    }

    /**
     * 判断用户是否拥有指定权限（可控制通配符）。
     *
     * <p>遍历已授权权限集合，逐一与所需权限进行匹配。
     * 通配符匹配复用 {@link #permissionMatch} 方法（含正则缓存）。
     *
     * @param granted         已授权权限集合
     * @param required        所需权限
     * @param wildcardEnabled 是否启用通配符匹配
     * @return 拥有权限返回 true，否则返回 false
     */
    public static boolean hasPermission(Set<String> granted, String required, boolean wildcardEnabled) {
        if (granted == null || granted.isEmpty() || StringUtils.isBlank(required)) {
            return false;
        }
        String req = required.trim();
        for (String g : granted) {
            if (permissionMatch(g, req, wildcardEnabled)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 权限匹配。
     *
     * <p>匹配规则：
     * <ol>
     *   <li>精确匹配：granted 和 required 完全相等</li>
     *   <li>通配符匹配：granted 或 required 包含 * 时，将 * 转换为正则表达式进行匹配</li>
     * </ol>
     *
     * @param granted         已授权权限
     * @param required        所需权限
     * @param wildcardEnabled 是否启用通配符匹配
     * @return 匹配成功返回 true，否则返回 false
     */
    public static boolean permissionMatch(String granted, String required, boolean wildcardEnabled) {
        if (StringUtils.isBlank(granted) || StringUtils.isBlank(required)) {
            return false;
        }
        String g = granted.trim();
        String r = required.trim();
        if (g.equals(r)) {
            return true;
        }
        if (!wildcardEnabled) {
            return false;
        }
        if (g.contains("*")) {
            return compilePattern(g).matcher(r).matches();
        }
        if (r.contains("*")) {
            return compilePattern(r).matcher(g).matches();
        }
        return false;
    }

    /**
     * 编译通配符模式为正则表达式，并缓存结果。
     *
     * <p>将 * 转换为 .*，** 转换为任意层级匹配，其他特殊字符使用 {@link Pattern#quote} 转义。
     * 例如：{@code user:*} 转换为 {@code ^user\..*$}。
     *
     * @param wildcard 包含通配符的权限字符串
     * @return 编译后的正则模式
     */
    public static Pattern compilePattern(String wildcard) {
        return PATTERN_CACHE.computeIfAbsent(wildcard, w -> {
            String regex = Arrays.stream(w.split("\\*\\*", -1))
                    .map(part -> {
                        if (part.contains("*")) {
                            return Arrays.stream(part.split("\\*", -1))
                                    .map(Pattern::quote)
                                    .collect(Collectors.joining("[^.]*"));
                        }
                        return Pattern.quote(part);
                    })
                    .collect(Collectors.joining(".*"));
            return Pattern.compile("^" + regex + "$");
        });
    }

    /**
     * 判断用户角色中是否包含超管角色。
     *
     * @param userRoles   用户角色集合
     * @param adminRoles  超管角色集合（CSV 格式或 Set 格式）
     * @return 是超管返回 true，否则返回 false
     */
    public static boolean isSuperAdmin(Set<String> userRoles, Set<String> adminRoles) {
        if (adminRoles == null || adminRoles.isEmpty() || userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        return userRoles.stream().anyMatch(adminRoles::contains);
    }

    /**
     * 判断用户角色中是否包含超管角色（CSV 格式配置）。
     *
     * @param userRoles    用户角色集合
     * @param adminRolesCsv 超管角色 CSV 字符串
     * @return 是超管返回 true，否则返回 false
     */
    public static boolean isSuperAdmin(Set<String> userRoles, String adminRolesCsv) {
        return isSuperAdmin(userRoles, splitCsv(adminRolesCsv));
    }

    /**
     * 将 CSV 字符串拆分为不可变 Set 集合。
     *
     * <p>自动去除前后空白和空值。例如：{@code "admin,super_admin"} 拆分为
     * {@code ["admin", "super_admin"]}。
     *
     * @param csv CSV 格式字符串
     * @return 拆分后的不可变集合
     */
    public static Set<String> splitCsv(String csv) {
        if (StringUtils.isBlank(csv)) {
            return Collections.emptySet();
        }
        Set<String> result = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        return Set.copyOf(result);
    }

    /**
     * 合并多个角色的权限集合。
     *
     * <p>将每个角色对应的权限集合合并为一个去重的权限集合。
     *
     * @param rolePermissions 角色到权限集合的映射
     * @param roleCodes       需要合并的角色编码集合
     * @return 合并后的权限集合
     */
    public static Set<String> mergeRolePermissions(Map<String, Set<String>> rolePermissions, Set<String> roleCodes) {
        Set<String> merged = new HashSet<>();
        if (rolePermissions == null || roleCodes == null) {
            return merged;
        }
        for (String roleCode : roleCodes) {
            Set<String> perms = rolePermissions.get(roleCode);
            if (perms != null) {
                merged.addAll(perms);
            }
        }
        return merged;
    }

    /**
     * 清空正则模式缓存。
     *
     * <p>通常在权限配置变更时调用，确保后续匹配使用最新的模式。
     */
    public static void clearPatternCache() {
        PATTERN_CACHE.clear();
    }
}
