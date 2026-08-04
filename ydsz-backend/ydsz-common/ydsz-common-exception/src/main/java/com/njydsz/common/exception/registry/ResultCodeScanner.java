package com.njydsz.common.exception.registry;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;
import com.njydsz.common.util.spring.SpringContextHolder;

import lombok.extern.slf4j.Slf4j;

/**
 * 错误码自动扫描注册器。
 *
 * <p>启动时扫描 classpath 中所有标注 {@link YdszResultCode} 注解的枚举类，
 * 将其注册到 {@link ResultCodeRegistry} 全局注册表。
 *
 * <p>扫描范围见 {@value #SCAN_PATTERN}，覆盖所有业务模块。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ResultCodeScanner {

    private static final String SCAN_PATTERN = "classpath*:com/njydsz/**/*.class";

    private final ResultCodeRegistry registry;
    private final AnnotationTypeFilter annotationFilter = new AnnotationTypeFilter(YdszResultCode.class);
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * 构造扫描器。
     *
     * @param registry 全局错误码注册表
     */
    public ResultCodeScanner(ResultCodeRegistry registry) {
        this.registry = registry;
    }

    /**
     * 应用就绪后执行扫描注册。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scanAndRegister() {
        try {
            MetadataReaderFactory readerFactory = SpringContextHolder
                    .getBean(MetadataReaderFactory.class);
            var resources = resourceResolver.getResources(SCAN_PATTERN);
            int registeredCount = 0;

            for (var resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                try {
                    MetadataReader reader = readerFactory.getMetadataReader(resource);
                    String className = reader.getClassMetadata().getClassName();
                    if (!reader.getAnnotationMetadata().hasAnnotation(YdszResultCode.class.getName())) {
                        continue;
                    }
                    var annotationAttrs = reader.getAnnotationMetadata()
                            .getAnnotationAttributes(YdszResultCode.class.getName());
                    if (annotationAttrs == null) {
                        continue;
                    }
                    String module = (String) annotationAttrs.get("module");
                    String description = (String) annotationAttrs.get("description");
                    registerEnum(module, description, className);
                    registeredCount++;
                } catch (Throwable e) {
                    log.debug("[ResultCodeScanner] 跳过无法加载的类: {} err={}",
                            resource.getFilename(), e.getMessage());
                }
            }
            log.info("[ResultCodeScanner] 扫描完成，注册 {} 个模块的错误码", registeredCount);
        } catch (Exception e) {
            log.warn("[ResultCodeScanner] 扫描失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 加载枚举类并注册所有错误码。
     *
     * <p>同时注册到两个注册中心：
     * <ul>
     *   <li>{@link ResultCodeRegistry} — 供文档端点按模块分组展示</li>
     *   <li>{@link ExceptionCodeRegistry} — 供 {@link ExceptionCode#fromCode(String)} 反查</li>
     * </ul>
     * 这解决了静态块注册的类加载确定性问题（P1-5）。
     */
    private void registerEnum(String module, String description, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isEnum() || !ExceptionCode.class.isAssignableFrom(clazz)) {
                return;
            }
            registry.registerModule(module, description);
            Object[] constants = clazz.getEnumConstants();
            java.util.Map<String, ExceptionCode> codeMap = new java.util.HashMap<>();
            for (Object constant : constants) {
                ExceptionCode code = (ExceptionCode) constant;
                registry.registerCode(module, code.getCode(), code.getKey(), ((Enum<?>) constant).name());
                codeMap.put(code.getCode(), code);
            }
            // 同步注册到 ExceptionCodeRegistry，确保 fromCode() 反查可用
            ExceptionCodeRegistry.register(codeMap);
            log.debug("[ResultCodeScanner] 注册模块错误码: module={} codes={}", module, constants.length);
        } catch (Exception e) {
            log.debug("[ResultCodeScanner] 加载枚举失败: {} err={}", className, e.getMessage());
        }
    }
}
