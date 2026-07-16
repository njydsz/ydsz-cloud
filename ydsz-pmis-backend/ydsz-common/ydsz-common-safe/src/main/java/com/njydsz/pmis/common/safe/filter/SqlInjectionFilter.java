package com.njydsz.common.safe.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.safe.alert.SecurityEvent;
import com.njydsz.common.safe.alert.SecurityEventPublisher;
import com.njydsz.common.safe.alert.SecurityEventType;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.safe.filter.CachedBodyHttpServletRequestWrapper;
import com.njydsz.common.util.url.UrlPathUtils;

/**
 * SQL 注入防护过滤器
 * <p>
 * 检测并拦截 HTTP 请求中的 SQL 注入攻击，保护应用安全。基于正则的"启发式 +
 * 行为特征"模式，平衡误报率与漏报率，是 MyBatis/MyBatis-Plus 等 ORM 场景
 * 的最后一道防线（最佳实践是结合预编译 SQL 防止注入）。
 * </p>
 *
 * <p><b>威胁模型：</b>攻击者通过查询参数、表单字段、HTTP Header、JSON Body
 * 注入 SQL 语句，绕过认证、读取敏感数据、破坏数据完整性。</p>
 *
 * <p><b>检测范围：</b></p>
 * <ul>
 *   <li>请求参数（Query String）</li>
 *   <li>请求头（Header）：User-Agent、Referer、X-Forwarded-For</li>
 *   <li>请求体（Body）：仅 JSON/XML 格式，最大 64KB 截断检测</li>
 * </ul>
 *
 * <p><b>检测规则（攻击特征）：</b></p>
 * <ul>
 *   <li>UNION SELECT 联合查询注入</li>
 *   <li>布尔型注入：OR/AND 数字=数字（如 {@code OR 1=1}）</li>
 *   <li>引号 + 逻辑运算符（如 {@code ' OR '}）</li>
 *   <li>堆叠查询：引号/分号后跟 DDL/DML</li>
 *   <li>SQL 注释符 {@code --} / {@code /*}</li>
 *   <li>存储过程执行 EXEC / XP_</li>
 *   <li>时间盲注 SLEEP / BENCHMARK / WAITFOR DELAY</li>
 *   <li>危险文件操作 INTO OUTFILE / LOAD_FILE</li>
 *   <li>INFORMATION_SCHEMA 探测</li>
 * </ul>
 *
 * <p><b>配置项：</b></p>
 * <ul>
 *   <li>{@code ydsz.safe.sql-injection.enabled} - 是否启用（默认 true）</li>
 *   <li>{@code ydsz.safe.sql-injection.block-on-detect} - 检测到攻击时是否阻断（默认 true）</li>
 *   <li>{@code ydsz.safe.sql-injection.whitelist-paths} - 排除检测的 URL 模式</li>
 *   <li>{@code ydsz.safe.sql-injection.whitelist-params} - 白名单参数名（其值不检测）</li>
 * </ul>
 *
 * <p><b>误报控制：</b>不匹配裸 SQL 关键字（避免正常业务查询"select user"等被误判），
 * 仅匹配组合攻击特征。表单中包含 SQL 字段名（如 ORDER BY DESC）时建议加入白名单参数。</p>
 *
 * @since 1.0.0
 */
