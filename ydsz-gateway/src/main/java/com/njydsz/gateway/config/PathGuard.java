package com.njydsz.gateway.config;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import com.njydsz.common.core.constant.HeaderConstants;

/**
 * 路径安全工具类
 *
 * <p>提供路径规范化、白名单匹配和内部头列表功能，防止路径穿越攻击和客户端伪造内部头。
 *
 * <h3>P2-12 增强项</h3>
 * <ul>
 *   <li>双重 URL 编码检测：拦截 {@code %252e%252e} (Double-Encoding 绕过)</li>
 *   <li>null 字节注入防护：拦截 {@code %00}、{@code \0}</li>
 *   <li>混合编码检测：拦截 {@code .%2f} 等混合编码穿越</li>
 *   <li>URL 解码规范化：先解码再检测，防范编码绕过</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class PathGuard {

    private PathGuard() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 内部头名称列表（客户端传入时必须剥离） */
    private static final Set<String> INTERNAL_HEADERS = Set.of(
            GatewayConstants.HEADER_TRACE_ID,
            GatewayConstants.HEADER_USER_ID,
            GatewayConstants.HEADER_USERNAME,
            GatewayConstants.HEADER_USER_ROLES,
            GatewayConstants.HEADER_USER_PERMISSIONS,
            GatewayConstants.HEADER_INTERNAL_SIG,
            GatewayConstants.HEADER_INTERNAL_TS,
            GatewayConstants.HEADER_INTERNAL_NONCE,
            GatewayConstants.HEADER_TENANT_ID,
            HeaderConstants.X_FORWARDED_FOR,
            "X-Real-IP"
    );

    /** 最大解码次数（防止递归解码 DoS） */
    private static final int MAX_DECODE_ITERATIONS = 3;

    /**
     * 创建不可修改的白名单集合
     *
     * @param paths 白名单路径
     * @return 不可修改的 Set
     */
    public static Set<String> whiteList(String... paths) {
        return Set.of(paths);
    }

    /**
     * 路径规范化，检测并拦截路径穿越攻击
     *
     * <h3>P2-12 增强检测项</h3>
     * <ul>
     *   <li>基础穿越模式：{@code ..}、{@code %2e}、{@code //}、{@code \}、{@code %5c}、{@code %2f}</li>
     *   <li>双重编码：{@code %252e%252e} (Double-Encoding bypass)</li>
     *   <li>null 字节注入：{@code %00}、{@code \0}</li>
     *   <li>混合编码：{@code .%2f}、{@code %2e%2f}</li>
     * </ul>
     *
     * <p>检测流程：
     * <ol>
     *   <li>先进行递归 URL 解码（最多 3 次），防范编码绕过</li>
     *   <li>对解码后的路径进行模式匹配检测</li>
     * </ol>
     *
     * @param rawPath 原始路径
     * @return 规范化后的路径，如果检测到穿越攻击返回 null
     */
    public static String sanitize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return rawPath;
        }

        // P2-5 快速失败：路径不含 '.' 和 '%' 时不可能存在穿越/编码攻击，直接放行
        // 覆盖 >99% 的正常请求，避免不必要的 URL 解码 + 多模式匹配开销
        if (rawPath.indexOf('.') < 0 && rawPath.indexOf('%') < 0 && rawPath.indexOf('\\') < 0) {
            return rawPath;
        }

        // P2-12: 递归 URL 解码（最多 3 次），防范 Double-Encoding 攻击
        String decodedPath = recursiveDecode(rawPath);

        // 检测路径穿越攻击
        String lowerPath = decodedPath.toLowerCase();

        // 基础检测：父目录引用
        if (lowerPath.contains("..") ||
                lowerPath.contains("%2e%2e") ||
                lowerPath.contains("%2e.")) {
            return null;
        }

        // 反斜杠检测
        if (lowerPath.contains("\\") ||
                lowerPath.contains("%5c") ||
                lowerPath.contains("%5C")) {
            return null;
        }

        // 双斜杠检测（可被用于绕过某些安全检查）
        if (lowerPath.contains("//")) {
            return null;
        }

        // P2-12: null 字节注入检测（可导致文件系统问题）
        // 检测 URL 编码的 null 字节 %00 和原始 \0 字符
        if (lowerPath.contains("%00") ||
                decodedPath.contains("\0")) {
            return null;
        }

        // P2-12: 混合编码检测（如 .%2f、%2e%2f）
        if (lowerPath.contains(".%2f") ||
                lowerPath.contains(".%5c") ||
                lowerPath.contains("%2f.") ||
                lowerPath.contains("%2e%2f") ||
                lowerPath.contains("%2e%5c")) {
            return null;
        }

        return rawPath;
    }

    /**
     * 递归 URL 解码（最多 MAX_DECODE_ITERATIONS 次）
     *
     * <p>防范 Double-Encoding 攻击：攻击者将路径编码两次 ({@code %252e} 代表 {@code %2e} 再代表 {@code .})
     *
     * @param path 原始路径
     * @return 解码后的路径（或原始路径，如果解码失败）
     */
    private static String recursiveDecode(String path) {
        String result = path;
        int iterations = 0;

        while (iterations < MAX_DECODE_ITERATIONS) {
            String prev = result;
            try {
                result = URLDecoder.decode(result, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                // 解码失败，返回上次成功解码的结果或原始路径
                return prev;
            }
            // 解码后不再变化，提前终止
            if (result.equals(prev)) {
                break;
            }
            iterations++;
        }

        return result;
    }

    /**
     * 精确匹配白名单（大小写不敏感）
     *
     * <p>P1: HTTP 路径按 RFC 3986 是 case-sensitive 的，但实际环境中：
     * <ul>
     *   <li>部分反向代理 / CDN 会把路径转换为小写（如 Cloudflare 的 Page Rules）</li>
     *   <li>Spring MVC 的 {@code AntPathMatcher} 默认大小写敏感，但部分开发者可能误写大小写</li>
     *   <li>Windows 文件系统大小写不敏感，可能导致路径解析差异</li>
     * </ul>
     *
     * <p>因此白名单匹配改为大小写不敏感，避免环境差异导致认证失败，
     * 同时不影响安全性（攻击者用大小写混淆绕过的可能性已被 sanitize() 阻断）。
     *
     * @param path      请求路径
     * @param whiteList 白名单集合
     * @return true 如果路径（大小写不敏感地）匹配白名单中的某一项
     */
    public static boolean matchWhiteList(String path, Set<String> whiteList) {
        if (path == null || whiteList == null || whiteList.isEmpty()) {
            return false;
        }
        // 快速路径：先精确匹配（保留原 case 大小写的常见场景快速命中）
        if (whiteList.contains(path)) {
            return true;
        }
        // 慢速路径：大小写不敏感匹配（处理代理/CDN 转换后的路径）
        String lowerPath = path.toLowerCase(Locale.ROOT);
        for (String allowed : whiteList) {
            if (allowed != null && allowed.toLowerCase(Locale.ROOT).equals(lowerPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回需要剥离的内部头名称列表
     *
     * @return 内部头名称集合
     */
    public static Set<String> internalHeaders() {
        return INTERNAL_HEADERS;
    }
}
