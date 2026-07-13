package com.njydsz.pmis.common.util.function;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import com.njydsz.pmis.common.util.concurrent.ExecutorUtils;

/**
 * 函数式编程工具类
 *
 * <p>提供丰富的函数式编程操作方法，功能对标 Guava Function 和 Apache Commons Functors，
 * 并进行了增强和优化。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>零依赖</b>：基于 JDK 8+ Function 接口</li>
 *   <li><b>函数组合</b>：支持函数的链式组合</li>
 *   <li><b>谓词组合</b>：支持条件的与或非操作</li>
 *   <li><b>异常处理</b>：支持带异常的函数式接口</li>
 *   <li><b>记忆化</b>：支持函数结果缓存</li>
 *   <li><b>重试机制</b>：支持函数执行重试</li>
 *   <li><b>惰性求值</b>：支持延迟计算</li>
 * </ul>
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>函数组合：compose、andThen、chain</li>
 *   <li>谓词组合：and、or、negate、andAll、orAll</li>
 *   <li>异常处理：wrap、unchecked</li>
 *   <li>记忆化：memoize、memoizeWithExpiration</li>
 *   <li>惰性求值：lazy</li>
 *   <li>重试机制：retry</li>
 *   <li>超时控制：withTimeout</li>
 *   <li>条件执行：ifThen、ifThenElse</li>
 *   <li>管道操作：pipe、cascade</li>
 *   <li>集合操作：mapFilter、transform、filter</li>
 *   <li>常用函数：identity、constant、nullToEmpty、emptyToNull</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 函数组合
 * Function<A, C> f = FunctionUtils.compose(g, f);
 *
 * // 谓词组合
 * Predicate<String> p = FunctionUtils.andAll(predicate1, predicate2, predicate3);
 *
 * // 记忆化函数
 * Function<Integer, Result> memoizedFn = FunctionUtils.memoize(this::computeExpensiveResult);
 *
 * // 惰性求值
 * Supplier<T> lazyValue = FunctionUtils.lazy(() -> computeValue());
 *
 * // 重试机制
 * Supplier<T> retrySupplier = FunctionUtils.retry(supplier, 3);
 *
 * // 条件执行
 * Function<Integer, Integer> transformIfPositive =
 *     FunctionUtils.ifThen(n -> n > 0, n -> n * 2);
 *
 * // 管道操作
 * String result = FunctionUtils.pipe("  hello world  ",
 *     String::trim,
 *     String::toUpperCase,
 *     s -> "Result: " + s);
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class FunctionUtils {

    /**
     * 私有构造函数，防止外部实例化
     */
    private FunctionUtils() {
        throw new UnsupportedOperationException("FunctionUtils 是工具类，不允许被实例化");
    }

    /**
     * 共享超时执行线程池，避免每次调用 withTimeout 时创建/销毁线程
     */
    private static final ExecutorService TIMEOUT_EXECUTOR = ExecutorUtils.newCachedThreadPool("function-timeout-");

    /**
     * 组合两个函数（先执行 f，再执行 g）
     */
    public static <A, B, C> Function<A, C> compose(Function<B, C> g, Function<A, B> f) {
        return a -> g.apply(f.apply(a));
    }

    /**
     * 组合多个函数
     */
    @SafeVarargs
    public static <T> Function<T, T> chain(Function<T, T>... functions) {
        return t -> {
            T result = t;
            for (Function<T, T> f : functions) {
                result = f.apply(result);
            }
            return result;
        };
    }

    /**
     * 组合多个函数（列表形式）
     */
    public static <T> Function<T, T> chain(List<Function<T, T>> functions) {
        return t -> {
            T result = t;
            for (Function<T, T> f : functions) {
                result = f.apply(result);
            }
            return result;
        };
    }

    /**
     * 组合两个谓词（与操作）
     */
    public static <T> Predicate<T> and(Predicate<T> p1, Predicate<T> p2) {
        return p1.and(p2);
    }

    /**
     * 组合多个谓词（与操作）
     */
    @SafeVarargs
    public static <T> Predicate<T> andAll(Predicate<T>... predicates) {
        Predicate<T> result = t -> true;
        for (Predicate<T> p : predicates) {
            result = result.and(p);
        }
        return result;
    }

    /**
     * 组合两个谓词（或操作）
     */
    public static <T> Predicate<T> or(Predicate<T> p1, Predicate<T> p2) {
        return p1.or(p2);
    }

    /**
     * 组合多个谓词（或操作）
     */
    @SafeVarargs
    public static <T> Predicate<T> orAll(Predicate<T>... predicates) {
        Predicate<T> result = t -> false;
        for (Predicate<T> p : predicates) {
            result = result.or(p);
        }
        return result;
    }

    /**
     * 否定谓词
     */
    public static <T> Predicate<T> negate(Predicate<T> predicate) {
        return predicate.negate();
    }

    /**
     * 恒等函数
     */
    public static <T> Function<T, T> identity() {
        return Function.identity();
    }

    /**
     * 常量函数（始终返回相同值）
     */
    public static <T, R> Function<T, R> constant(R value) {
        return t -> value;
    }

    /**
     * null 转空字符串
     */
    public static Function<String, String> nullToEmpty() {
        return s -> s != null ? s : "";
    }

    /**
     * 空字符串转 null
     */
    public static Function<String, String> emptyToNull() {
        return s -> (s != null && !s.isEmpty()) ? s : null;
    }

    /**
     * 包装带检查异常的函数
     */
    public static <T, R, E extends Exception> Function<T, R> wrap(
            CheckedFunction<T, R, E> checkedFunction) {
        return t -> {
            try {
                return checkedFunction.apply(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * 包装带检查异常的 Supplier
     */
    public static <T, E extends Exception> Supplier<T> wrapChecked(
            CheckedSupplier<T, E> checkedSupplier) {
        return () -> {
            try {
                return checkedSupplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * 包装带检查异常的 Consumer
     */
    public static <T, E extends Exception> Consumer<T> wrapConsumer(
            CheckedConsumer<T, E> checkedConsumer) {
        return t -> {
            try {
                checkedConsumer.accept(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * 包装带检查异常的 Runnable
     */
    public static <E extends Exception> Runnable wrapRunnable(
            CheckedRunnable<E> checkedRunnable) {
        return () -> {
            try {
                checkedRunnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * 记忆化函数（缓存结果）
     */
    public static <T, R> Function<T, R> memoize(Function<T, R> function) {
        Map<T, R> cache = new ConcurrentHashMap<>();
        return t -> cache.computeIfAbsent(t, function);
    }

    /**
     * 记忆化函数（带过期时间）
     */
    public static <T, R> Function<T, R> memoizeWithExpiration(
            Function<T, R> function, long duration, TimeUnit unit) {
        Map<T, CacheEntry<R>> cache = new ConcurrentHashMap<>();
        long ttlMillis = unit.toMillis(duration);

        return t -> {
            CacheEntry<R> entry = cache.get(t);
            long now = System.currentTimeMillis();

            if (entry != null && !entry.isExpired(now)) {
                return entry.value;
            }

            R result = function.apply(t);
            cache.put(t, new CacheEntry<>(result, now + ttlMillis));
            return result;
        };
    }

    /**
     * 惰性求值
     */
    public static <T> Supplier<T> lazy(Supplier<T> supplier) {
        return new LazySupplier<>(supplier);
    }

    /**
     * 重试包装器
     */
    public static <T> Supplier<T> retry(Supplier<T> supplier, int maxRetries) {
        return () -> {
            Exception lastException = null;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    return supplier.get();
                } catch (Exception e) {
                    lastException = e;
                }
            }
            throw new RuntimeException("Failed after " + maxRetries + " retries", lastException);
        };
    }

    /**
     * 超时包装器
     */
    public static <T> Supplier<T> withTimeout(Supplier<T> supplier, long timeout, TimeUnit unit) {
        return () -> {
            try {
                return ExecutorUtils.submitWithTimeout(TIMEOUT_EXECUTOR, supplier::get, timeout, unit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * 条件执行
     */
    public static <T> Function<T, T> ifThen(Predicate<T> condition, Function<T, T> transformer) {
        return t -> condition.test(t) ? transformer.apply(t) : t;
    }

    /**
     * 条件执行（带 else）
     */
    public static <T> Function<T, T> ifThenElse(
            Predicate<T> condition, 
            Function<T, T> ifTrue, 
            Function<T, T> ifFalse) {
        return t -> condition.test(t) ? ifTrue.apply(t) : ifFalse.apply(t);
    }

    /**
     * 级联调用（处理 null）
     */
    public static <T, R> Function<T, R> cascade(Function<T, R> function, R defaultValue) {
        return t -> {
            if (t == null) {
                return defaultValue;
            }
            try {
                return function.apply(t);
            } catch (Exception e) {
                return defaultValue;
            }
        };
    }

    /**
     * 管道操作
     */
    @SafeVarargs
    public static <T> T pipe(T initial, Function<T, T>... operations) {
        T result = initial;
        for (Function<T, T> op : operations) {
            result = op.apply(result);
        }
        return result;
    }

    /**
     * 集合转换和过滤
     */
    public static <T, R> List<R> mapFilter(
            Collection<T> collection,
            Function<T, R> mapper,
            Predicate<R> filter) {
        List<R> result = new ArrayList<>();
        for (T item : collection) {
            R mapped = mapper.apply(item);
            if (filter.test(mapped)) {
                result.add(mapped);
            }
        }
        return result;
    }

    /**
     * 集合转换
     */
    public static <T, R> List<R> transform(Collection<T> collection, Function<T, R> mapper) {
        List<R> result = new ArrayList<>(collection.size());
        for (T item : collection) {
            result.add(mapper.apply(item));
        }
        return result;
    }

    /**
     * 集合过滤
     */
    public static <T> List<T> filter(Collection<T> collection, Predicate<T> predicate) {
        List<T> result = new ArrayList<>();
        for (T item : collection) {
            if (predicate.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 检查缓存条目
     */
    private static class CacheEntry<T> {
        final T value;
        final long expireTime;

        CacheEntry(T value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }

        boolean isExpired(long now) {
            return now > expireTime;
        }
    }

    /**
     * 惰性 Supplier
     */
    private static class LazySupplier<T> implements Supplier<T> {
        private final Supplier<T> supplier;
        private T value;
        private volatile boolean computed = false;

        LazySupplier(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (!computed) {
                value = supplier.get();
                computed = true;
            }
            return value;
        }
    }

    /**
     * 带检查异常的 Function 接口
     */
    @FunctionalInterface
    public interface CheckedFunction<T, R, E extends Exception> {
        R apply(T t) throws E;
    }

    /**
     * 带检查异常的 Supplier 接口
     */
    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }

    /**
     * 带检查异常的 Consumer 接口
     */
    @FunctionalInterface
    public interface CheckedConsumer<T, E extends Exception> {
        void accept(T t) throws E;
    }

    /**
     * 带检查异常的 Runnable 接口
     */
    @FunctionalInterface
    public interface CheckedRunnable<E extends Exception> {
        void run() throws E;
    }
}
