package com.njydsz.pmis.common.feign.codec;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.json.YdszJson;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;

/**
 * 基于 Jackson 的 Feign JSON 编码器。
 *
 * <p>使用 {@link YdszJson} 作为 JSON 序列化实现，提供统一的 JSON 编码能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class YdszJsonEncoder implements Encoder {

    private static final Logger LOG = LoggerFactory.getLogger(YdszJsonEncoder.class);

    private static final String CONTENT_TYPE = "application/json;charset=UTF-8";

    /**
     * 将对象编码为 JSON 并写入请求体。
     *
     * @param object         待编码的对象，为 null 时跳过
     * @param bodyType       请求体目标类型
     * @param requestTemplate Feign 请求模板
     * @throws EncodeException 编码失败时抛出
     */
    @Override
    public void encode(Object object, Type bodyType, RequestTemplate requestTemplate) {
        if (object == null) {
            return;
        }

        try {
            String json = YdszJson.toJson(object);
            requestTemplate.body(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            requestTemplate.header("Content-Type", CONTENT_TYPE);
        } catch (Exception e) {
            LOG.warn("JSON 编码失败, 类型: {}, 错误: {}", bodyType, e.getMessage());
            throw new EncodeException("JSON 编码失败: " + e.getMessage(), e);
        }
    }
}
