package com.njydsz.pmis.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.CsrfSecurityPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Content-Type 严格校验过滤器(CSRF 防御纵深)
 *
 * <p>对写操作(POST/PUT/DELETE/PATCH)强制校验 Content-Type,拒绝简单请求类型,
 * 确保跨域写请求必须先通过 CORS 预检(PREFLIGHT),从而阻断 CSRF 攻击向量。
 *
 * <p>允许的 Content-Type:
 * <ul>
 *   <li>application/json(含 charset 变体)— 触发预检,安全</li>
 *   <li>multipart/form-data — 文件上传场景</li>
 * </ul>
 *
 * <p>拒绝的 Content-Type:
 * <ul>
 *   <li>text/plain — 简单请求,绕过预检</li>
 *   <li>application/x-www-form-urlencoded — 简单请求,绕过预检</li>
 *   <li>其他非 JSON 类型</li>
 * </ul>
 *
 * <p>白名单路径: /actuator/** / /v3/api-docs/** / /swagger-ui/** / /auth/**
 * (监控端点、OpenAPI 文档、登录接口可能使用表单提交)
 *
 * <p>顺序: 在 {@link SameSiteCookieFilter}(HIGHEST_PRECEDENCE + 2)之后执行。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class StrictContentTypeFilter extends OncePerRequestFilter {

    /** JSON ObjectMapper,用于序列化错误响应 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 白名单路径前缀(写操作不校验 Content-Type) */
    private static final String[] WHITELIST_PATHS = {
            "/actuator/",
            "/v3/api-docs/",
            "/swagger-ui/",
            "/auth/"
    };

    /** 允许的 Content-Type 主类型 */
    private static final String ALLOWED_JSON = "application/json";
    private static final String ALLOWED_MULTIPART = "multipart/form-data";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String method = request.getMethod();

        // 仅对写操作校验
        if (CsrfSecurityPolicy.isSafeMethod(method)) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // 白名单路径放行
        if (isWhitelisted(path)) {
            chain.doFilter(request, response);
            return;
        }

        String contentType = request.getContentType();

        // 写操作且 Content-Type 不在允许列表 → 拒绝
        if (!isAllowedContentType(contentType)) {
            log.warn("[StrictContentTypeFilter] 拒绝非 JSON 写请求 method={} path={} contentType={}",
                    method, path, contentType);
            rejectUnsupportedMediaType(response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 判断路径是否在白名单中
     *
     * <p>匹配规则: 路径以白名单前缀开头,或包含该前缀片段(兼容 /api/v1/auth/login 等嵌套路径)。
     * 前缀已含前导斜杠与尾随斜杠,避免误匹配(如 /someauth/ 不会命中 /auth/)。
     *
     * @param path 请求 URI
     * @return true 表示白名单路径
     */
    private boolean isWhitelisted(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        for (String prefix : WHITELIST_PATHS) {
            // 前缀已含前导斜杠,contains 即可覆盖根级与嵌套两种场景
            if (normalized.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 Content-Type 是否允许
     *
     * <p>允许: application/json(含 charset 变体)、multipart/form-data
     *
     * @param contentType Content-Type 头值
     * @return true 表示允许
     */
    private boolean isAllowedContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            // 写操作必须显式声明 Content-Type
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        // 截取分号前的主类型
        int semi = normalized.indexOf(';');
        String mainType = semi >= 0 ? normalized.substring(0, semi).trim() : normalized;
        return ALLOWED_JSON.equals(mainType) || ALLOWED_MULTIPART.equals(mainType);
    }

    /**
     * 返回 415 Unsupported Media Type 错误响应
     *
     * @param response HTTP 响应
     * @throws IOException 写入响应体时发生 IO 异常
     */
    private void rejectUnsupportedMediaType(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> body = Result.failed(BizErrorCode.UNSUPPORTED_MEDIA_TYPE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
        response.getWriter().flush();
    }
}
