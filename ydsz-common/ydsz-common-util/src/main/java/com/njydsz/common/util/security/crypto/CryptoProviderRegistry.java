package com.njydsz.common.util.security.crypto;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 加密算法注册表——统一的加密入口。
 *
 * <p>业务代码通过以下方式实现算法无关性：
 * <pre>{@code
 *   // 配置项决定使用 AES 还是 SM4，业务代码无需 if-else
 *   String algorithm = config.getCryptoAlgorithm(); // "AES-256-GCM" 或 "SM4-GCM"
 *   CryptoProvider provider = CryptoProviderRegistry.get(algorithm);
 *   byte[] ct = provider.encrypt(plaintext, key, null);
 * }</pre>
 *
 * <p>内置算法：
 * <ul>
 *   <li>AES-128-GCM / AES-192-GCM / AES-256-GCM（JDK 自带，始终可用）</li>
 *   <li>SM4-GCM / SM4-CBC（需 BouncyCastle，按需注册）</li>
 * </ul>
 *
 * <p>扩展方式：
 * <pre>{@code
 *   // 注册自定义算法（如后量子密码、HSM 提供者）
 *   CryptoProviderRegistry.register(new MyCustomCryptoProvider());
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.0.0
 * @see CryptoProvider
 * @see CryptoUtils
 */
public final class CryptoProviderRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(CryptoProviderRegistry.class);

    private static final Map<String, CryptoProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        // AES 系列（JDK 自带，始终可用）
        register(new AesGcmCryptoProvider(128));
        register(new AesGcmCryptoProvider(192));
        register(new AesGcmCryptoProvider(256));

        // SM4 系列（需 BouncyCastle，构造器内部会幂等注册 BC Provider）。
        // 不再前置判断 Security.getProvider(BC) —— BC 是懒注册的，前置判断会导致
        // SM4 永远注册不上（鸡生蛋问题）。改为无条件 try 注册：
        //  - bcprov-jdk18on 在 classpath：构造器内 ensure BC，注册成功；
        //  - bcprov-jdk18on 缺失：构造触发 NoClassDefFoundError，捕获后跳过，
        //    保证纯 AES 场景不依赖 BC（避免注册表静态初始化失败）。
        try {
            register(new Sm4GcmCryptoProvider());
            register(new Sm4CbcCryptoProvider());
            LOG.info("SM4 crypto providers registered (BC available)");
        } catch (NoClassDefFoundError e) {
            LOG.info("BouncyCastle not on classpath, SM4 providers skipped: {}", e.getMessage());
        } catch (Exception e) {
            LOG.warn("Failed to register SM4 providers: {}", e.getMessage());
        }
    }

    private CryptoProviderRegistry() {
        throw new UnsupportedOperationException("CryptoProviderRegistry is a utility class");
    }

    /**
     * 注册加密算法提供者。
     *
     * @param provider 加密算法实现
     * @throws NullPointerException provider 为 null 时
     */
    public static void register(CryptoProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        REGISTRY.put(provider.algorithm(), provider);
        LOG.debug("CryptoProvider registered: {}", provider.algorithm());
    }

    /**
     * 获取指定算法的加密提供者。
     *
     * @param algorithm 算法标识（如 "AES-256-GCM"、"SM4-GCM"）
     * @return 对应的 CryptoProvider 实例
     * @throws IllegalArgumentException 算法未注册时
     */
    public static CryptoProvider get(String algorithm) {
        CryptoProvider provider = REGISTRY.get(algorithm);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Unsupported crypto algorithm: '" + algorithm + "'. Available: " + REGISTRY.keySet());
        }
        return provider;
    }

    /**
     * 检查指定算法是否可用。
     *
     * @param algorithm 算法标识
     * @return 已注册返回 true
     */
    public static boolean isAvailable(String algorithm) {
        return REGISTRY.containsKey(algorithm);
    }

    /**
     * 获取所有已注册的算法标识。
     *
     * @return 不可变的算法标识集合
     */
    public static Set<String> availableAlgorithms() {
        return Set.copyOf(REGISTRY.keySet());
    }
}
