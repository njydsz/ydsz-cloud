package com.njydsz.common.util.security;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * BouncyCastle Provider 防御性注册助手。
 *
 * <p>国密 SM2/SM3/SM4 算法依赖 BouncyCastle Provider。为避免部署时因
 * classpath 上未显式 {@code Security.addProvider} 而导致所有国密调用抛
 * {@code NoSuchProviderException}，本类在首次被引用时幂等地注册 Provider。
 *
 * <p>注册逻辑幂等：仅当 Provider 未注册时才添加，重复调用安全无害。
 *
 * @author ydsz-team
 * @since 2.1.0
 */
final class BcProvider {

    private BcProvider() {
        throw new UnsupportedOperationException("BcProvider is a utility class and cannot be instantiated");
    }

    /**
     * 确保 BouncyCastle Provider 已注册（幂等）。
     *
     * <p>各 SM 工具类的静态初始化块在获取 Cipher/Signature/MessageDigest 前调用本方法，
     * 使工具类在未显式注册 Provider 的部署环境中也能正常工作。</p>
     */
    static void ensure() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}





