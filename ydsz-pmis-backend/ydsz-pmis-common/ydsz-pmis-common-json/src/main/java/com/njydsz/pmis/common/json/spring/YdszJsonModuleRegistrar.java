package com.njydsz.pmis.common.json.spring;

import com.njydsz.pmis.common.json.module.YdszJsonModule;
import com.njydsz.pmis.common.json.module.YdszJsonModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * YdszJson 模块注册器。
 *
 * <p>自动发现所有实现 {@link YdszJsonModule.SpringFactory} 接口的 Bean 并注册到
 * {@link YdszJsonModuleRegistry}。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public class YdszJsonModuleRegistrar {

    private static final Logger log = LoggerFactory.getLogger(YdszJsonModuleRegistrar.class);

    private final List<YdszJsonModule> springModules;

    public YdszJsonModuleRegistrar(List<YdszJsonModule> springModules) {
        this.springModules = springModules;
    }

    /**
     * 执行模块注册。
     */
    public void register() {
        if (springModules != null && !springModules.isEmpty()) {
            log.info("发现 {} 个 YdszJson Spring Factory 模块", springModules.size());
            YdszJsonModuleRegistry registry = YdszJsonModuleRegistry.getInstance();
            registry.registerSpringFactories(springModules);
            registry.initialize();
            log.info("YdszJson 模块注册完成 | 模块数量={} | 序列化器={} | 反序列化器={}",
                    registry.getModuleCount(),
                    registry.getSerializerCount(),
                    registry.getDeserializerCount());
        } else {
            log.debug("未发现 YdszJson Spring Factory 模块");
        }
    }
}
