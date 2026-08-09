package com.njydsz.common.exception.registry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.enums.ExceptionCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 错误码自动扫描注册器。
 *
 * <p>启动时扫描所有标注 {@link YdszResultCode} 注解的枚举类，
 * 将其注册到统一错误码表 {@link ErrorCodeTable}（单一注册中心）。
 *
 * <p><b>性能优化（v2.0）：</b>优先读取编译时生成的索引文件 META-INF/spring/ydsz-result-codes.idx，
 * 仅在索引不存在时回退到 ASM 字节码扫描，减少启动开销。
 *
 * <p><b>使用方式：</b>业务模块可在 src/main/resources/META-INF/spring/ydsz-result-codes.idx
 * 中列出所有错误码枚举类全限定名（每行一个），格式：
 * <pre>
 * com.example.module.ErrorCode
 * com.example.module.OtherErrorCode
 * </pre>
 *
 * <p>Gradle/Maven 插件可在编译时自动生成此文件，避免运行时 ASM 扫描开销。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ResultCodeScanner {

    private static final String INDEX_LOCATION = "META-INF/spring/ydsz-result-codes.idx";
    private static final String SCAN_PATTERN = "classpath*:com/njydsz/**/*.class";
    private static final String INDEX_PATTERN = "classpath*:" + INDEX_LOCATION;

    private final ErrorCodeTable errorCodeTable;
    private final AnnotationTypeFilter annotationFilter = new AnnotationTypeFilter(YdszResultCode.class);
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * {@link MetadataReaderFactory} 懒加载构造
     */
    private volatile MetadataReaderFactory metadataReaderFactory;

    /**
     * 构造扫描注册器。
     *
     * @param errorCodeTable 统一错误码表（可为 null，为 null 时跳过注册）
     */
    public ResultCodeScanner(ErrorCodeTable errorCodeTable) {
        this.errorCodeTable = errorCodeTable;
    }

    /**
     * 应用就绪后执行扫描注册。
     *
     * <p>优先使用编译时索引，回退到 ASM 字节码扫描。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scanAndRegister() {
        // 1. 优先尝试从索引文件加载
        boolean indexed = scanFromIndex();

        // 2. 索引不存在或加载失败时，回退到 ASM 扫描
        if (!indexed) {
            log.info("[ResultCodeScanner] 未检测到编译时索引（{}），回退到 ASM 字节码扫描", INDEX_LOCATION);
            scanWithAsm();
        }
    }

    /**
     * 从编译时生成的索引文件加载错误码枚举类
     *
     * @return true-成功从索引加载；false-索引不存在或加载失败
     */
    private boolean scanFromIndex() {
        try {
            Resource[] resources = resourceResolver.getResources(INDEX_PATTERN);
            if (resources.length == 0) {
                return false;
            }

            int registeredCount = 0;
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                try (InputStream is = resource.getInputStream();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(is, StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        // 跳过空行和注释
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        registerByIndex(line);
                        registeredCount++;
                    }
                }
            }

            log.info("[ResultCodeScanner] 从索引文件加载完成，注册 {} 个错误码枚举 | 索引来源: {}",
                    registeredCount, resources.length);
            return true;
        } catch (Exception e) {
            log.warn("[ResultCodeScanner] 索引文件加载失败: {}，将回退到 ASM 扫描", e.getMessage());
            return false;
        }
    }

    /**
     * 按索引行内容加载并注册枚举类
     */
    private void registerByIndex(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            registerClass(clazz);
        } catch (ClassNotFoundException e) {
            log.warn("[ResultCodeScanner] 索引中列出的类不存在: {}", className);
        } catch (Exception e) {
            log.debug("[ResultCodeScanner] 加载类失败: {} | error={}", className, e.getMessage());
        }
    }

    /**
     * ASM 字节码扫描（兜底策略）
     */
    private void scanWithAsm() {
        try {
            MetadataReaderFactory readerFactory = getOrCreateReaderFactory();
            Resource[] resources = resourceResolver.getResources(SCAN_PATTERN);
            int registeredCount = 0;

            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                try {
                    MetadataReader reader = readerFactory.getMetadataReader(resource);
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
                    registerEnum(module, description, reader.getClassMetadata().getClassName());
                    registeredCount++;
                } catch (Throwable e) {
                    log.debug("[ResultCodeScanner] 跳过无法加载的类: {} err={}",
                            resource.getFilename(), e.getMessage());
                }
            }
            log.info("[ResultCodeScanner] ASM 扫描完成，注册 {} 个模块的错误码", registeredCount);
        } catch (Exception e) {
            log.warn("[ResultCodeScanner] 扫描失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取或创建 {@link MetadataReaderFactory}
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
     * 注册枚举类到注册表
     */
    private void registerClass(Class<?> clazz) {
        if (!clazz.isEnum() || !java.lang.reflect.Modifier.isPublic(clazz.getModifiers())
                || !ExceptionCode.class.isAssignableFrom(clazz)) {
            return;
        }
        // 提取注解信息
        YdszResultCode annotation = clazz.getAnnotation(YdszResultCode.class);
        if (annotation == null) {
            log.debug("[ResultCodeScanner] 类 {} 未标注 @YdszResultCode，跳过", clazz.getName());
            return;
        }
        registerEnum(annotation.module(), annotation.description(), clazz.getName());
    }

    /**
     * 加载枚举类并注册所有错误码到统一错误码表 {@link ErrorCodeTable}。
     *
     * <p>注册内容：
     * <ul>
     *   <li>按模块维护 code 明细（moduleIndex），供运维端点与统计使用</li>
     *   <li>填充全局 code → ExceptionCode 反查索引（codeIndex），供运行时 resolve 使用</li>
     * </ul>
     */
    private void registerEnum(String module, String description, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isEnum() || !java.lang.reflect.Modifier.isPublic(clazz.getModifiers())
                    || !ExceptionCode.class.isAssignableFrom(clazz)) {
                return;
            }
            java.util.Map<String, ExceptionCode> codeMap = new java.util.HashMap<>();
            for (Object constant : clazz.getEnumConstants()) {
                ExceptionCode code = (ExceptionCode) constant;
                if (errorCodeTable != null) {
                    errorCodeTable.registerModule(module, description);
                    errorCodeTable.registerCode(module, code.getCode(), code.getKey(), ((Enum<?>) constant).name());
                }
                codeMap.put(code.getCode(), code);
            }
            // 填充统一错误码表（ErrorCodeTable）的全局 code→ExceptionCode 反查索引
            if (errorCodeTable != null) {
                errorCodeTable.registerAll(codeMap);
            }
            log.debug("[ResultCodeScanner] 注册模块错误码: module={} codes={}", module, codeMap.size());
        } catch (Exception e) {
            log.debug("[ResultCodeScanner] 加载枚举失败: {} err={}", className, e.getMessage());
        }
    }
}
