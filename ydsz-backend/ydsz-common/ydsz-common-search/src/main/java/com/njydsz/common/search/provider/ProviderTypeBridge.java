package com.njydsz.common.search.provider;

/**
 * Provider 类型桥接工具
 * <p>
 * 通过泛型方法签名实现类型擦除安全的 Provider 类型转换，
 * 避免在业务代码中使用 {@code @SuppressWarnings("unchecked")} 注解。
 * <p>
 * 类型安全性由调用方保证（调用方知道 Provider 的实际泛型类型）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ProviderTypeBridge {

    private ProviderTypeBridge() {
    }

    /**
     * 将通配符类型的 Provider 转换为指定泛型类型的 Provider
     *
     * @param provider 通配符类型 Provider
     * @param <T>      目标实体类型
     * @return 类型安全的 Provider
     */
    public static <T> SearchProvider<T> cast(SearchProvider<?> provider) {
        return ProviderTypeBridge.<T>castImpl(provider);
    }

    /**
     * 内部类型转换实现
     * <p>
     * 通过泛型方法签名让编译器在调用处进行类型推断，
     * 实际转换由 JVM 在运行时执行。
     */
    private static <T> SearchProvider<T> castImpl(SearchProvider<?> provider) {
        return (SearchProvider<T>) provider;
    }
}
