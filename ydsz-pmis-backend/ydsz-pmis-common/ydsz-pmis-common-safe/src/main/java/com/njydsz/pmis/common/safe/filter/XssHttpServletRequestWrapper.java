package com.njydsz.pmis.common.safe.filter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.njydsz.pmis.common.safe.xss.EscapeUtils;
import com.njydsz.pmis.common.util.string.StringUtils;

/**
 * XSS 过滤请求包装器
 *
 * <p>对 HTTP 请求参数和 JSON 请求体进行 XSS 安全过滤。
 * 继承 HttpServletRequestWrapper，拦截并清洗用户输入。
 *
 * <p><b>过滤内容：</b>
 * <ul>
 *   <li>表单参数：通过 getParameterValues 获取并过滤</li>
 *   <li>JSON 请求体：通过 getInputStream 获取并过滤</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * 通常由 {@link XssFilter} 创建并传递给 FilterChain。
 *
 * @since 1.0.0
 * 
 * @see XssFilter
 * @see EscapeUtils
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private volatile byte[] requestBody;

    private volatile boolean requestBodyInitialized;

    private final Object lock = new Object();

    private final byte[] cachedBody;

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.cachedBody = null;
    }

    /**
     * 使用预缓存的请求体构造
     * @param request 原始请求
     * @param cachedBody XssFilter 缓存的请求体（用于 XSS 检测和包装器复用）
     */
    public XssHttpServletRequestWrapper(HttpServletRequest request, XssFilter.CachedRequestBody cachedBody) {
        super(request);
        if (cachedBody != null) {
            this.cachedBody = cachedBody.getBytes();
        } else {
            this.cachedBody = null;
        }
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values != null) {
            String[] escapedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escapedValues[i] = EscapeUtils.clean(values[i]).trim();
            }
            return escapedValues;
        }
        return super.getParameterValues(name);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (!isJsonRequest()) {
            return super.getInputStream();
        }
        byte[] jsonBytes = getOrInitRequestBody();
        final ByteArrayInputStream bis = new ByteArrayInputStream(jsonBytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bis.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public int available() throws IOException {
                return bis.available();
            }

            @Override
            public void setReadListener(ReadListener listener) {
            }

            @Override
            public int read() throws IOException {
                return bis.read();
            }
        };
    }

    /**
     * 获取或初始化请求体
     *
     * <p>懒加载模式，首次调用时读取并清洗请求体。
     * 清洗使用 EscapeUtils.clean() 方法。
     *
     * <p>使用双重检查锁定替代 synchronized 方法，降低并发竞争。</p>
     *
     * @return 清洗后的请求体字节数组
     * @throws IOException IO异常
     */
    private byte[] getOrInitRequestBody() throws IOException {
        if (requestBodyInitialized) {
            return requestBody;
        }
        synchronized (lock) {
            if (requestBodyInitialized) {
                return requestBody;
            }
            // 使用 XssFilter 预缓存的请求体（已避免 InputStream 被重复消费）
            if (cachedBody != null) {
                String cachedJson = new String(cachedBody, StandardCharsets.UTF_8);
                if (StringUtils.isEmpty(cachedJson)) {
                    requestBody = new byte[0];
                } else {
                    String cleanJson = EscapeUtils.cleanJsonValue(cachedJson).trim();
                    requestBody = cleanJson.getBytes(StandardCharsets.UTF_8);
                }
                requestBodyInitialized = true;
                return requestBody;
            }
            byte[] bytes = super.getInputStream().readAllBytes();
            if (bytes == null || bytes.length == 0) {
                requestBody = new byte[0];
            } else {
                String json = new String(bytes, StandardCharsets.UTF_8);
                if (StringUtils.isEmpty(json)) {
                    requestBody = new byte[0];
                } else {
                    String cleanJson = EscapeUtils.cleanJsonValue(json).trim();
                    requestBody = cleanJson.getBytes(StandardCharsets.UTF_8);
                }
            }
            requestBodyInitialized = true;
            return requestBody;
        }
    }

    /**
     * 判断是否为 JSON 请求
     *
     * @return 是否为 JSON 请求
     */
    public boolean isJsonRequest() {
        String contentType = super.getHeader(HttpHeaders.CONTENT_TYPE);
        return StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE);
    }
}
