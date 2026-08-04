package com.njydsz.common.feign.fallback;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.util.id.TracerUtils;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.*;
/**
 * Feign Client 降级工厂抽象基类。
 *
 * <p>提供统一的降级策略实现，简化 FallbackFactory 的编写。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * @Component
 * public class UserServiceFallbackFactory extends DefaultFallbackFactory<UserServiceClient> {
 *
 *     @Override
 *     protected UserServiceClient createFallback(Throwable cause) {
 *         // 根据异常类型返回不同的降级实现
 *         if (cause instanceof FeignException.NotFound) {
 *             return new UserServiceClient() {
 *                 @Override
 *                 public User getUser(Long id) {
 *                     return User.NOT_FOUND;
 *                 }
 *             };
 *         }
 *         return new UserServiceClient() {
 *             @Override
 *             public User getUser(Long id) {
 *                 log.warn("UserService 调用失败, 使用降级数据, cause: {}", cause.getMessage());
 *                 return User.DEFAULT;
 *             }
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p><b>降级策略原则：</b>
 * <ul>
 *   <li>优先返回缓存数据</li>
 *   <li>其次返回默认数据或空集合</li>
 *   <li>记录详细日志便于排查</li>
 *   <li>避免返回 null 导致空指针</li>
 * </ul>
 *
 * @param <T> Feign Client 接口类型
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public abstract class DefaultFallbackFactory<T> implements FallbackFactory<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 根据异常创建降级实例。
     *
     * <p>优先使用 {@link #createFallback(Throwable)} 的返回值，
     * 若返回 null 则使用 {@link #createSafeFallback(Throwable)} 生成安全降级代理。
     *
     * @param cause 触发降级的异常
     * @return Feign Client 降级实例
     */
    @Override
    public final T create(Throwable cause) {
        if (cause == null) {
            T result = createFallback(null);
            return result != null ? result : createSafeFallback(null);
        }
        log.warn("Feign Client 触发降级, 类型: {}, 消息: {}, traceId: {}",
                cause.getClass().getSimpleName(),
                cause.getMessage(),
                TracerUtils.getTraceId());
        T result = createFallback(cause);
        return result != null ? result : createSafeFallback(cause);
    }

    /**
     * 创建降级实现。
     *
     * @param cause 触发降级的异常（可能为 null）
     * @return Feign Client 降级实现，禁止返回 null
     */
    protected abstract T createFallback(Throwable cause);

    /**
     * 创建安全降级实现，当 {@link #createFallback(Throwable)} 返回 null 时调用。
     * <p>
     * 通过动态代理为目标接口生成降级实例，方法调用返回包含错误码的 {@link BaseResponse}，
     * 确保业务方不会拿到 null。
     *
     * @param cause 触发降级的异常
     * @return 安全的降级实例
     */
        protected T createSafeFallback(Throwable cause) {
        String errorMsg = cause != null ? cause.getMessage() : "服务降级（未知异常）";
        log.warn("Feign Client 降级实现返回 null, 使用安全降级: {}", errorMsg);

        Class<?>[] interfaces = resolveFeignClientInterface();
        return (T) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                interfaces,
                (proxy, method, args) -> {
                    Class<?> returnType = method.getReturnType();
                    if (returnType == void.class) {
                        return null;
                    }
                    if (BaseResponse.class.isAssignableFrom(returnType)) {
                        return BaseResponse.error(
                                "B01004",
                                errorMsg
                        );
                    }
                    if (Collection.class.isAssignableFrom(returnType)) {
                        return createEmptyCollection(returnType);
                    }
                    if (Map.class.isAssignableFrom(returnType)) {
                        return new HashMap<>();
                    }
                    return getDefaultValue(returnType);
                }
        );
    }

    /**
     * 根据集合接口类型创建空集合实例。
     *
     * @param collectionType 集合类型
     * @return 空集合实例
     */
    private Object createEmptyCollection(Class<?> collectionType) {
        if (List.class.isAssignableFrom(collectionType)) {
            if (LinkedList.class.isAssignableFrom(collectionType)) {
                return new LinkedList<>();
            }
            return new ArrayList<>();
        }
        if (Set.class.isAssignableFrom(collectionType)) {
            if (NavigableSet.class.isAssignableFrom(collectionType)) {
                return new TreeSet<>();
            }
            if (LinkedHashSet.class.isAssignableFrom(collectionType)) {
                return new LinkedHashSet<>();
            }
            return new HashSet<>();
        }
        if (Collection.class.isAssignableFrom(collectionType)) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    /**
     * 获取指定类型的默认值。
     */
    private Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0d;
            if (type == char.class) return '\u0000';
        }
        return null;
    }

    /**
     * 判断是否为服务不可用异常。
     *
     * @param throwable 异常
     * @return true=服务不可用
     */
    protected boolean isServiceUnavailable(Throwable throwable) {
        if (throwable instanceof FeignException) {
            FeignException fe = (FeignException) throwable;
            int status = fe.status();
            return status == 503 || status == 504 || status == 502;
        }
        return false;
    }

    /**
     * 判断是否为服务未找到异常。
     *
     * @param throwable 异常
     * @return true=服务未找到
     */
    protected boolean isNotFound(Throwable throwable) {
        if (throwable instanceof FeignException) {
            FeignException fe = (FeignException) throwable;
            return fe.status() == 404;
        }
        return false;
    }

    /**
     * 判断是否为客户端异常（4xx）。
     *
     * @param throwable 异常
     * @return true=客户端异常
     */
    protected boolean isClientException(Throwable throwable) {
        if (throwable instanceof FeignException) {
            FeignException fe = (FeignException) throwable;
            int status = fe.status();
            return status >= 400 && status < 500;
        }
        return false;
    }

    /**
     * 判断是否为服务端异常（5xx）。
     *
     * @param throwable 异常
     * @return true=服务端异常
     */
    protected boolean isServerException(Throwable throwable) {
        if (throwable instanceof FeignException) {
            FeignException fe = (FeignException) throwable;
            int status = fe.status();
            return status >= 500 && status < 600;
        }
        return false;
    }

    /**
     * 解析目标 FeignClient 接口
     * <p>优先通过 {@code getClass().getInterfaces()[0]} 获取，
     * 若为空则通过泛型反射查找目标接口。
     */
    private Class<?>[] resolveFeignClientInterface() {
        Class<?>[] directInterfaces = getClass().getInterfaces();
        if (directInterfaces.length > 0) {
            return new Class<?>[]{directInterfaces[0]};
        }
        // 通过泛型反射查找目标 FeignClient 接口
        Type genericSuperclass = getClass().getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType pt) {
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> clazz) {
                return new Class<?>[]{clazz};
            }
        }
        throw new IllegalStateException("无法解析 FeignClient 接口类型，请确保 " +
                getClass().getName() + " 正确指定了泛型参数");
    }
}
