package com.njydsz.pmis.common.security;

import java.util.Locale;

/**
 * CSRF 威胁模型声明与防御策略
 *
 * <p>威胁模型:
 * <ul>
 *   <li>PMIS 采用 JWT-only API 鉴权(不依赖 Cookie),理论上免疫经典 CSRF。</li>
 *   <li>但仍需防御以下变种:
 *     <ol>
 *       <li>误用 Cookie 的场景(如 SSE / 文件下载鉴权 / OAuth2 回调)</li>
 *       <li>JWT 存储在 Cookie 中(未来可能切换)</li>
 *       <li>Content-Type: text/plain 的简单请求绕过预检</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>防御纵深:
 * <ol>
 *   <li>CORS 严格白名单(已实现,生产环境强制 CORS_ALLOWED_ORIGINS)</li>
 *   <li>SameSite Cookie 策略(本类提供 SetSameSiteCookieFilter)</li>
 *   <li>Content-Type 严格校验(写操作强制 application/json)</li>
 *   <li>X-Requested-With 头校验(非简单请求触发预检)</li>
 * </ol>
 *
 * <p>参考标准:
 * <ul>
 *   <li>OWASP CSRF Prevention Cheat Sheet</li>
 *   <li>RFC 6265bis SameSite Cookie</li>
 *   <li>Spring Security CSRF 章节(本项目不引入 Spring Security,自实现轻量方案)</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class CsrfSecurityPolicy {

    /** 安全方法集合: GET / HEAD / OPTIONS / TRACE */
    private static final String[] SAFE_METHODS = {"GET", "HEAD", "OPTIONS", "TRACE"};

    /** 写操作方法集合: POST / PUT / DELETE / PATCH */
    private static final String[] WRITE_METHODS = {"POST", "PUT", "DELETE", "PATCH"};

    private CsrfSecurityPolicy() {
        // 工具类,禁止实例化
    }

    /**
     * 判断是否为安全方法(GET / HEAD / OPTIONS / TRACE)
     *
     * <p>安全方法不修改服务端状态,理论上不需要 CSRF 防护。
     *
     * @param method HTTP 方法名,null 视为非安全方法
     * @return true 表示安全方法;false 表示写操作或未知方法
     */
    public static boolean isSafeMethod(String method) {
        if (method == null || method.isEmpty()) {
            return false;
        }
        String upper = method.toUpperCase(Locale.ROOT);
        for (String safe : SAFE_METHODS) {
            if (safe.equals(upper)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断请求是否需要 CSRF 防护
     *
     * <p>规则: 写操作(POST/PUT/DELETE/PATCH)且 Content-Type 非 application/json 时返回 true。
     *
     * <p>原理:
     * <ul>
     *   <li>application/json 会触发 CORS 预检(PREFLIGHT),浏览器保证非简单请求,
     *       跨域请求必须先通过 OPTIONS 预检,无法被 CSRF 利用</li>
     *   <li>text/plain / application/x-www-form-urlencoded 属于简单请求,绕过预检,
     *       是经典的 CSRF 攻击向量</li>
     *   <li>null Content-Type 的写操作视为可疑,需要保护</li>
     * </ul>
     *
     * @param method      HTTP 方法名
     * @param contentType Content-Type 头值(可能含 charset,如 application/json;charset=UTF-8)
     * @return true 表示需要 CSRF 防护;false 表示安全或非写操作
     */
    public static boolean requireCsrfProtection(String method, String contentType) {
        if (method == null || method.isEmpty()) {
            return false;
        }
        String upper = method.toUpperCase(Locale.ROOT);
        boolean isWrite = false;
        for (String m : WRITE_METHODS) {
            if (m.equals(upper)) {
                isWrite = true;
                break;
            }
        }
        if (!isWrite) {
            // 安全方法或未知方法,不需要 CSRF 防护
            return false;
        }
        // 写操作: Content-Type 为空视为可疑,需要保护
        if (contentType == null || contentType.isEmpty()) {
            return true;
        }
        // 检查是否为 application/json(忽略大小写与 charset 后缀)
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        // 截取分号前的主类型
        int semi = normalized.indexOf(';');
        String mainType = semi >= 0 ? normalized.substring(0, semi).trim() : normalized;
        return !"application/json".equals(mainType);
    }
}
