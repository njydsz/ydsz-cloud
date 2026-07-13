package com.njydsz.pmis.common.json.spring;

import com.njydsz.pmis.common.json.YdszJson;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * YdszJson HTTP 消息转换器。
 *
 * <p>通用 JSON 消息转换器，支持所有 Java 对象类型的 JSON 序列化/反序列化。
 * 自动注册到 Spring MVC 的 {@code HttpMessageConverter} 链中。
 *
 * <p>支持 {@code application/json} 和 {@code application/*+json} 媒体类型。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public class YdszJsonHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    /**
     * 构造函数，注册支持的媒体类型。
     */
    public YdszJsonHttpMessageConverter() {
        super(StandardCharsets.UTF_8,
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json"));
    }

    @Override
    protected boolean supports(@NonNull Class<?> clazz) {
        // 通用转换器，支持所有非 CharSequence 类型
        return !CharSequence.class.isAssignableFrom(clazz);
    }

    @Override
    @Nullable
    protected Object readInternal(@NonNull Class<?> clazz, @NonNull HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        try {
            byte[] body = inputMessage.getBody().readAllBytes();
            if (body.length == 0) {
                return null;
            }
            String json = new String(body, getDefaultCharset());
            return YdszJson.toObject(json, clazz);
        } catch (Exception e) {
            throw new HttpMessageNotReadableException("JSON 解析失败：" + e.getMessage(), e, inputMessage);
        }
    }

    @Override
    protected void writeInternal(@Nullable Object o, @NonNull HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        try {
            byte[] bytes = YdszJson.toJsonBytes(o);
            outputMessage.getBody().write(bytes);
            outputMessage.getBody().flush();
        } catch (Exception e) {
            throw new HttpMessageNotWritableException("JSON 序列化失败：" + e.getMessage(), e);
        }
    }
}
