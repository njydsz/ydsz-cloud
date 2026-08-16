package com.njydsz.common.safe.xss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.common.json.module.JsonModule;
import com.njydsz.common.json.module.ModuleDeserializerRegistry;

/**
 * Safe 模块 YdszJson SPI 注册。
 *
 * <p>实现 {@link JsonModule.SpringFactory} 接口，通过 Spring Boot 自动装配机制
 * 将 {@link XssStringDeserializer} 注册到 YdszJson 引擎，替代手动 {@code YdszJson.register()} 调用。
 *
 * <p>由 {@link XssAutoConfiguration} 声明为 {@code @Bean}，当
 * {@code ydsz.safe.xss.enabled=true} 时自动生效。
 * {@code JsonAutoConfiguration} 中的 {@code JsonModuleRegistrar} 会自动发现并注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SafeJsonModule implements JsonModule, JsonModule.SpringFactory {

    private static final Logger log = LoggerFactory.getLogger(SafeJsonModule.class);

    @Override
    public String getModuleName() {
        return "safeJsonModule";
    }

    @Override
    public void setDeserializers(ModuleDeserializerRegistry registry) {
        registry.register(String.class, new XssStringDeserializer());
        log.debug("[SafeJsonModule] XssStringDeserializer registered via JsonModule SPI");
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
