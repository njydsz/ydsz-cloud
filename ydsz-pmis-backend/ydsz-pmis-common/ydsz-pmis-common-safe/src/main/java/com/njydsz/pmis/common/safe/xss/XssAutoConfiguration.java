package com.njydsz.pmis.common.safe.xss;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.njydsz.pmis.common.util.json.JsonUtils;

/**
 * XSS JSON 自动配置
 *
 * <p>当 {@code ydsz.safe.xss.json-enabled=true} 时，自动注册 {@link XssStringDeserializer}
 * 到全局 Jackson ObjectMapper。这使得 JSON 反序列化时自动对 String 字段进行 XSS 清洗。
 *
 * <p>此方式与 XssFilter、XssJsonMessageConverter 互补，
 * 在 Jackson 反序列化层面提供 XSS 防护。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.safe.xss", name = "enabled", havingValue = "true", matchIfMissing = false)
public class XssAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(XssAutoConfiguration.class);

    @Value("${ydsz.safe.xss.json-enabled:true}")
    private boolean jsonEnabled;

    @PostConstruct
    public void registerXssDeserializer() {
        if (jsonEnabled) {
            ObjectMapper mapper = JsonUtils.getMapper();
            XssJacksonConfig.registerModule(mapper);
            log.debug("ydsz Safe: XssStringDeserializer registered for XSS JSON body protection.");
        }
    }
}
