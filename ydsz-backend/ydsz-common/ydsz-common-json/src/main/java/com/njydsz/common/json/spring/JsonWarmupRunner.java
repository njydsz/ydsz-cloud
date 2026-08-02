package com.njydsz.common.json.spring;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;

import com.njydsz.common.json.YdszJson;

/**
 * YdszJson ASM 预热 Runner。
 *
 * <p>在 Spring Boot 应用启动后，根据 {@link JsonProperties#getWarmupClasses()}
 * 配置的类列表，异步执行 ASM 字节码预热，避免首次请求时的延迟尖峰。
 *
 * <p>预热失败不会阻断应用启动，仅记录警告日志。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Order(0)
public class JsonWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JsonWarmupRunner.class);

    private final JsonProperties properties;

    public JsonWarmupRunner(JsonProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        List<String> classNames = properties.getWarmupClasses();
        if (classNames == null || classNames.isEmpty()) {
            return;
        }

        List<Class<?>> classes = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            try {
                classes.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                log.warn("[YdszJson] 预热类未找到，跳过: {}", className);
            } catch (Throwable t) {
                log.warn("[YdszJson] 加载预热类失败，跳过: {} - {}", className, t.getMessage());
            }
        }

        if (classes.isEmpty()) {
            return;
        }

        log.info("[YdszJson] 开始 ASM 预热，共 {} 个类", classes.size());
        long start = System.currentTimeMillis();
        try {
            YdszJson.warmup(classes.toArray(new Class<?>[0]));
        } catch (Exception e) {
            log.warn("[YdszJson] ASM 预热过程中发生异常（不影响应用启动）: {}", e.getMessage());
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("[YdszJson] ASM 预热完成，耗时 {}ms", elapsed);
    }
}