public class SqlInjectionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SqlInjectionFilter.class);

    /**
     * 默认 SQL 注入检测正则表达式
     */
    private static final String DEFAULT_PATTERN_STR =
            "(?i)" +
                    // UNION SELECT 联合查询注入
                    "(?:\\bUNION\\s+(?:ALL\\s+)?SELECT\\b)" +
                    // 布尔型注入：OR/AND 数字=数字（如 OR 1=1, AND '1'='1'）
                    "|(?:\\b(?:OR|AND)\\b\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?)" +
                    // 引号 + 逻辑运算符（如 ' OR ', ' AND '）
                    "|(?:['\"]\\s*(?:OR|AND)\\s+['\"])" +
                    // 堆叠查询：引号/分号后跟危险 SQL 语句
                    "|(?:['\";]\\s*(?:DROP|DELETE|TRUNCATE|ALTER|CREATE|INSERT|UPDATE)\\b)" +
                    // SQL 行注释符 --
                    "|(?:--\\s)" +
                    // SQL 块注释符 /*
                    "|(?:/\\*)" +
                    // 存储过程执行
                    "|(?:\\b(?:EXEC|EXECUTE)\\s*\\()" +
                    "|(?:\\bXP_\\w+)" +
                    // 时间盲注
                    "|(?:\\bWAITFOR\\s+DELAY\\b)" +
                    "|(?:\\bSLEEP\\s*\\()" +
                    "|(?:\\bBENCHMARK\\s*\\()" +
                    // 危险文件操作
                    "|(?:\\bINTO\\s+(?:OUTFILE|DUMPFILE)\\b)" +
                    "|(?:\\bLOAD_FILE\\s*\\()" +
                    // 信息 schema 探测
                    "|(?:\\bINFORMATION_SCHEMA\\b)";

    /**
     * 当前使用的 SQL 注入检测 Pattern（支持运行时热更新）
     */
    private volatile Pattern sqlInjectionPattern = Pattern.compile(DEFAULT_PATTERN_STR);

    /** 请求体最大检测长度，避免大请求体导致性能问题 */
    private static final int MAX_BODY_DETECT_LENGTH = 65536;

    /**
     * P1-11: 运行时热更新 SQL 注入检测规则
     *
     * <p>通过传入新的正则表达式替换当前检测规则，无需重启服务。
     * 使用 volatile 保证可见性，线程安全。
     *
     * @param newPattern 新的 SQL 注入检测正则表达式
     */
    public void updatePattern(String newPattern) {
        if (newPattern != null && !newPattern.trim().isEmpty()) {
            this.sqlInjectionPattern = Pattern.compile(newPattern);
            log.info("SQL 注入检测规则已热更新");
        }
    }

    /**
     * 重置为默认检测规则
     */
    public void resetPattern() {
        this.sqlInjectionPattern = Pattern.compile(DEFAULT_PATTERN_STR);
        log.info("SQL 注入检测规则已重置为默认");
    }

    /**
     * 是否启用阻断模式
     */
    private final boolean blockOnDetect;

    /**
     * 安全事件发布器
     */
    private final SecurityEventPublisher eventPublisher;

    /**
     * 白名单路径（Ant 风格），匹配时跳过检测
     */
    private final List<String> whitelistPaths;

    /**
     * 白名单参数名，匹配的参数值跳过检测
     */
    private final List<String> whitelistParams;

    public SqlInjectionFilter(boolean blockOnDetect, SecurityEventPublisher eventPublisher) {
        this(blockOnDetect, eventPublisher, null, null);
    }

    public SqlInjectionFilter(boolean blockOnDetect, SecurityEventPublisher eventPublisher,
                              List<String> whitelistPaths, List<String> whitelistParams) {
        this.blockOnDetect = blockOnDetect;
        this.eventPublisher = eventPublisher;
        this.whitelistPaths = filterNotBlank(whitelistPaths);
        this.whitelistParams = filterNotBlank(whitelistParams);
    }

    public SqlInjectionFilter() {
        this(true, null, null, null);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 白名单路径跳过检测
        if (UrlPathUtils.matchAny(whitelistPaths, request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 读取并缓存 JSON/XML 请求体（解决 InputStream 只能读取一次的问题）
        CachedBodyHttpServletRequestWrapper wrappedRequest = null;
        String bodyContent = null;
        String contentType = request.getContentType();
        if (contentType != null
                && (contentType.contains("application/json") || contentType.contains("application/xml"))
                && !(request instanceof CachedBodyHttpServletRequestWrapper)) {
            try {
                byte[] bodyBytes = request.getInputStream().readAllBytes();
                bodyContent = new String(bodyBytes, StandardCharsets.UTF_8);
                if (bodyContent.length() > MAX_BODY_DETECT_LENGTH) {
                    bodyContent = bodyContent.substring(0, MAX_BODY_DETECT_LENGTH);
                }
                wrappedRequest = new CachedBodyHttpServletRequestWrapper(request, bodyBytes);
            } catch (IOException e) {
                // 读取请求体失败，仅检测参数和请求头
            }
        }

        // 检测请求参数、请求头和请求体
        if (detectSqlInjection(request, bodyContent)) {
            String clientIp = ClientIpResolver.getClientIp(request);
            String queryString = request.getQueryString();

            log.warn("【SQL注入防护】检测到可疑请求 | ip={} | uri={} | query={}",
                    clientIp, uri, queryString);

            // 发布安全事件
            if (eventPublisher != null) {
                SecurityEvent event = new SecurityEvent(
                        SecurityEventType.SQL_INJECTION,
                        uri,
                        clientIp,
                        request.getHeader("User-Agent"),
                        queryString,
                        SecurityEvent.Severity.HIGH
                );
                eventPublisher.publish(event);
            }

            if (blockOnDetect) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"A04053\",\"msg\":\"请求包含非法字符\"}");
                return;
            }
        }

        // 请求体已缓存时传递包装请求，确保下游可重复读取
        filterChain.doFilter(wrappedRequest != null ? wrappedRequest : request, response);
    }

    /**
     * 检测请求中是否包含 SQL 注入特征
     *
     * <p>检测范围：
     * <ul>
     *   <li>请求参数（逐值检测，跳过白名单参数）</li>
     *   <li>关键请求头（User-Agent, Referer, X-Forwarded-For）</li>
     *   <li>请求体（由调用方读取后传入，仅 JSON/XML 类型）</li>
     * </ul>
     *
     * @param request     HTTP 请求
     * @param bodyContent 已读取的请求体内容（可为 null）
     * @return true 表示检测到 SQL 注入
     */
    private boolean detectSqlInjection(HttpServletRequest request, String bodyContent) {
        // 检测请求参数（逐值检测，跳过白名单参数名）
        Map<String, String[]> paramMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            if (whitelistParams.contains(entry.getKey())) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (StringUtils.hasText(value) && sqlInjectionPattern.matcher(value).find()) {
                    return true;
                }
            }
        }

        // 检测关键请求头
        String[] headersToCheck = {"User-Agent", "Referer", "X-Forwarded-For"};
        for (String headerName : headersToCheck) {
            String headerValue = request.getHeader(headerName);
            if (StringUtils.hasText(headerValue) && sqlInjectionPattern.matcher(headerValue).find()) {
                return true;
            }
        }

        // 检测请求体
        if (StringUtils.hasText(bodyContent) && sqlInjectionPattern.matcher(bodyContent).find()) {
            return true;
        }

        return false;
    }

    /**
     * 过滤掉列表中的空白字符串（处理 @Value 空默认值场景）
     */
    private static List<String> filterNotBlank(List<String> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(list.size());
        for (String item : list) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }


}
