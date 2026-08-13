package com.njydsz.common.exception.registry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.MessageSource;
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
 * <p>启动时扫描所有标注 {@link YdszExceptionCode} 注解的枚举类，
 * 将其注册到统一错误码表 {@link ErrorCodeTable}（单一注册中心）。
 *
 * <p><b>时序说明：</b>实现 {@link SmartInitializingSingleton}，在全部单例 Bean 实例化完成后
 * 执行扫描注册与 i18n key fail-fast 校验，保证校验时机晚于注册，避免"校验空转"。
 *
 * <p><b>性能优化（v2.0）：</b>优先读取编译时生成的索引文件 META-INF/spring/ydsz-exception-codes.idx，
 * 仅在索引不存在时回退到 ASM 字节码扫描，减少启动开销。
 *
 * <p><b>使用方式：</b>业务模块可在 src/main/resources/META-INF/spring/ydsz-exception-codes.idx
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
public class ExceptionCodeScanner implements SmartInitializingSingleton {

    private static final String INDEX_LOCATION = "META-INF/spring/ydsz-exception-codes.idx";
    private static final String SCAN_PATTERN = "classpath*:com/njydsz/**/*.class";
    private static final String INDEX_PATTERN = "classpath*:" + INDEX_LOCATION;

    private final ErrorCodeTable errorCodeTable;
    private final MessageSource messageSource;
    private final boolean validateOnStartup;
    private final AnnotationTypeFilter annotationFilter = new AnnotationTypeFilter(YdszExceptionCode.class);
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * {@link MetadataReaderFactory} 懒加载构造
     */
    private volatile MetadataReaderFactory metadataReaderFactory;

    /**
     * 构造扫描注册器。
     *
     * @param errorCodeTable  统一错误码表（可为 null，为 null 时跳过注册）
     * @param messageSource   国际化消息源（用于 i18n key fail-fast 校验）
     * @param validateOnStartup 是否启动时校验 i18n key 可解析
     */
    public ExceptionCodeScanner(ErrorCodeTable errorCodeTable,
                                MessageSource messageSource,
                                boolean validateOnStartup) {
        this.errorCodeTable = errorCodeTable;
        this.messageSource = messageSource;
        this.validateOnStartup = validateOnStartup;
    }

    /**
     * 所有单例 Bean 实例化完成后执行扫描注册与 i18n key 校验。
     *
     * <p>扫描先于校验，保证 fail-fast 校验基于完整注册表执行。
     */
    @Override
    public void afterSingletonsInstantiated() {
        scanAndRegister();
        if (validateOnStartup && messageSource != null) {
            validateExceptionCodeKeys();
        }
    }

    /**
     * 执行扫描注册。
     *
     * <p>优先使用编译时索引，回退到 ASM 字节码扫描。
     */
    public void scanAndRegister() {
        // 1. 优先尝试从索引文件加载
        boolean indexed = scanFromIndex();

        // 2. 索引不存在或加载失败时，回退到 ASM 扫描
        if (!indexed) {
            log.info("[ExceptionCodeScanner] 未检测到编译时索引（{}），回退到 ASM 字节码扫描", INDEX_LOCATION);
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

            log.info("[ExceptionCodeScanner] 从索引文件加载完成，注册 {} 个错误码枚举 | 索引来源: {}",
                    registeredCount, resources.length);
            return true;
        } catch (Exception e) {
            log.warn("[ExceptionCodeScanner] 索引文件加载失败: {}，将回退到 ASM 扫描", e.getMessage());
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
            log.warn("[ExceptionCodeScanner] 索引中列出的类不存在: {}", className);
        } catch (Exception e) {
            log.debug("[ExceptionCodeScanner] 加载类失败: {} | error={}", className, e.getMessage());
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
                    if (!reader.getAnnotationMetadata().hasAnnotation(YdszExceptionCode.class.getName())) {
                        continue;
                    }
                    var annotationAttrs = reader.getAnnotationMetadata()
                            .getAnnotationAttributes(YdszExceptionCode.class.getName());
                    if (annotationAttrs == null) {
                        continue;
                    }
                    String module = (String) annotationAttrs.get("module");
                    String description = (String) annotationAttrs.get("description");
                    registerEnum(module, description, reader.getClassMetadata().getClassName());
                    registeredCount++;
                } catch (Throwable e) {
                    log.debug("[ExceptionCodeScanner] 跳过无法加载的类: {} err={}",
                            resource.getFilename(), e.getMessage());
                }
            }
            log.info("[ExceptionCodeScanner] ASM 扫描完成，注册 {} 个模块的错误码", registeredCount);
        } catch (Exception e) {
            log.warn("[ExceptionCodeScanner] 扫描失败: {}", e.getMessage(), e);
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
        YdszExceptionCode annotation = clazz.getAnnotation(YdszExceptionCode.class);
        if (annotation == null) {
            log.debug("[ExceptionCodeScanner] 类 {} 未标注 @YdszExceptionCode，跳过", clazz.getName());
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
            Map<String, ExceptionCode> codeMap = new HashMap<>();
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
            log.debug("[ExceptionCodeScanner] 注册模块错误码: module={} codes={}", module, codeMap.size());
        } catch (Exception e) {
            log.debug("[ExceptionCodeScanner] 加载枚举失败: {} err={}", className, e.getMessage());
        }
    }

    /**
     * 启动时校验所有已注册 ExceptionCode 的 i18n key 是否可在默认 messages.properties 中解析。
     *
     * <p>基于扫描完成的 {@link ErrorCodeTable} 全量注册表执行 fail-fast 校验，
     * 任一 key 缺失即抛出 {@link IllegalStateException} 阻止应用启动。
     */
    private void validateExceptionCodeKeys() {
        if (errorCodeTable == null) {
            log.warn("[ExceptionCodeScanner] ErrorCodeTable 不可用，跳过 i18n key 启动校验");
            return;
        }
        Map<String, ExceptionCode> registered = errorCodeTable.allCodes();
        List<String> missingKeys = new ArrayList<>(4);
        for (ExceptionCode code : registered.values()) {
            collectMissingKey(code, missingKeys);
        }
        if (!missingKeys.isEmpty()) {
            String errorMsg = String.format(
                    "i18n 启动校验失败：以下 %d 个 ExceptionCode 的 key 在 messages.properties 中缺失，"
                    + "请检查 src/main/resources/i18n/messages.properties 及对应语言文件：\n  - %s\n"
                    + "如需关闭校验，可设置 ydsz.i18n.validate-on-startup=false（不推荐）。",
                    missingKeys.size(), String.join("\n  - ", missingKeys));
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        log.info("[ExceptionCodeScanner] i18n 启动校验通过：共 {} 个 ExceptionCode key 全部可在 messages.properties 中解析",
                registered.size());
    }

    /**
     * 收集单个异常码缺失的 i18n key。
     *
     * @param code        异常码枚举
     * @param missingKeys 缺失 key 收集列表
     */
    private void collectMissingKey(ExceptionCode code, List<String> missingKeys) {
        String key = code.getKey();
        if (key == null || key.isEmpty()) {
            return;
        }
        try {
            String message = messageSource.getMessage(key, null, null, Locale.ROOT);
            if (message == null || message.equals(key)) {
                missingKeys.add(key + " (code=" + code.getCode() + ")");
            }
        } catch (Exception e) {
            missingKeys.add(key + " (code=" + code.getCode() + ", error=" + e.getMessage() + ")");
        }
    }
}
