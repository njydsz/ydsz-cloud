package com.njydsz.pmis.common.safe.sensitive;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 敏感数据脱敏 AOP 拦截器
 *
 * <p>基于 Spring {@link ResponseBodyAdvice} 实现，在 Controller 方法返回值
 * 写入 HTTP 响应体之前，自动对返回值中的敏感字段进行脱敏处理。
 *
 * <p><b>工作原理：</b>
 * <ul>
 *   <li>拦截所有带有 {@link SensitiveData} 注解字段的方法返回值</li>
 *   <li>支持全局脱敏规则（通过字段名匹配）</li>
 *   <li>在序列化前调用 {@link SensitiveDataProcessor} 进行脱敏</li>
 *   <li>支持配置开关 {@code ydsz.safe.sensitive.enabled} 控制是否启用</li>
 *   <li>使用缓存机制避免重复检查同一个类</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @RestController
 * public class UserController {
 *     @GetMapping("/user/{id}")
 *     public UserVO getUser(@PathVariable Long id) {
 *         // 返回的 UserVO 中带有 @SensitiveData 注解的字段会自动脱敏
 *         return userService.findById(id);
 *     }
 * }
 *
 * public class UserVO {
 *     @SensitiveData(SensitiveType.PHONE)
 *     private String phone;
 *
 *     @SensitiveData(SensitiveType.NAME)
 *     private String name;
 * }
 * }</pre>
 *
 * <p><b>配置开关：</b>
 * <pre>{@code
 * ydsz:
 *   safe:
 *     sensitive:
 *       enabled: true  # 默认启用
 *       max-depth: 10  # 最大递归深度
 *       # 全局脱敏规则（可选）
 *       global-rules:
 *         - field-name: phone
 *           type: PHONE
 *         - field-name: idCard
 *           type: ID_CARD
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see SensitiveData
 * @see SensitiveDataProcessor
 * @see SensitiveDataConfiguration
 */
@RestControllerAdvice
public class SensitiveDataAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataAdvice.class);

    private final SensitiveDataConfiguration configuration;

    /**
     * 缓存类的敏感字段检查结果，避免重复反射检查
     * Key: Class对象，Value: 是否包含敏感字段
     */
    private final Map<Class<?>, Boolean> sensitiveClassCache = new ConcurrentHashMap<>();

    public SensitiveDataAdvice(SensitiveDataConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // 如果未启用脱敏，直接跳过
        if (!configuration.isEnabled()) {
            return false;
        }

        // 检查返回类型及其字段是否包含 @SensitiveData 注解或匹配全局规则
        Class<?> returnTypeClass = returnType.getParameterType();
        return containsSensitiveAnnotation(returnTypeClass, returnType);
    }

    @Override
    @Nullable
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (body == null) {
            return body;
        }

        try {
            log.debug("开始对返回值进行敏感数据脱敏: {}", returnType.getParameterType().getName());
            return SensitiveDataProcessor.process(body, configuration.getMaxDepth());
        } catch (Exception e) {
            // 脱敏失败返回空对象，防止原始未脱敏数据泄露
            log.error("敏感数据脱敏处理失败，返回空对象以避免数据泄露: {}", e.getMessage(), e);
            return createEmptyObject(body.getClass());
        }
    }

    /**
     * 创建指定类型的空对象，用于脱敏失败时返回
     * 避免原始数据泄露
     */
    private Object createEmptyObject(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            log.error("创建空对象失败: {}", clazz.getName(), ex);
            return null;
        }
    }

    /**
     * 递归检查类及其字段是否包含 {@link SensitiveData} 注解或匹配全局规则
     *
     * @param clazz 待检查的类
     * @param methodParameter 方法参数（用于获取泛型信息）
     * @return 是否包含敏感数据注解
     */
    private boolean containsSensitiveAnnotation(Class<?> clazz, MethodParameter methodParameter) {
        // 使用缓存避免重复检查
        return sensitiveClassCache.computeIfAbsent(clazz, c -> 
            doCheckSensitiveAnnotation(c, methodParameter, 0));
    }

    /**
     * 实际执行敏感注解检查
     */
    private boolean doCheckSensitiveAnnotation(Class<?> clazz, MethodParameter methodParameter, int depth) {
        if (clazz == null || clazz == Object.class || depth > configuration.getMaxDepth()) {
            return false;
        }

        // 检查当前类的所有字段
        for (Field field : clazz.getDeclaredFields()) {
            // 检查字段是否有 @SensitiveData 注解
            if (field.isAnnotationPresent(SensitiveData.class)) {
                return true;
            }

            // 检查字段是否匹配全局脱敏规则
            if (matchesGlobalRule(field.getName())) {
                return true;
            }

            // 检查嵌套对象的字段
            Class<?> fieldType = field.getType();
            
            // 处理集合类型，尝试获取泛型参数
            if (Collection.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                        Class<?> elementType = (Class<?>) typeArgs[0];
                        if (!isSimpleType(elementType) && 
                            doCheckSensitiveAnnotation(elementType, methodParameter, depth + 1)) {
                            return true;
                        }
                    }
                }
                // 无法获取泛型信息时，保守返回 true
                return true;
            }
            
            // 处理 Map 类型，尝试获取值的泛型参数
            if (Map.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (typeArgs.length > 1 && typeArgs[1] instanceof Class) {
                        Class<?> valueType = (Class<?>) typeArgs[1];
                        if (!isSimpleType(valueType) && 
                            doCheckSensitiveAnnotation(valueType, methodParameter, depth + 1)) {
                            return true;
                        }
                    }
                }
                // 无法获取泛型信息时，保守返回 true
                return true;
            }
            
            // 处理普通对象类型
            if (!isSimpleType(fieldType)) {
                if (doCheckSensitiveAnnotation(fieldType, methodParameter, depth + 1)) {
                    return true;
                }
            }
        }

        // 检查父类
        return doCheckSensitiveAnnotation(clazz.getSuperclass(), methodParameter, depth + 1);
    }

    /**
     * 检查字段名是否匹配全局脱敏规则
     *
     * @param fieldName 字段名
     * @return 是否匹配全局规则
     */
    private boolean matchesGlobalRule(String fieldName) {
        if (configuration.getGlobalRules() == null || configuration.getGlobalRules().isEmpty()) {
            return false;
        }

        return configuration.getGlobalRules().stream()
            .filter(SensitiveDataConfiguration.GlobalDesensitizeRule::isEnabled)
            .anyMatch(rule -> matchesFieldName(fieldName, rule.getFieldName()));
    }

    /**
     * 匹配字段名（支持通配符）
     *
     * @param fieldName 实际字段名
     * @param pattern 匹配模式（支持 * 通配符）
     * @return 是否匹配
     */
    private boolean matchesFieldName(String fieldName, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // 精确匹配
        if (!pattern.contains("*")) {
            return fieldName.equals(pattern);
        }

        // 通配符匹配
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return fieldName.matches(regex);
    }

    /**
     * 判断是否为简单类型（无需检查注解）
     *
     * @param clazz 类型
     * @return 是否为简单类型
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class
                || Date.class.isAssignableFrom(clazz)
                || Temporal.class.isAssignableFrom(clazz);
    }
}
