package com.njydsz.common.safe.converter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.safe.xss.EscapeUtils;

/**
 * 带 XSS 防护的 YdszJson HTTP 消息转换器
 *
 * <p>继承 {@link JsonHttpMessageConverter}，在反序列化 JSON 请求体时对字符串值进行 XSS 过滤。
 * 通过重写 {@link #readInternal} 方法，在 YdszJson 反序列化前对原始 JSON 字符串进行清洗，
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
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see JsonHttpMessageConverter
 * @see EscapeUtils
 */
public class XssJsonMessageConverter extends JsonHttpMessageConverter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(XssJsonMessageConverter.class);

    /**
     * 转换器优先级，设为最高优先级确保 XSS 过滤最先执行
     */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    /**
     * 构造方法
     *
     * <p>使用默认配置创建 XSS 防护的消息转换器。
     */
    public XssJsonMessageConverter() {
        super();
    }

    /**
     * 读取并反序列化 JSON 请求体
     *
     * <p>重写父类方法，在反序列化前对 JSON 字符串值进行 XSS 过滤。
     * 使用 {@link EscapeUtils#cleanJsonValue} 进行流式 JSON 解析和清洗，
     * 确保仅清洗字符串值，不破坏 JSON 结构。
     *
     * @param clazz         目标类型
     * @param inputMessage  HTTP 输入消息
     * @return 反序列化后的对象
     * @throws IOException                     IO异常
     * @throws HttpMessageNotReadableException 消息不可读异常
     */
    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        byte[] originalBytes = inputMessage.getBody().readAllBytes();

        if (originalBytes == null || originalBytes.length == 0) {
            HttpInputMessage emptyInput = new XssByteArrayInputMessage(new byte[0], inputMessage.getHeaders());
            return super.readInternal(clazz, emptyInput);
        }

        String originalJson = new String(originalBytes, StandardCharsets.UTF_8);
        String cleanedJson = EscapeUtils.cleanJsonValue(originalJson);

        if (!cleanedJson.equals(originalJson)) {
            log.debug("[XssJsonMessageConverter] JSON Body XSS 过滤完成");
        }

        byte[] cleanedBytes = cleanedJson.getBytes(StandardCharsets.UTF_8);
        HttpInputMessage cleanedInput = new XssByteArrayInputMessage(cleanedBytes, inputMessage.getHeaders());
        return super.readInternal(clazz, cleanedInput);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 基于 ByteArrayInputStream 的 HttpInputMessage 实现
     *
     * <p>用于包装清洗后的 JSON 字节数组，供 YdszJson 反序列化使用。
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
