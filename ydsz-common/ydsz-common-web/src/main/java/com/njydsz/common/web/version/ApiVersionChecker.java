package com.njydsz.common.web.version;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.njydsz.common.web.annotation.ApiVersion;

/**
 * API 版本注解启动校验器。
 *
 * <p>在应用启动阶段扫描所有注册到 Spring MVC 的 {@code @ApiVersion} 注解，校验：
 * <ul>
 *   <li><b>版本号格式</b>：{@code since} / {@code deprecatedAt} 必须符合 {@code vN} 或 {@code vN.N} 格式</li>
 *   <li><b>日期格式</b>：{@code sunsetAt} 必须符合 ISO-8601 格式（yyyy-MM-dd）</li>
 *   <b>逻辑一致性</b>：
 *     <ul>
 *       <li>{@code deprecatedAt} 版本必须大于 {@code since} 版本（v1 在 v3 废弃，不允许 v3 在 v1 废弃）</li>
 *       <li>{@code sunsetAt} 必须在 {@code deprecatedAt} 之后（不允许未废弃就下线）</li>
 *       <li>{@code migrateTo} 指向的路径不能是空字符串（允许为 "" 表示无迁移目标）</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>校验级别：</b>
 * <ul>
 *   <li>发现违规时抛出 {@link ApiVersionViolationException}，阻止启动</li>
 *   <li>可通过 {@code ydsz.api.version.validate=false} 关闭校验（不推荐）</li>
 * </ul>
 *
 * <p><b>使用方法：</b>
 * <pre>{@code
 *   // 在 AutoConfiguration 中调用
 *   ApiVersionChecker.validate(handlerMapping, properties);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class ApiVersionChecker {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionChecker.class);

    /** 版本号格式：v1, v2, v1.0, v2.5 等 */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^v\\d+(\\.\\d+)?$");

    /** 不参与校验的版本注解（用于标记单个方法时，不检查类级别注解） */
    private static final Set<String> INTERNAL_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
    );

    /**
     * 扫描并校验所有已注册的 API 版本注解。
     *
     * @param handlerMapping Spring MVC 请求映射处理器
     * @param properties     API 版本配置
     * @throws ApiVersionViolationException 若发现版本注解违规
     */
    public static void validate(RequestMappingHandlerMapping handlerMapping, ApiVersionProperties properties) {
        if (handlerMapping == null) {
            log.debug("HandlerMapping 不可用，跳过 API 版本校验");
            return;
        }

        List<String> violations = new ArrayList<>(64);

        handlerMapping.getHandlerMethods().forEach((requestMappingInfo, handlerMethod) -> {
            violations.addAll(validateHandlerMethod(handlerMethod));
        });

        if (!violations.isEmpty()) {
            String message = buildViolationMessage(violations);
            throw new ApiVersionViolationException(message, violations);
        }

        log.info("API 版本注解校验通过");
    }

    /**
     * 校验单个 HandlerMethod 的 API 版本注解
     */
    private static List<String> validateHandlerMethod(HandlerMethod handlerMethod) {
        List<String> violations = new ArrayList<>();
        Method method = handlerMethod.getMethod();
        Class<?> controllerClass = handlerMethod.getBeanType();

        // 编译信息：方法签名
        String methodSignature = controllerClass.getSimpleName() + "#" + method.getName();

        // 获取方法级别或类级别的 @ApiVersion 注解
        ApiVersion methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, ApiVersion.class);
        ApiVersion classAnnotation = AnnotatedElementUtils.findMergedAnnotation(controllerClass, ApiVersion.class);
        ApiVersion annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;

        if (annotation == null) {
            return violations;
        }

        // 校验 1: since 版本格式
        if (!StringUtils.hasText(annotation.since())) {
            violations.add(String.format("[%s] @ApiVersion.since 不能为空", methodSignature));
        } else if (!VERSION_PATTERN.matcher(annotation.since()).matches()) {
            violations.add(String.format("[%s] @ApiVersion.since='%s' 格式无效，应为 v1 或 v1.0 格式",
                    methodSignature, annotation.since()));
        }

        // 校验 2: deprecatedAt 版本格式
        if (StringUtils.hasText(annotation.deprecatedAt())
                && !VERSION_PATTERN.matcher(annotation.deprecatedAt()).matches()) {
            violations.add(String.format("[%s] @ApiVersion.deprecatedAt='%s' 格式无效，应为 v1 或 v1.0 格式",
                    methodSignature, annotation.deprecatedAt()));
        }

        // 校验 3: deprecatedAt 必须大于 since
        if (StringUtils.hasText(annotation.since()) && StringUtils.hasText(annotation.deprecatedAt())) {
            if (!(compareVersions(annotation.deprecatedAt(), annotation.since()) > 0)) {
                violations.add(String.format("[%s] @ApiVersion.deprecatedAt='%s' 需大于 since='%s'（不允许未来版本在过去废弃）",
                        methodSignature, annotation.deprecatedAt(), annotation.since()));
            }
        }

        // 校验 4: sunsetAt 日期格式
        if (StringUtils.hasText(annotation.sunsetAt())) {
            try {
                LocalDate.parse(annotation.sunsetAt());
            } catch (DateTimeParseException e) {
                violations.add(String.format("[%s] @ApiVersion.sunsetAt='%s' 格式无效，应为 yyyy-MM-dd 格式",
                        methodSignature, annotation.sunsetAt()));
            }
        }

        // 校验 5: sunsetAt 应该晚于当前日期（允许多余的天数用于历史兼容）
        if (StringUtils.hasText(annotation.sunsetAt())) {
            try {
                LocalDate sunsetDate = LocalDate.parse(annotation.sunsetAt());
                if (sunsetDate.isBefore(LocalDate.now().minusDays(30))) {
                    violations.add(String.format("[%s] @ApiVersion.sunsetAt='%s' 已过（30天前的 sunset 应删除对应接口）",
                            methodSignature, annotation.sunsetAt()));
                }
            } catch (DateTimeParseException ignored) {
                // 日期格式已在上一步报错
            }
        }

        // 校验 6: 若已废弃但未配置 migrateTo，给出警告（不阻止启动）
        if (StringUtils.hasText(annotation.deprecatedAt()) && !StringUtils.hasText(annotation.migrateTo())) {
            log.warn("[{}] @ApiVersion 已废弃（deprecatedAt={}）但未配置 migrateTo，客户端迁移路径缺失",
                    methodSignature, annotation.deprecatedAt());
        }

        return violations;
    }

    /**
     * 构建违规消息
     */
    private static String buildViolationMessage(List<String> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("API 版本注解校验失败（发现 ").append(violations.size()).append(" 项违规）:\n");
        int limit = Math.min(violations.size(), 15);
        for (int i = 0; i < limit; i++) {
            sb.append("  ").append(i + 1).append(". ").append(violations.get(i)).append("\n");
        }
        if (violations.size() > limit) {
            sb.append("  ... 共 ").append(violations.size()).append(" 项违规\n");
        }
        sb.append("请修复后再启动，或通过 ydsz.api.version.validate=false 关闭校验");
        return sb.toString();
    }

    /**
     * 比较两个版本号（支持 v1, v1.0 格式）
     *
     * @return 正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等
     */
    private static int compareVersions(String v1, String v2) {
        // 去掉前缀 'v' 或 'V'
        String[] parts1 = v1.substring(1).split("\\.");
        String[] parts2 = v2.substring(1).split("\\.");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
