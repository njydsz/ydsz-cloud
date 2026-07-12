package com.njydsz.pmis.common.safe.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.safe.xss.EscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 带 XSS 防护的 Jackson HTTP 消息转换器
 *
 * <p>继承 {@link MappingJackson2HttpMessageConverter}，在反序列化 JSON 请求体时对字符串值进行 XSS 过滤。
 * 通过重写 {@link #read} 方法，在 Jackson 反序列化前对原始 JSON 字符串进行清洗，
 * 确保所有字符串类型的值都经过 XSS 过滤。
 *
 * <p><b>过滤规则：</b>
 * <ul>
 *   <li>移除 {@code <script>} 标签及其内容</li>
 *   <li>移除 {@code javascript:}、{@code vbscript:}、{@code data:} 等危险协议</li>
 *   <li>移除 {@code on*} 事件属性（如 onclick、onload 等）</li>
 *   <li>HTML 实体编码特殊字符：{@code < > " ' &}</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * 当 {@code ydsz.safe.xss.mode=converter} 时，此转换器会替换默认的 JSON 转换器，
 * 在反序列化阶段完成 XSS 过滤，与 Filter 模式和 Advice 模式互斥。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @see MappingJackson2HttpMessageConverter
 * @see EscapeUtils
 */
// NOTE: MappingJackson2HttpMessageConverter 在 Spring 7.0 已弃用并标记 forRemoval，
// 待项目完成 Jackson 3.x 迁移后替换为 JacksonJsonHttpMessageConverter。
// forRemoval=true 的弃用警告需要 "removal" 而非 "deprecation" 来抑制。
@SuppressWarnings({"deprecation", "removal"})
public class XssJsonMessageConverter extends MappingJackson2HttpMessageConverter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(XssJsonMessageConverter.class);

    /**
     * 转换器优先级，设为最高优先级确保 XSS 过滤最先执行
     */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    /**
     * 支持的媒体类型列表
     */
    private static final List<MediaType> SUPPORTED_MEDIA_TYPES = Arrays.asList(
            MediaType.APPLICATION_JSON,
            new MediaType("application", "*+json")
    );

    /**
     * 构造方法
     *
     * <p>使用默认配置创建 XSS 防护的消息转换器。
     */
    public XssJsonMessageConverter() {
        super();
    }

    /**
     * 构造方法
     *
     * <p>使用指定的 ObjectMapper 创建 XSS 防护的消息转换器。
     *
     * @param objectMapper 待使用的 ObjectMapper
     */
    public XssJsonMessageConverter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    /**
     * 重写支持的媒体类型
     *
     * <p>返回此转换器支持的媒体类型列表：application/json 和 application/*+json
     *
     * @return 支持的媒体类型列表
     */
    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return SUPPORTED_MEDIA_TYPES;
    }

    /**
     * 读取并反序列化 JSON 请求体
     *
     * <p>重写父类方法，在反序列化前对 JSON 字符串值进行 XSS 过滤。
     * 使用 {@link EscapeUtils#cleanJsonValue} 进行流式 JSON 解析和清洗，
     * 确保仅清洗字符串值，不破坏 JSON 结构。
     *
     * @param type          目标类型
     * @param contextClass  上下文类
     * @param inputMessage  HTTP 输入消息
     * @return 反序列化后的对象
     * @throws IOException                     IO异常
     * @throws HttpMessageNotReadableException 消息不可读异常
     */
    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        byte[] originalBytes = inputMessage.getBody().readAllBytes();

        if (originalBytes == null || originalBytes.length == 0) {
            HttpInputMessage emptyInput = new XssByteArrayInputMessage(new byte[0], inputMessage.getHeaders());
            return super.read(type, contextClass, emptyInput);
        }

        String originalJson = new String(originalBytes, StandardCharsets.UTF_8);
        String cleanedJson = EscapeUtils.cleanJsonValue(originalJson);

        if (!cleanedJson.equals(originalJson)) {
            log.debug("[XssJsonMessageConverter] JSON Body XSS 过滤完成");
        }

        byte[] cleanedBytes = cleanedJson.getBytes(StandardCharsets.UTF_8);
        HttpInputMessage cleanedInput = new XssByteArrayInputMessage(cleanedBytes, inputMessage.getHeaders());
        return super.read(type, contextClass, cleanedInput);
    }

    /**
     * 序列化对象为 JSON 响应体（不修改）
     */
    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotReadableException {
        super.writeInternal(object, outputMessage);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 基于 ByteArrayInputStream 的 HttpInputMessage 实现
     *
     * <p>用于包装清洗后的 JSON 字节数组，供 Jackson 反序列化使用。
     */
    private static class XssByteArrayInputMessage implements HttpInputMessage {

        private final byte[] body;
        private final HttpHeaders headers;

        XssByteArrayInputMessage(byte[] body, HttpHeaders headers) {
            this.body = body;
            this.headers = headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
