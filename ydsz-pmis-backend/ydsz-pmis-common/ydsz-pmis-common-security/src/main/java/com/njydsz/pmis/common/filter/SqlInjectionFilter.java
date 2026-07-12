package com.njydsz.pmis.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * SQL 注入防御过滤器
 *
 * <p>拦截请求参数中的 SQL 注入关键字，阻止恶意 SQL 片段进入业务层。
 *
 * <h3>检测规则</h3>
 * <ul>
 *   <li>UNION SELECT</li>
 *   <li>OR 1=1 / AND 1=1</li>
 *   <li>注释符（双连字符、斜杠星号）</li>
 *   <li>存储过程调用 EXEC / EXECUTE</li>
 *   <li>堆叠查询 ;</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
public class SqlInjectionFilter implements Filter {

    /** SQL 注入检测正则（大小写不敏感） */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(?:'|(?:--)|(?:;)|(?:/\\*)|(?:\\*/)|(?:\\bUNION\\b.*\\bSELECT\\b)|(?:\\bOR\\b\\s+\\d+=\\d+)|(?:\\bAND\\b\\s+\\d+=\\d+)|(?:\\bEXEC(?:UTE)?\\b)|(?:\\bINSERT\\b.*\\bINTO\\b)|(?:\\bDELETE\\b.*\\bFROM\\b)|(?:\\bDROP\\b.*\\bTABLE\\b)|(?:\\bUPDATE\\b.*\\bSET\\b)|(?:\\bXP_CMDSHELL\\b))"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            chain.doFilter(new SqlInjectionRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * 请求包装器：检测参数中的 SQL 注入
     */
    private static class SqlInjectionRequestWrapper extends HttpServletRequestWrapper {

        public SqlInjectionRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return sanitize(value, name);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] sanitized = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitized[i] = sanitize(values[i], name);
            }
            return sanitized;
        }

        private String sanitize(String value, String paramName) {
            if (value == null) return null;
            if (SQL_INJECTION_PATTERN.matcher(value).find()) {
                log.warn("[SQLInjection] 检测到可疑SQL注入: param={} value={}", paramName,
                        value.length() > 100 ? value.substring(0, 100) + "..." : value);
                // 清除恶意内容，返回空字符串
                return "";
            }
            return value;
        }
    }
}
