package com.njydsz.common.json.internal;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.util.ClassUtils;

/**
 * JSON 双体系一致性检测器。
 *
 * <p>在应用启动阶段扫描指定的基础包，检测以下冲突场景：
 * <ul>
 *   <li><b>MIXED_ANNOTATIONS</b>：业务类同时使用 {@code @JsonClass} / {@code @JsonField}
 *       和 Jackson 注解（{@code com.fasterxml.jackson.*}）</li>
 *   <li><b>JACKSON_ON_CLASSPATH</b>：类路径中检测到 Jackson 核心类（{@code ObjectMapper}），
 *       可能造成 Spring Boot 自动配置冲突</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 *   DualJsonDetector.scanAndReport(basePackages, failOnConflict);
 * }</pre>
 *
 * <p><b>性能说明：</b>扫描仅在启动阶段执行一次，使用 Spring {@code MetadataReaderFactory}
 * 不加载类，开销可接受（通常 < 500ms）。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class DualJsonDetector {

    private static final Logger log = LoggerFactory.getLogger(DualJsonDetector.class);

    /** YdszJson 核心注解 */
    private static final String ANNOTATION_JSON_CLASS = "com.njydsz.common.json.annotation.JsonClass";
    private static final String ANNOTATION_JSON_FIELD = "com.njydsz.common.json.annotation.JsonField";

    /** Jackson 注解包名前缀 */
    private static final String JACKSON_ANNOTATION_PREFIX = "com.fasterxml.jackson";
    private static final String JACKSON_CORE_CLASS = "com.fasterxml.jackson.databind.ObjectMapper";

    /** 已知的、可忽略的外部 Jackson 使用场景（如 Spring Boot Actuator 内部组件） */
    private static final Set<String> WELL_KNOWN_EXTERNAL_JACKSON_USERS = Set.of(
            "org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration",
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
    );

    /** Spring 元数据读取工厂 */
    private final SimpleMetadataReaderFactory metadataReaderFactory;

    /**
     * 构造检测器
     *
     * @param classLoader 类加载器（可为 null，使用默认）
     */
    public DualJsonDetector(ClassLoader classLoader) {
        this.metadataReaderFactory = new SimpleMetadataReaderFactory(
                classLoader == null ? ClassUtils.getDefaultClassLoader() : classLoader);
    }

    /**
     * 执行检测。
     *
     * @param packages       需扫描的包名列表
     * @param failOnConflict 发现冲突时是否抛出异常
     * @return 冲突列表（可能为空）
     * @throws DualJsonConflictException 若 failOnConflict=true 且发现冲突
     */
    public List<DualJsonConflictException.DualJsonConflict> detect(List<String> packages,
                                                                  boolean failOnConflict) {
        List<DualJsonConflictException.DualJsonConflict> conflicts = new ArrayList<>();

        // 检测 1: Jackson 依赖是否在类路径
        boolean jacksonOnClasspath = isJacksonOnClasspath();
        if (jacksonOnClasspath) {
            conflicts.add(new DualJsonConflictException.DualJsonConflict(
                    "classpath",
                    DualJsonConflictException.ConflictType.JACKSON_ON_CLASSPATH,
                    "检测到 Jackson ObjectMapper 存在于类路径中，可能与 YdszJson 产生冲突"
            ));
        }

        // 检测 2: 扫描业务类上的注解混用
        for (String pkg : packages) {
            try {
                conflicts.addAll(scanPackageForMixedAnnotations(pkg));
            } catch (Exception e) {
                log.warn("双体系扫描异常（包 {} 可访问性不足），跳过: {}", pkg, e.getMessage());
            }
        }

        if (!conflicts.isEmpty()) {
            String summary = buildSummary(conflicts);
            if (failOnConflict) {
                throw new DualJsonConflictException(
                        "JSON 双体系冲突检测失败（strict-mode 已启用）: " + summary, conflicts);
            } else {
                log.warn("JSON 双体系潜在冲突（strict-mode 松弛模式）: {}", summary);
            }
        } else {
            log.info("JSON 双体系检测通过，未发现冲突");
        }

        return conflicts;
    }

    /**
     * 静态便捷方法：创建实例并执行检测
     *
     * @param packages       需扫描的包名列表
     * @param failOnConflict 发现冲突时是否抛出异常
     * @return 冲突列表
     */
    public static List<DualJsonConflictException.DualJsonConflict> scanAndReport(
            List<String> packages, boolean failOnConflict) {
        DualJsonDetector detector = new DualJsonDetector(null);
        return detector.detect(packages, failOnConflict);
    }

    /**
     * 检查 Jackson 核心类是否存在于类路径
     */
    private boolean isJacksonOnClasspath() {
        return ClassUtils.isPresent(JACKSON_CORE_CLASS, null);
    }

    /**
     * 扫描指定包下的业务类，检测 YdszJson 注解与 Jackson 注解混用
     */
    private List<DualJsonConflictException.DualJsonConflict> scanPackageForMixedAnnotations(String pkg)
            throws Exception {
        List<DualJsonConflictException.DualJsonConflict> conflicts = new ArrayList<>();
        // 将包名转为路径模式: com.example -> classpath*:com/example/**
        String pattern = "classpath*:" + pkg.trim().replace('.', '/') + "/**/*.class";

        org.springframework.core.io.Resource[] resources;
        try {
            resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources(pattern);
        } catch (Exception e) {
            log.debug("扫描包资源失败: {} -> {}", pkg, e.getMessage());
            return conflicts;
        }

        for (org.springframework.core.io.Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            try {
                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();

                boolean hasYdszJson = metadata.hasAnnotation(ANNOTATION_JSON_CLASS)
                        || hasAnnotationBySimpleName(metadata, "JsonClass");
                boolean hasJackson = hasJacksonAnnotations(metadata);

                if (hasYdszJson && hasJackson) {
                    conflicts.add(new DualJsonConflictException.DualJsonConflict(
                            metadata.getClassName(),
                            DualJsonConflictException.ConflictType.MIXED_ANNOTATIONS,
                            "类同时存在 @JsonClass 注解和 Jackson 注解"
                    ));
                }
            } catch (Exception e) {
                log.debug("读取类元数据失败: {} -> {}", resource, e.getMessage());
            }
        }
        return conflicts;
    }

    /**
     * 检查元数据是否包含 Jackson 注解
     */
    private boolean hasJacksonAnnotations(AnnotationMetadata metadata) {
        // 检查类级别的注解集合
        for (String annType : metadata.getAnnotationTypes()) {
            if (annType.startsWith(JACKSON_ANNOTATION_PREFIX)) {
                return true;
            }
        }
        // 检查方法和类级别引用的所有注解（通过 getAnnotationTypes 已覆盖类级别，
        // 这里额外检查方法上的 Jackson 注解）
        try {
            Set<MethodMetadata> allMethods = metadata.getAnnotatedMethods(
                    "com.fasterxml.jackson.annotation.JsonCreator");
            if (!allMethods.isEmpty()) {
                return true;
            }
        } catch (Exception ignored) {
            // Jackson 类不在类路径时忽略
        }
        // 检查其他常见的 Jackson 注解
        String[] commonJacksonAnnotations = {
                "com.fasterxml.jackson.annotation.JsonIgnore",
                "com.fasterxml.jackson.annotation.JsonProperty",
                "com.fasterxml.jackson.annotation.JsonFormat",
                "com.fasterxml.jackson.annotation.JsonSerialize",
                "com.fasterxml.jackson.annotation.JsonDeserialize",
                "com.fasterxml.jackson.databind.annotation.JsonSerialize",
                "com.fasterxml.jackson.databind.annotation.JsonDeserialize"
        };
        for (String jacksonAnnotation : commonJacksonAnnotations) {
            if (metadata.hasAnnotation(jacksonAnnotation)) {
                return true;
            }
            try {
                if (!metadata.getAnnotatedMethods(jacksonAnnotation).isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {
                // Jackson 类不在类路径时忽略
            }
        }
        return false;
    }

    /**
     * 通过简名匹配注解（避免因类未加载导致 hasAnnotation 失败）
     */
    private boolean hasAnnotationBySimpleName(AnnotationMetadata metadata, String simpleName) {
        for (String annType : metadata.getAnnotationTypes()) {
            if (annType.endsWith("." + simpleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成冲突摘要（用于日志和异常消息）
     */
    private String buildSummary(List<DualJsonConflictException.DualJsonConflict> conflicts) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n发现 ").append(conflicts.size()).append(" 个 JSON 双体系冲突：\n");
        int limit = Math.min(conflicts.size(), 10);
        for (int i = 0; i < limit; i++) {
            sb.append("  ").append(i + 1).append(". ")
              .append(conflicts.get(i).toString()).append("\n");
        }
        if (conflicts.size() > limit) {
            sb.append("  ... 共 ").append(conflicts.size()).append(" 项，详见完整日志\n");
        }
        sb.append("解决建议：移除 Jackson 注解，统一使用 @JsonClass/@JsonField；或在 application.yml 中关闭 ydsz.json.strict-mode");
        return sb.toString();
    }
}
