package com.remisoft.common.exception.registry;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.remisoft.common.exception.enums.ExceptionCode;
import com.remisoft.common.exception.enums.ExceptionCodeRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 错误码自动扫描注册器。
 *
 * <p>启动时扫描 classpath 中所有标注 {@link RemiResultCode} 注解的枚举类，
 * 将其注册到 {@link ResultCodeRegistry} 全局注册表。
 *
 * <p>扫描范围见 {@value #SCAN_PATTERN}，覆盖所有业务模块。
 *
 * <p><b>改进：</b>{@link MetadataReaderFactory} 采用懒加载自构造方式，
 * 避免直接依赖 Spring Context 启动早的 Bean 注入时序问题。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class ResultCodeScanner {

    private static final String SCAN_PATTERN = "classpath*:com/remisoft/**/*.class";

    private final ResultCodeRegistry registry;
    private final AnnotationTypeFilter annotationFilter = new AnnotationTypeFilter(RemiResultCode.class);
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * {@link MetadataReaderFactory} 懒加载构造：
     * <ul>
     *     <li>首次扫描时通过 {@link SimpleMetadataReaderFactory} 自构造，不依赖 Spring 容器</li>
     * </ul>
     */
    private volatile MetadataReaderFactory metadataReaderFactory;

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
            MetadataReaderFactory readerFactory = getOrCreateReaderFactory();
            var resources = resourceResolver.getResources(SCAN_PATTERN);
            int registeredCount = 0;

            for (var resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                try {
                    MetadataReader reader = readerFactory.getMetadataReader(resource);
                    if (!reader.getAnnotationMetadata().hasAnnotation(RemiResultCode.class.getName())) {
                        continue;
                    }
                    var annotationAttrs = reader.getAnnotationMetadata()
                            .getAnnotationAttributes(RemiResultCode.class.getName());
                    if (annotationAttrs == null) {
                        continue;
                    }
                    String module = (String) annotationAttrs.get("module");
                    String description = (String) annotationAttrs.get("description");
                    registerEnum(module, description, reader.getClassMetadata().getClassName());
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
     * 获取或创建 {@link MetadataReaderFactory}。
     *
     * <p>线程安全的双重检查锁：
     * 首次调用时自构造 {@link SimpleMetadataReaderFactory}。
     */
    private MetadataReaderFactory getOrCreateReaderFactory() {
        if (metadataReaderFactory == null) {
            synchronized (this) {
                if (metadataReaderFactory == null) {
                    metadataReaderFactory = new SimpleMetadataReaderFactory();
                }
            }
        }
        return metadataReaderFactory;
    }

    /**
     * 加载枚举类并注册所有错误码。
     *
     * <p>注册到两个注册中心：
     * <ul>
     *   <li>{@link ResultCodeRegistry} — 供文档端点按模块分组展示</li>
     *   <li>{@link ExceptionCodeRegistry} — 供 {@link ExceptionCode#fromCode(String)} 反查</li>
     * </ul>
     */
    private void registerEnum(String module, String description, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            // 必须为 public 枚举（non-public 反射 getEnumConstants 会失败）
            if (!clazz.isEnum() || !java.lang.reflect.Modifier.isPublic(clazz.getModifiers())
                    || !ExceptionCode.class.isAssignableFrom(clazz)) {
                return;
            }
            registry.registerModule(module, description);
            java.util.Map<String, ExceptionCode> codeMap = new java.util.HashMap<>();
            for (Object constant : clazz.getEnumConstants()) {
                ExceptionCode code = (ExceptionCode) constant;
                registry.registerCode(module, code.getCode(), code.getKey(), ((Enum<?>) constant).name());
                codeMap.put(code.getCode(), code);
            }
            // 同步注册到 ExceptionCodeRegistry，确保 lookup 反查可用
            ExceptionCodeRegistry.register(codeMap);
            log.debug("[ResultCodeScanner] 注册模块错误码: module={} codes={}", module, codeMap.size());
        } catch (Exception e) {
            log.debug("[ResultCodeScanner] 加载枚举失败: {} err={}", className, e.getMessage());
        }
    }
}
