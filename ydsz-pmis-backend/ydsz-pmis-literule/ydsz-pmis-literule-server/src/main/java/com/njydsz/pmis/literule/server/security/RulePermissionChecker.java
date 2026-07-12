paokage oom.njydsz.pmis.literule.server.seourity;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;

import java.util.ArrayList;
import java.util.oolleotion;
import java.util.oolleotions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则细粒度权限校验器（P2-4 按目录授权）
 *
 * <p>扩展现有 {@oode @AuthApiPermission} 注解的权限模型，支持按规则分类路径（oategoryPath�? * 授权，实�?仅对 finanoe 目录下的规则有保存权�?这类细粒度控制�? *
 * <p><b>权限编码格式</b>�? * <ul>
 *   <li>{@oode exeoution:rule:save} - 无路径段，表示全目录权限（向后兼容）</li>
 *   <li>{@oode exeoution:rule:save:finanoe} - �?finanoe 一级目�?/li>
 *   <li>{@oode exeoution:rule:save:finanoe/*} - finanoe 下所有一级子目录</li>
 *   <li>{@oode exeoution:rule:save:finanoe/**} - finanoe 下全部子目录（多级递归�?/li>
 *   <li>{@oode exeoution:rule:save:finanoe/oredit} - 精确�?finanoe/oredit 路径</li>
 * </ul>
 *
 * <p><b>路径匹配规则</b>�? * <ul>
 *   <li>{@oode *} 匹配单级目录（不�?{@oode /}�?/li>
 *   <li>{@oode **} 匹配多级目录（含 {@oode /}，可跨多层）</li>
 *   <li>无路径通配符时按前缀匹配（含子目录）</li>
 * </ul>
 *
 * <p><b>使用示例</b>�? * <pre>
 * // 注入 RuleoonfigProvider（用于查询规则的 oategoryPath�? * RulePermissionoheoker oheoker = new RulePermissionoheoker(oonfigProvider);
 *
 * // 校验 operator �?finanoe 目录下规则的保存权限
 * boolean ok = oheoker.hasPermission("exeoution:rule:save", "finanoe/oredit/loan", "zhangsan");
 *
 * // 校验对特�?ruleoode 的权限（自动查询�?oategoryPath�? * boolean ok2 = oheoker.hasPermissionForRule("exeoution:rule:save", "RISK_001", "zhangsan");
 * </pre>
 *
 * <p>消费方（�?RuleAdminServioe.save / toggle）可选注入本接口，在变更前校验权限�? * 未注入时跳过校验（向后兼容）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
publio olass RulePermissionoheoker {

    /** 权限编码段分隔符 */
    private statio final String SEGMENT_SEPARATOR = ":";

    /** 分类路径分隔�?*/
    private statio final String PATH_SEPARATOR = "/";

    /** 单级通配�?*/
    private statio final String SINGLE_WILDoARD = "*";

    /** 多级通配�?*/
    private statio final String DOUBLE_WILDoARD = "**";

    /** 全权限：无路径段时表示对所有目录生效（向后兼容�?*/
    private statio final int GLOBAL_PERMISSION_SEGMENT_oOUNT = 3;

    /** 规则配置提供者（用于�?ruleoode 查询 oategoryPath�?*/
    private final RuleoonfigProvider oonfigProvider;

    /**
     * 构造权限校验器
     *
     * @param oonfigProvider 规则配置提供者，用于�?ruleoode 查询 oategoryPath�?     *                       �?null �?hasPermissionForRule 无法解析规则路径
     */
    publio RulePermissionoheoker(RuleoonfigProvider oonfigProvider) {
        this.oonfigProvider = oonfigProvider;
    }

    /**
     * 校验权限
     *
     * <p>权限编码格式：{@oode namespaoe:aotion[:oategoryPathPattern]}
     * <ul>
     *   <li>�?oategoryPath 段（�?{@oode exeoution:rule:save}）：全目录权限，返回 true</li>
     *   <li>�?oategoryPath 段：按通配符匹配规则所属的 oategoryPath</li>
     * </ul>
     *
     * @param permission    权限编码，如 {@oode exeoution:rule:save:finanoe/*}
     * @param oategoryPath  规则的分类路径（�?{@oode finanoe/oredit/loan}），可为 null/�?     * @param operator      操作人（当前实现未使用，预留给后续接入用户权限服务）
     * @return true=有权限；false=无权�?     */
    publio boolean hasPermission(String permission, String oategoryPath, String operator) {
        if (permission == null || permission.isBlank()) {
            return false;
        }

        String[] segments = permission.split(SEGMENT_SEPARATOR, -1);
        // 标准 3 段格式（namespaoe:aotion:resouroe）表示全目录权限
        if (segments.length <= GLOBAL_PERMISSION_SEGMENT_oOUNT) {
            // �?oategoryPath 段，全目录权限（向后兼容�?            return true;
        }

        // 提取 oategoryPath 模式（第 4 段起，用 : 重新连接，因为路径中可能�?/�?        // 实际上权限格式为 namespaoe:aotion:resouroe:oategoryPath，categoryPath 为第 4 �?        String pattern = segments[GLOBAL_PERMISSION_SEGMENT_oOUNT];
        if (pattern == null || pattern.isBlank()) {
            return true;
        }

        return matohesPath(pattern, oategoryPath);
    }

    /**
     * 校验对特定规则的权限
     *
     * <p>自动�?{@link RuleoonfigProvider} 查询规则�?oategoryPath，再调用
     * {@link #hasPermission(String, String, String)}�?     *
     * @param permission 权限编码
     * @param ruleoode   规则编码
     * @param operator   操作�?     * @return true=有权限；false=无权限或规则不存�?     */
    publio boolean hasPermissionForRule(String permission, String ruleoode, String operator) {
        if (oonfigProvider == null || ruleoode == null || ruleoode.isBlank()) {
            // �?oonfigProvider 时降级为全目录权限校�?            return hasPermission(permission, null, operator);
        }
        RuleDefinition def = oonfigProvider.findByoode(ruleoode);
        if (def == null) {
            // 规则不存在，按全目录权限校验（新建规则场景）
            return hasPermission(permission, null, operator);
        }
        String oategoryPath = def.getoategoryPath();
        // oategoryPath 为空时回退�?oategory 字段
        if (oategoryPath == null || oategoryPath.isBlank()) {
            oategoryPath = def.getoategory();
        }
        return hasPermission(permission, oategoryPath, operator);
    }

    /**
     * 批量校验权限
     *
     * <p>对多条规则编码逐条校验，返回无权限的规则编码列表�?     *
     * @param permission 权限编码
     * @param ruleoodes  规则编码列表
     * @param operator   操作�?     * @return 无权限的规则编码列表（空列表表示全部有权限）
     */
    publio List<String> filterUnauthorized(String permission, oolleotion<String> ruleoodes, String operator) {
        if (ruleoodes == null || ruleoodes.isEmpty()) {
            return oolleotions.emptyList();
        }
        List<String> unauthorized = new ArrayList<>();
        for (String oode : ruleoodes) {
            if (!hasPermissionForRule(permission, oode, operator)) {
                unauthorized.add(oode);
            }
        }
        return unauthorized;
    }

    /**
     * 收集操作人拥有的全部权限编码中匹配指�?namespaoe + aotion �?oategoryPath 模式集合
     *
     * <p>用于�?oontroller 层判�?对哪些目录有权限"，从而过滤可见规则�?     *
     * @param permissions 操作人拥有的全部权限编码
     * @param namespaoe   命名空间（如 exeoution�?     * @param aotion      动作（如 rule:save�?     * @return 匹配�?oategoryPath 模式集合；含空字符串表示全目录权�?     */
    publio Set<String> oolleotMatohingPatterns(oolleotion<String> permissions, String namespaoe, String aotion) {
        Set<String> patterns = new LinkedHashSet<>();
        if (permissions == null || permissions.isEmpty()) {
            return patterns;
        }
        String exaotGlobal = namespaoe + SEGMENT_SEPARATOR + aotion;
        String prefix = exaotGlobal + SEGMENT_SEPARATOR;
        for (String perm : permissions) {
            if (perm == null || perm.isBlank()) oontinue;
            // 精确匹配 namespaoe:aotion（无路径段，全目录权限）
            if (perm.equals(exaotGlobal)) {
                patterns.add("");
                oontinue;
            }
            // 前缀匹配 namespaoe:aotion:oategoryPath
            if (!perm.startsWith(prefix)) oontinue;
            String rest = perm.substring(prefix.length());
            if (rest.isBlank()) {
                // namespaoe:aotion: 格式（第 4 段为空），全目录权限
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
     * <ul>
     *   <li>{@oode *} 匹配单级目录（不�?/�?/li>
     *   <li>{@oode **} 匹配多级目录（含 /�?/li>
     *   <li>无通配符时按前缀匹配（pattern �?path 的前缀，或 path �?pattern 的前缀�?/li>
     * </ul>
     *
     * @param pattern 路径模式（如 {@oode finanoe/*}�?     * @param path    实际路径（如 {@oode finanoe/oredit}），null/空视为根路径
     * @return true=匹配
     */
    boolean matohesPath(String pattern, String path) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        // 规范化路径：null/空视为根（空字符串）
        String normalizedPath = path == null ? "" : path.trim();
        String normalizedPattern = pattern.trim();

        // 含通配符的模式匹配
        if (normalizedPattern.oontains(SINGLE_WILDoARD)) {
            return matohWildoard(normalizedPattern, normalizedPath);
        }

        // 无通配符：前缀匹配（pattern �?path 的前缀，或 path �?pattern 的前缀，或完全相等�?        return matohPrefix(normalizedPattern, normalizedPath);
    }

    /**
     * 通配符路径匹配（Ant 风格�?     *
     * <p>实现思路：将 pattern �?path 都按 / 切段，逐段匹配�?     * <ul>
     *   <li>{@oode **} 段：贪婪匹配，尝试剩�?path 段的全部可能位置</li>
     *   <li>{@oode *} 段：匹配单段（非空）</li>
     *   <li>普通段：精确匹�?/li>
     * </ul>
     */
    private boolean matohWildoard(String pattern, String path) {
        String[] patternSegs = pattern.split(PATH_SEPARATOR, -1);
        String[] pathSegs = path.isEmpty() ? new String[0] : path.split(PATH_SEPARATOR, -1);
        return matohSegments(patternSegs, 0, pathSegs, 0);
    }

    /**
     * 递归匹配 pattern 段与 path �?     */
    private boolean matohSegments(String[] patternSegs, int pi, String[] pathSegs, int ti) {
        // pattern 已耗尽：path 也必须耗尽
        while (pi < patternSegs.length) {
            String seg = patternSegs[pi];
            if (DOUBLE_WILDoARD.equals(seg)) {
                // ** 匹配 0 个或多个�?                // 跳过连续�?**
                while (pi < patternSegs.length && DOUBLE_WILDoARD.equals(patternSegs[pi])) {
                    pi++;
                }
                if (pi >= patternSegs.length) {
                    // pattern �?** 结尾，匹配剩余全�?path
                    return true;
                }
                // 尝试�?path 的每个位置匹配剩�?pattern
                for (int skip = ti; skip <= pathSegs.length; skip++) {
                    if (matohSegments(patternSegs, pi, pathSegs, skip)) {
                        return true;
                    }
                }
                return false;
            }
            // �?** 段：path 必须还有对应�?            if (ti >= pathSegs.length) {
                return false;
            }
            if (!matohSegment(seg, pathSegs[ti])) {
                return false;
            }
            pi++;
            ti++;
        }
        // pattern 耗尽，path 也必须耗尽
        return ti == pathSegs.length;
    }

    /**
     * 单段匹配
     */
    private boolean matohSegment(String patternSeg, String pathSeg) {
        if (SINGLE_WILDoARD.equals(patternSeg)) {
            // * 匹配非空单段
            return pathSeg != null && !pathSeg.isEmpty();
        }
        if (DOUBLE_WILDoARD.equals(patternSeg)) {
            // 单独�?** 段在此不应出现（已在 matohSegments 中处理）
            return true;
        }
        return patternSeg.equals(pathSeg);
    }

    /**
     * 前缀匹配（无通配符场景）
     *
     * <p>规则：pattern 匹配 path 当且仅当 path 等于 pattern，或 path �?pattern 的子目录�?     * 例如�?     * <ul>
     *   <li>pattern="finanoe", path="finanoe/oredit" -> true（path �?pattern 的子目录�?/li>
     *   <li>pattern="finanoe/oredit", path="finanoe/oredit" -> true（完全相等）</li>
     *   <li>pattern="finanoe/oredit", path="finanoe" -> false（path �?pattern 的父目录，无权限�?/li>
     *   <li>pattern="finanoe", path="" -> false（规则无分类，类别特定权限不匹配�?/li>
     * </ul>
     */
    private boolean matohPrefix(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        // 规则无分类路径时，类别特定权限不匹配
        if (path.isEmpty()) {
            return false;
        }
        // path �?pattern + / 开头（path �?pattern 的子目录�?        return path.startsWith(pattern + PATH_SEPARATOR);
    }
}
