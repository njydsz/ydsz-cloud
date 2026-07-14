package com.njydsz.pmis.common.json.spring;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import com.njydsz.pmis.common.json.Json;

/**
 * Json HTTP 消息转换器。
 *
 * <p>通用 JSON 消息转换器，支持所有 Java 对象类型的 JSON 序列化/反序列化。
 * 自动注册到 Spring MVC 的 {@code HttpMessageConverter} 链中。
 *
 * <p>支持 {@code application/json} 和 {@code application/*+json} 媒体类型。
 *
 * <p><b>优化：</b></p>
 * <ul>
 *   <li>写入时直接输出 UTF-8 字节并设置 Content-Length，避免 chunked 编码开销</li>
 *   <li>读取时在读取前校验 Content-Length，防止超大 payload DoS 攻击</li>
 *   <li>不手动 flush，由 Spring 框架统一管理输出流生命周期</li>
 * </ul>
 *
 * @since 1.3.0
 */
public class JsonHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    /** 默认最大请求体大小（10MB），超过此值的请求将被拒绝 */
    private static final long MAX_REQUEST_BODY_SIZE = 10L * 1024 * 1024;

    /**
     * 构造函数，注册支持的媒体类型。
     */
    public JsonHttpMessageConverter() {
        super(StandardCharsets.UTF_8,
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        // 通用转换器，支持所有非 CharSequence 类型
        return !CharSequence.class.isAssignableFrom(clazz);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        try {
            // 读取前校验 Content-Length，防止超大 payload DoS
            long contentLength = inputMessage.getHeaders().getContentLength();
            if (contentLength > MAX_REQUEST_BODY_SIZE) {
                throw new IOException("Request body too large: " + contentLength
                        + " > " + MAX_REQUEST_BODY_SIZE);
            }

            byte[] body = inputMessage.getBody().readAllBytes();
            if (body.length == 0) {
                return null;
            }
            String json = new String(body, getDefaultCharset());
            return Json.toObject(json, clazz);
        } catch (Exception e) {
            throw new HttpMessageNotReadableException("JSON 解析失败：" + e.getMessage(), e, inputMessage);
        }
    }

    @Override
    protected void writeInternal(Object o, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        try {
            byte[] bytes = Json.toJsonBytes(o);
            // 设置 Content-Length，避免 HTTP chunked 编码开销
            outputMessage.getHeaders().setContentLength(bytes.length);
            OutputStream out = outputMessage.getBody();
            out.write(bytes);
            // 不手动 flush，由 Spring 框架统一管理输出流生命周期
        } catch (Exception e) {
            throw new HttpMessageNotWritableException("JSON 序列化失败：" + e.getMessage(), e);
        }
    }

}
