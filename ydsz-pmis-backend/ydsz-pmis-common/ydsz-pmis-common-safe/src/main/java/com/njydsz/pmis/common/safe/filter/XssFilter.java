package com.njydsz.pmis.common.safe.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.pmis.common.safe.alert.SafeAlertProperties;
import com.njydsz.pmis.common.safe.alert.SecurityEvent;
import com.njydsz.pmis.common.safe.alert.SecurityEventPublisher;
import com.njydsz.pmis.common.safe.alert.SecurityEventType;
import com.njydsz.pmis.common.safe.xss.EscapeUtils;
import com.njydsz.pmis.common.safe.util.ClientIpResolver;
import com.njydsz.pmis.common.util.url.UrlPathUtils;

/**
 * XSS 安全防护过滤器
 * <p>
 * 全局 HTTP 请求参数与 JSON 请求体的 XSS 攻击过滤。
 * 基于 Spring {@link OncePerRequestFilter} 实现，在请求进入 Controller 之前完成参数清洗。
 * </p>
 *
 * <p><b>威胁模型：</b>攻击者通过查询参数、表单字段、JSON Body 注入 JavaScript / HTML 片段，
 * 实现 cookie 窃取、钓鱼、UI 伪装、键盘记录等 XSS 攻击。</p>
 *
 * <p><b>核心特性：</b></p>
 * <ul>
 *   <li>全局过滤：一次配置，全局生效</li>
 *   <li>智能排除：支持 Ant 风格路径匹配，排除无需过滤的端点</li>
 *   <li>JSON 支持：可处理 JSON 请求体的 XSS 攻击</li>
 *   <li>OncePerRequest：基于 Spring 过滤器，确保每次请求只执行一次</li>
 *   <li>安全告警：检测到 XSS 攻击时发布安全事件</li>
 * </ul>
 *
 * <p><b>过滤范围：</b></p>
 * <ul>
 *   <li>排除路径列表中的端点不过滤</li>
 *   <li>其他所有请求的参数和 JSON Body 都会经过 XSS 过滤</li>
 * </ul>
 *
 * <p><b>性能影响：</b>每次请求都会执行参数遍历和字符串替换，对高 QPS 接口需评估
 * 性能开销。JSON Body 在内存中缓存（10MB 上限），不应作为大文件上传接口的兜底。</p>
 *
 * @since 1.0.0
 * @since 1.0.0
 * @see XssHttpServletRequestWrapper
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class XssFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(XssFilter.class);

    /**
     * 默认 XSS 排除路径列表
     */
    private static final List<String> DEFAULT_EXCLUDES = new ArrayList<>();

    static {
        DEFAULT_EXCLUDES.add("/error");
        DEFAULT_EXCLUDES.add("/favicon.ico");
        DEFAULT_EXCLUDES.add("/actuator/**");
    }

    /** 排除路径列表（Ant 风格） */
    private final List<String> excludes;
    /** 安全事件发布器（可为 null） */
    private final SecurityEventPublisher eventPublisher;
    /** 安全告警配置（可为 null） */
    private final SafeAlertProperties alertProperties;

    /**
     * 默认构造器：使用默认排除路径，不发布安全事件
     */
    public XssFilter() {
        this.excludes = new ArrayList<>(DEFAULT_EXCLUDES);
        this.eventPublisher = null;
        this.alertProperties = null;
    }

    /**
     * 自定义排除路径构造器
     *
     * @param excludes 排除路径列表（null 时使用默认）
     */
    public XssFilter(List<String> excludes) {
        this.excludes = excludes == null ? new ArrayList<>() : new ArrayList<>(excludes);
        if (this.excludes.isEmpty()) {
            this.excludes.addAll(DEFAULT_EXCLUDES);
        }
        this.eventPublisher = null;
        this.alertProperties = null;
    }

    /**
     * 完整构造器
     *
     * @param excludes         排除路径列表
     * @param eventPublisher   安全事件发布器（可为 null）
     * @param alertProperties   安全告警配置（可为 null）
     */
    public XssFilter(List<String> excludes, SecurityEventPublisher eventPublisher,
                     SafeAlertProperties alertProperties) {
        this.excludes = excludes == null ? new ArrayList<>() : new ArrayList<>(excludes);
        if (this.excludes.isEmpty()) {
            this.excludes.addAll(DEFAULT_EXCLUDES);
        }
        this.eventPublisher = eventPublisher;
        this.alertProperties = alertProperties;
    }

    /**
     * 过滤器核心逻辑
     * <ol>
     *   <li>排除路径直接放行</li>
     *   <li>JSON 请求体先缓存并执行 XSS 攻击检测，命中时发布安全事件</li>
     *   <li>非 JSON 请求遍历参数做 XSS 检测</li>
     *   <li>使用 {@link XssHttpServletRequestWrapper} 包装请求，自动清洗参数</li>
     * </ol>
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws IOException      IO 异常
     * @throws ServletException Servlet 异常
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain)
            throws IOException, ServletException {
        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 对于 JSON 请求，缓存请求体以支持 XSS 检测和后续 Wrapper 读取
        CachedRequestBody cachedBody = null;
        if (isJsonRequest(request)) {
            try {
                byte[] bodyBytes = request.getInputStream().readAllBytes();
                cachedBody = new CachedRequestBody(bodyBytes);
                // 使用缓存的请求体进行 XSS 检测
                if (cachedBody.hasText() && EscapeUtils.containsXSS(cachedBody.getText())) {
                    publishEvent(request, cachedBody.getText());
                }
            } catch (IOException e) {
                // 读取失败时 fail-closed：记录日志并抛出，避免后续 Wrapper 读取已消费的 InputStream 出错
                log.warn("XSS 过滤器读取请求体失败 | URI: {} | 消息: {}", request.getRequestURI(), e.getMessage());
                throw e;
            }
        } else {
            // 非 JSON 请求只检测参数
            detectAndPublishXssEvent(request);
        }

        XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(request, cachedBody);
        filterChain.doFilter(xssRequest, response);
    }

    /**
     * 检测非 JSON 请求参数中的 XSS 攻击，并发布安全事件
     * <p>仅扫描非 JSON 请求。JSON 请求体的检测在 {@link #doFilterInternal} 中完成。
     */
    private void detectAndPublishXssEvent(HttpServletRequest request) {
        if (eventPublisher == null || alertProperties == null || !alertProperties.isEnabled()) {
            return;
        }

        // 检查请求参数
        String[] paramNames = request.getParameterMap().keySet().toArray(new String[0]);
        for (String name : paramNames) {
            String[] values = request.getParameterValues(name);
            if (values != null) {
                for (String value : values) {
                    if (EscapeUtils.containsXSS(value)) {
                        publishEvent(request, value);
                        return;
                    }
                }
            }
        }
    }

    /**
     * 发布 XSS 攻击安全事件
     *
     * @param request HTTP 请求
     * @param payload 触发检测的攻击载荷
     */
    private void publishEvent(HttpServletRequest request, String payload) {
        SecurityEvent event = new SecurityEvent(
                SecurityEventType.XSS_ATTACK,
                request.getRequestURI(),
                ClientIpResolver.getClientIp(request),
                request.getHeader("User-Agent"),
                payload,
                SecurityEvent.Severity.HIGH
        );
        eventPublisher.publish(event);
    }

    /**
     * 判断请求是否为 JSON 请求
     *
     * @param request HTTP 请求
     * @return Content-Type 包含 {@code application/json} 时返回 true
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getHeader("Content-Type");
        return StringUtils.hasText(contentType) &&
               contentType.toLowerCase().contains("application/json");
    }

    /**
     * 缓存的请求体，用于支持 XSS 检测和后续 Wrapper 重复读取
     */
    static class CachedRequestBody {
        /** 原始字节数组 */
        private final byte[] bytes;
        /** 原始字符串（UTF-8 编码） */
        private final String text;

        CachedRequestBody(byte[] bytes) {
            this.bytes = bytes;
            this.text = new String(bytes, StandardCharsets.UTF_8);
        }

        byte[] getBytes() {
            return bytes;
        }

        String getText() {
            return text;
        }

        boolean hasText() {
            return StringUtils.hasText(text);
        }
    }

    /**
     * 判断请求路径是否需要排除 XSS 过滤
     *
     * @param request HTTP 请求
     * @return 需要排除返回 true
     */
    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }
}
