package com.njydsz.pmis.common.json.spring;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.json.module.JsonModule;
import com.njydsz.pmis.common.json.module.JsonModuleRegistry;

/**
 * Json 模块注册器。
 *
 * <p>自动发现所有实现 {@link JsonModule.SpringFactory} 接口的 Bean 并注册到
 * {@link JsonModuleRegistry}。
 *
 * @since 1.0.0
 */
public class JsonModuleRegistrar {

    private static final Logger log = LoggerFactory.getLogger(JsonModuleRegistrar.class);

    private final List<JsonModule> springModules;

    public JsonModuleRegistrar(List<JsonModule> springModules) {
        this.springModules = springModules;
    }

    /**
     * 执行模块注册。
     */
    public void register() {
        if (springModules != null && !springModules.isEmpty()) {
            log.info("发现 {} 个 Json Spring Factory 模块", springModules.size());
            JsonModuleRegistry registry = JsonModuleRegistry.getInstance();
            registry.registerSpringFactories(springModules);
            registry.initialize();
            log.info("Json 模块注册完成 | 模块数量={} | 序列化器={} | 反序列化器={}",
                    registry.getModuleCount(),
                    registry.getSerializerCount(),
                    registry.getDeserializerCount());
        } else {
            log.debug("未发现 Json Spring Factory 模块");
        }
    }
}
